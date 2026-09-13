import * as api from './core/api.js';
import * as storage from './core/storage.js';
import { checkForNewVideos, importFollowList } from './core/checker.js';
import { refreshCreatorMeta } from './core/meta.js';
import { calculateNextCheckTime } from './core/schedules.js';

// ── 顶层：首次安装处理 ──

chrome.runtime.onInstalled.addListener(async (details) => {
  if (details.reason === 'install') {
    // 打开首次导入页面
    chrome.tabs.create({ url: 'onboarding/onboarding.html' });
  }

  // 确保 alarm 存在（按用户设置的星期和时间排程）
  await scheduleNextCheck();
  await storage.prunePublicationHistory();
});

chrome.runtime.onStartup.addListener(async () => {
  await scheduleNextCheck();
  await storage.prunePublicationHistory();
  await drainCheckQueue();
});

// ── 顶层：alarm 触发 ──

const CHECK_PREFIX = 'check-watch-later:';
const RETRY_ALARM = 'check-watch-later-queue';
let scheduleWrites = Promise.resolve();
let queueWrites = Promise.resolve();
let draining = false;

chrome.alarms.onAlarm.addListener(async alarm => {
  if (alarm.name === RETRY_ALARM) return drainCheckQueue();
  const id = alarm.name === 'check-watch-later' ? 'default'
    : alarm.name.startsWith(CHECK_PREFIX) ? alarm.name.slice(CHECK_PREFIX.length) : null;
  if (!id) return;
  const { checkers } = await storage.getSettings();
  const checker = checkers.find(item => item.id === id && item.enabled);
  if (!checker) return;
  // 在长任务前安排下次运行，同一时间的多个检查器进入持久化队列。
  await scheduleOne(checker);
  await updateQueue(queue => { if (!queue.includes(id)) queue.push(id); });
  await chrome.alarms.create(RETRY_ALARM, { when: Date.now() + 60000, periodInMinutes: 1 });
  return drainCheckQueue();
});

async function scheduleOne(checker) {
  const name = CHECK_PREFIX + checker.id;
  await chrome.alarms.clear(name);
  const when = checker.enabled && calculateNextCheckTime(checker.schedule);
  if (when) await chrome.alarms.create(name, { when });
}

function scheduleNextCheck() {
  const pending = scheduleWrites.then(async () => {
    const { checkers } = await storage.getSettings();
    const alarms = await chrome.alarms.getAll();
    for (const alarm of alarms) {
      if (alarm.name === 'check-watch-later' || alarm.name.startsWith(CHECK_PREFIX)) await chrome.alarms.clear(alarm.name);
    }
    for (const checker of checkers) await scheduleOne(checker);
  });
  scheduleWrites = pending.catch(() => {});
  return pending;
}

function updateQueue(update) {
  const pending = queueWrites.then(async () => {
    const data = await chrome.storage.local.get('_pendingCheckers');
    const queue = data._pendingCheckers || [];
    update(queue);
    await chrome.storage.local.set({ _pendingCheckers: queue });
    return queue;
  });
  queueWrites = pending.catch(() => {});
  return pending;
}

async function drainCheckQueue() {
  if (draining) return;
  draining = true;
  try {
    while (true) {
      const queue = await updateQueue(() => {});
      if (!queue.length) { await chrome.alarms.clear(RETRY_ALARM); return; }
      if (_checkRunning || _metaRefreshing || _configImporting) {
        await chrome.alarms.create(RETRY_ALARM, { when: Date.now() + 60000, periodInMinutes: 1 });
        return;
      }
      const { checkers } = await storage.getSettings();
      const checker = checkers.find(item => item.id === queue[0] && item.enabled);
      // 获取设置期间手动检查可能开始；重新检查互斥条件。
      if (_checkRunning || _metaRefreshing || _configImporting) continue;
      if (checker) {
        try { await runCheck(checker); }
        catch (error) { console.error('[检查器]', checker.name, error); }
      }
      await updateQueue(items => { const index = items.indexOf(queue[0]); if (index >= 0) items.splice(index, 1); });
    }
  } finally { draining = false; }
}

// 防止重复触发全量元数据刷新
let _metaRefreshing = false;
// 检查是否进行中（与元数据刷新互斥，避免并发请求B站）
let _checkRunning = false;
let _configImporting = false;
let _activeConfigWrites = 0;
const CONFIG_WRITES = new Set(['saveSettings', 'saveChecker', 'deleteChecker', 'addCreator', 'removeCreator', 'importTrackingList', 'importFollowList']);
// _metaProgress 的延迟清理定时器句柄（二次刷新前清除旧定时器，防止误删新进度）
let _metaProgressTimeout = null;
let _checkProgressTimeout = null;

async function runCheck(checker = null) {
  _checkRunning = true;
  if (_checkProgressTimeout) clearTimeout(_checkProgressTimeout);
  try {
    await chrome.storage.local.remove('_checkCancelled');
    await chrome.storage.local.set({ _checkProgress: { type: 'progress', current: 0, total: 1 } });
    const report = await checkForNewVideos(progress => {
      chrome.storage.local.set({ _checkProgress: { ...progress, checkerName: checker?.name } });
    }, checker);
    await chrome.storage.local.set({ _checkProgress: { type: 'complete', report } });
    return report;
  } catch (e) {
    await chrome.storage.local.set({ _checkProgress: { type: 'complete', report: { status: 'error' } } });
    throw e;
  } finally {
    _checkRunning = false;
    _checkProgressTimeout = setTimeout(() => chrome.storage.local.remove('_checkProgress'), 30000);
  }
}

// ── 顶层：消息处理 ──

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  handleMessage(message).then(sendResponse).catch(e => {
    console.error('[service-worker] handleMessage error:', e);
    sendResponse({ error: e.message || '内部错误' });
  });
  return true;
});

async function handleMessage(msg) {
  if (_configImporting) return { success: false, reason: '配置导入中，请稍后再操作' };
  if (msg.type === 'importConfiguration') {
    if (_checkRunning || _metaRefreshing || draining || _activeConfigWrites) return { success: false, reason: '请等待当前检查、刷新或保存完成后再导入配置' };
    _configImporting = true;
    try {
      const result = await storage.restoreConfiguration(msg.backup);
      await updateQueue(queue => { queue.length = 0; });
      await scheduleNextCheck();
      return { success: true, ...result };
    } finally { _configImporting = false; }
  }
  const writing = CONFIG_WRITES.has(msg.type);
  if (writing) _activeConfigWrites++;
  try { return await routeMessage(msg); }
  finally { if (writing) _activeConfigWrites--; }
}

async function routeMessage(msg) {
  switch (msg.type) {
    case 'exportConfiguration': return { success: true, backup: await storage.exportConfiguration() };
    case 'getStatus': {
      const cookies = await api.getCookies();
      const stats = await storage.getStats();
      const trackingList = await storage.getTrackingList();
      const trackedVideos = await storage.getTrackedVideos();

      return {
        authenticated: !!cookies.SESSDATA,
        trackingCount: Object.keys(trackingList).length,
        trackedCount: Object.keys(trackedVideos).length,
        totalAdded: stats.totalAdded || 0,
        lastNewCount: stats.lastNewCount || 0,
        lastCheck: stats.lastCheck || null
      };
    }

    case 'triggerCheck': {
      if (_metaRefreshing) {
        return { success: false, reason: '数据刷新进行中，请稍后再检查' };
      }
      if (_checkRunning) {
        return { success: false, reason: '检查正在进行中' };
      }
      return runCheck();
    }

    case 'saveChecker': {
      const checker = await storage.saveChecker(msg.checker);
      await scheduleNextCheck();
      return { success: true, checker };
    }
    case 'deleteChecker': {
      await storage.deleteChecker(msg.id);
      await scheduleNextCheck();
      return { success: true };
    }

    case 'cancelCheck': {
      // 取消检查：设置中断标志
      await chrome.storage.local.set({ _checkCancelled: true });
      return { success: true };
    }

    case 'getTrackingList': {
      await storage.prunePublicationHistory();
      return await storage.getTrackingList();
    }

    case 'saveSettings': {
      await storage.saveSettings(msg.settings);
      return { success: true };
    }

    case 'getSettings': {
      return await storage.getSettings();
    }

    case 'addCreator': {
      const { mid, name, face } = msg;
      if (!mid) return { success: false, reason: '缺少UID参数' };

      const logs = [];
      try {
        logs.push(`开始添加 mid=${mid}`);
        const cookies = await api.getCookies();
        logs.push(`Cookie状态: SESSDATA=${!!cookies.SESSDATA}, bili_jct=${!!cookies.bili_jct}`);

        const info = await api.getUpInfo(mid);
        logs.push(`getUpInfo结果: name=${info.name}, exists=${info.exists}`);

        const displayName = name || info.name || `UP主_${mid}`;
        await storage.addToTrackingList(mid, {
          name: displayName,
          face: face || info.face || '',
          fans: info.fans ?? null
        });
        logs.push(`已添加到追踪名单: ${displayName}`);
        return { success: true, name: displayName, logs };
      } catch (e) {
        logs.push(`错误: ${e.message}`);
        return { success: false, reason: `添加失败: ${e.message}`, logs };
      }
    }

    case 'removeCreator': {
      const { mid } = msg;
      if (!mid) return { success: false, reason: '缺少参数' };
      await storage.removeFromTrackingList(mid);
      return { success: true };
    }

    case 'importFollowList': {
      try {
        const user = await api.getCurrentUser();
        if (!user) return { status: 'not_logged_in' };

        const result = await importFollowList(user.uid, async (progress) => {
          chrome.storage.local.set({ _importProgress: progress });
        });
        await chrome.storage.local.remove('_importProgress');
        return result;
      } catch (e) {
        return { status: 'not_logged_in', error: e.message };
      }
    }

    case 'getCheckProgress': {
      const { _checkProgress } = await chrome.storage.local.get('_checkProgress');
      return _checkProgress || null;
    }

    case 'getImportProgress': {
      const { _importProgress } = await chrome.storage.local.get('_importProgress');
      return _importProgress || null;
    }

    case 'refreshCreatorMeta': {
      // 全量刷新追踪名单的粉丝数/更新时间元数据
      if (_metaRefreshing) {
        return { success: false, reason: '数据刷新已在进行中' };
      }
      if (_checkRunning) {
        return { success: false, reason: '检查正在进行中，请稍后再刷新' };
      }
      _metaRefreshing = true;
      // 清除上一次刷新的延迟清理定时器，避免误删新进度
      if (_metaProgressTimeout) clearTimeout(_metaProgressTimeout);
      try {
        const report = await refreshCreatorMeta((progress) => {
          chrome.storage.local.set({ _metaProgress: progress });
        });
        // 保持进度结果 30 秒供 popup 读取，不立即清除
        await chrome.storage.local.set({ _metaProgress: { type: 'complete', report } });
        _metaProgressTimeout = setTimeout(() => {
          chrome.storage.local.remove('_metaProgress');
          _metaProgressTimeout = null;
        }, 30000);
        return report;
      } finally {
        _metaRefreshing = false;
      }
    }

    case 'getMetaProgress': {
      const { _metaProgress } = await chrome.storage.local.get('_metaProgress');
      return _metaProgress || null;
    }

    case 'exportTrackingList': {
      const list = await storage.getTrackingList();
      return list;
    }

    case 'importTrackingList': {
      const { creators } = msg;
      if (!Array.isArray(creators) || creators.length === 0) {
        return { success: false, reason: '无效的名单数据' };
      }
      const result = await storage.importTrackingList(creators);
      return { success: true, ...result };
    }

    default:
      return { error: '未知消息类型' };
  }
}
