import * as api from './core/api.js';
import * as storage from './core/storage.js';
import { checkForNewVideos, importFollowList } from './core/checker.js';

// ── 顶层：首次安装处理 ──

chrome.runtime.onInstalled.addListener(async (details) => {
  if (details.reason === 'install') {
    // 打开首次导入页面
    chrome.tabs.create({ url: 'onboarding/onboarding.html' });
  }

  // 确保 alarm 存在（按用户设置的星期和时间排程）
  const settings = await storage.getSettings();
  await scheduleNextCheck(settings);
});

// ── 顶层：alarm 触发 ──

chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (alarm.name === 'check-watch-later') {
    console.log('[稍后观看助手] 定时检查开始');
    try {
      await checkForNewVideos();
    } catch (e) {
      console.error('[稍后观看助手] 检查出错:', e);
    }
  }
});

// ── 排程工具 ──

const WEEK_MS = 7 * 24 * 60 * 60 * 1000;

function calculateNextCheckTime(checkDays, checkTime) {
  if (!checkDays || checkDays.length === 0) return null;

  const [h, m] = (checkTime || '18:00').split(':').map(Number);
  const now = new Date();
  const today = now.getDay(); // 0=周日

  // 从今天开始往后找7天，找到第一个匹配的日期
  for (let offset = 0; offset < 7; offset++) {
    const d = new Date(now);
    d.setDate(d.getDate() + offset);
    d.setHours(h, m, 0, 0);
    if (d > now && checkDays.includes(d.getDay())) {
      return d.getTime();
    }
  }
  return null;
}

async function scheduleNextCheck(settings) {
  const nextTime = calculateNextCheckTime(settings.checkDays, settings.checkTime);
  await chrome.alarms.clear('check-watch-later');
  if (nextTime) {
    chrome.alarms.create('check-watch-later', {
      when: nextTime,
      periodInMinutes: Math.round(WEEK_MS / 60000)
    });
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
  switch (msg.type) {
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
      const report = await checkForNewVideos((progress) => {
        chrome.storage.local.set({ _checkProgress: progress });
      });
      // 保持进度结果 30 秒供 popup 读取，不立即清除
      await chrome.storage.local.set({ _checkProgress: { type: 'complete', report } });
      setTimeout(() => {
        chrome.storage.local.remove('_checkProgress');
      }, 30000);
      return report;
    }

    case 'cancelCheck': {
      // 取消检查：设置中断标志
      await chrome.storage.local.set({ _checkCancelled: true });
      return { success: true };
    }

    case 'getTrackingList': {
      return await storage.getTrackingList();
    }

    case 'saveSettings': {
      await storage.saveSettings(msg.settings);
      // 如果修改了排程设置，重新计算下次检查时间
      if (msg.settings.checkDays || msg.settings.checkTime) {
        const settings = await storage.getSettings();
        await scheduleNextCheck(settings);
      }
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
          face: face || info.face || ''
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

    default:
      return { error: '未知消息类型' };
  }
}
