import { sortTrackingMids } from '../core/activity.js';
import { initCheckers } from './checkers.js';
import { createActivityPanel } from './activity-panel.js';
import { initConfigBackup } from './config-backup.js';

// ── DOM 元素 ──
const $statusBadge = document.getElementById('status-badge');
const $statTracking = document.getElementById('stat-tracking');
const $statNew = document.getElementById('stat-new');
const $btnCheck = document.getElementById('btn-check');
const $btnOpenWl = document.getElementById('btn-open-wl');
const $btnReimport = document.getElementById('btn-reimport');
const $btnAdd = document.getElementById('btn-add');
const $trackingList = document.getElementById('tracking-list');
const $lastCheck = document.getElementById('last-check');
const $checkProgress = document.getElementById('check-progress');
const $progressFill = document.getElementById('progress-fill');
const $progressLabel = document.getElementById('progress-label');
const $addModal = document.getElementById('add-modal');
const $addInput = document.getElementById('add-input');
const $addError = document.getElementById('add-error');
const $btnConfirmAdd = document.getElementById('btn-confirm-add');
const $btnCancel = document.getElementById('btn-cancel');
const $btnCancelCheck = document.getElementById('btn-cancel-check');
const $selectWindow = document.getElementById('select-window');
const activityPanel = createActivityPanel();
document.getElementById('activity-timezone').textContent = Intl.DateTimeFormat().resolvedOptions().timeZone;
const $searchTracking = document.getElementById('search-tracking');
const $btnExport = document.getElementById('btn-export');
const $btnImport = document.getElementById('btn-import');
const $importFileInput = document.getElementById('import-file-input');
const $selectSort = document.getElementById('select-sort');

let _trackingListCache = {};
let _sortMode = 'default';

let _progressInterval = null;
let _stopProgressTimeout = null;
let _metaPollInterval = null;
let _metaStopTimeout = null;

// 与弹窗共享后台状态：管理页始终显示最新名单与检查进度。
chrome.storage.onChanged.addListener((changes, area) => {
  if (area !== 'local') return;
  if (changes.trackingList) {
    _trackingListCache = changes.trackingList.newValue || {};
    $statTracking.textContent = Object.keys(_trackingListCache).length;
    renderTrackingList(_trackingListCache, $searchTracking.value, _sortMode);
    activityPanel.update(_trackingListCache);
  }
  if (changes._checkProgress?.newValue?.type === 'progress' && !_progressInterval) startProgressPolling();
  if (changes._metaProgress?.newValue?.type === 'progress' && !_metaPollInterval) startMetaPolling();
  if (changes.stats) refreshStatus();
});

// ── 初始化 ──
document.addEventListener('DOMContentLoaded', async () => {
  // 加载设置
  const settings = await chrome.runtime.sendMessage({ type: 'getSettings' });
  activityPanel.configure(settings);
  if (settings?.newVideoWindowHours) {
    $selectWindow.value = String(settings.newVideoWindowHours);
  }
  initCheckers(settings);
  initConfigBackup();

  // 排序方式（持久化在 settings 中）
  if (settings?.sortMode) $selectSort.value = settings.sortMode;
  _sortMode = $selectSort.value;

  await refreshStatus();
  // 如果有正在进行的检查，恢复进度显示
  const p = await chrome.runtime.sendMessage({ type: 'getCheckProgress' });
  if (p && p.type === 'progress') {
    resumeProgress();
  }
  // 如果有正在进行的数据刷新，恢复进度显示
  const mp = await chrome.runtime.sendMessage({ type: 'getMetaProgress' });
  if (mp && mp.type === 'progress') {
    startMetaPolling();
  }
});

// ── 设置变更 ──
$selectWindow.addEventListener('change', async () => {
  await chrome.runtime.sendMessage({
    type: 'saveSettings',
    settings: { newVideoWindowHours: parseInt($selectWindow.value) }
  });
});

// ── 按钮事件 ──
$btnCheck.addEventListener('click', async () => {
  $btnCheck.disabled = true;
  $btnCheck.textContent = '检查中...';
  $checkProgress.style.display = 'block';
  $progressFill.style.width = '0%';
  $progressLabel.textContent = '正在检查...';

  // 发送检查请求
  chrome.runtime.sendMessage({ type: 'triggerCheck' }).then(res => {
    // SW 因互斥拒绝（如数据刷新进行中）时停止轮询并提示
    if (res && res.success === false) {
      stopProgress();
      $checkProgress.style.display = 'none';
      $btnCheck.disabled = false;
      $btnCheck.textContent = '立即检查';
      alert(res.reason || '检查失败');
    }
  });
  startProgressPolling();
});

$btnOpenWl.addEventListener('click', () => {
  chrome.tabs.create({ url: 'https://www.bilibili.com/watchlater/' });
});

$btnReimport.addEventListener('click', async () => {
  $btnReimport.textContent = '导入中...';
  $btnReimport.disabled = true;
  await chrome.runtime.sendMessage({ type: 'importFollowList' });
  $btnReimport.textContent = '重新导入关注';
  $btnReimport.disabled = false;
  refreshStatus();
});

$btnAdd.addEventListener('click', () => {
  $addModal.style.display = 'flex';
  $addInput.value = '';
  $addError.style.display = 'none';
});

$btnCancel.addEventListener('click', () => {
  $addModal.style.display = 'none';
});

$btnConfirmAdd.addEventListener('click', async () => {
  const raw = $addInput.value.trim();
  if (!raw) {
    $addError.textContent = '请输入UID或B站主页链接';
    $addError.style.display = 'block';
    return;
  }

  let mid;
  // 从链接中提取UID
  const uidMatch = raw.match(/space\.bilibili\.com\/(\d+)/);
  if (uidMatch) {
    mid = uidMatch[1];
  } else if (/^\d+$/.test(raw)) {
    mid = raw;
  } else {
    $addError.textContent = '请输入有效的UID（纯数字）或B站主页链接';
    $addError.style.display = 'block';
    return;
  }

  $btnConfirmAdd.disabled = true;
  $btnConfirmAdd.textContent = '查询中...';

  const res = await chrome.runtime.sendMessage({
    type: 'addCreator',
    mid,
    name: '', // 由后端通过API获取
    face: ''
  });

  if (res.success) {
    $addModal.style.display = 'none';
    refreshStatus();
    // 在控制台打印日志
    if (res.logs) console.log('[添加UP主]', res.logs.join(' → '));
  } else {
    const errMsg = res.reason || '添加失败';
    const debugInfo = res.logs ? '\n调试: ' + res.logs.join(' → ') : '';
    $addError.textContent = errMsg + debugInfo;
    $addError.style.display = 'block';
    console.error('[添加UP主失败]', errMsg, res.logs);
  }

  $btnConfirmAdd.disabled = false;
  $btnConfirmAdd.textContent = '确认添加';
});

$btnCancelCheck.addEventListener('click', async () => {
  await chrome.runtime.sendMessage({ type: 'cancelCheck' });
  stopProgress();
  $checkProgress.style.display = 'none';
  $btnCheck.disabled = false;
  $btnCheck.textContent = '立即检查';
  refreshStatus();
});

// 搜索过滤
$searchTracking.addEventListener('input', () => {
  renderTrackingList(_trackingListCache, $searchTracking.value, _sortMode);
});

// 排序变更
$selectSort.addEventListener('change', () => {
  _sortMode = $selectSort.value;
  chrome.runtime.sendMessage({
    type: 'saveSettings',
    settings: { sortMode: _sortMode }
  });
  renderTrackingList(_trackingListCache, $searchTracking.value, _sortMode);
});

// ── 导出/导入 ──
$btnExport.addEventListener('click', async () => {
  try {
    const list = await chrome.runtime.sendMessage({ type: 'exportTrackingList' });
    const mids = Object.keys(list);
    if (mids.length === 0) {
      alert('追踪名单为空，无需导出');
      return;
    }
    // 构建导出数据结构
    const data = {
      version: 1,
      exportedAt: new Date().toISOString(),
      count: mids.length,
      creators: mids.map(mid => ({
        mid,
        name: list[mid].name,
        face: list[mid].face || ''
      }))
    };
    const json = JSON.stringify(data, null, 2);
    const dataUrl = 'data:application/json;charset=utf-8,' + encodeURIComponent(json);
    const filename = `bilibili-tracking-list-${new Date().toISOString().slice(0, 10)}.json`;
    await chrome.downloads.download({
      url: dataUrl,
      filename: filename,
      saveAs: true
    });
  } catch (e) {
    console.error('[导出名单]', e);
    alert('导出失败：' + (e.message || '未知错误'));
  }
});

$btnImport.addEventListener('click', () => {
  $importFileInput.click();
});

$importFileInput.addEventListener('change', async () => {
  const file = $importFileInput.files[0];
  if (!file) return;

  try {
    const text = await file.text();
    const data = JSON.parse(text);

    // 验证格式
    if (!data.creators || !Array.isArray(data.creators)) {
      throw new Error('文件格式不正确：缺少 creators 数组');
    }

    const creators = data.creators.filter(c => c.mid);
    if (creators.length === 0) {
      throw new Error('文件中没有有效的UP主数据');
    }

    const result = await chrome.runtime.sendMessage({
      type: 'importTrackingList',
      creators
    });

    if (result.success) {
      let msg = `导入完成！新增 ${result.added} 位UP主`;
      if (result.skipped > 0) {
        msg += `，跳过 ${result.skipped} 位（已存在）`;
      }
      alert(msg);
      refreshStatus();
    } else {
      alert('导入失败：' + (result.reason || '未知错误'));
    }
  } catch (e) {
    console.error('[导入名单]', e);
    alert('导入失败：' + (e.message || '文件解析错误'));
  }

  // 清空文件选择，允许重复选择同一文件
  $importFileInput.value = '';
});

// 点击弹窗外部关闭
$addModal.addEventListener('click', (e) => {
  if (e.target === $addModal) $addModal.style.display = 'none';
});

// ── 状态刷新 ──
async function refreshStatus() {
  const status = await chrome.runtime.sendMessage({ type: 'getStatus' });

  // 登录状态
  if (status.authenticated) {
    $statusBadge.textContent = '已登录';
    $statusBadge.className = 'badge logged-in';
  } else {
    $statusBadge.textContent = '未登录';
    $statusBadge.className = 'badge not-logged-in';
  }

  // 统计
  $statTracking.textContent = status.trackingCount || 0;
  $statNew.textContent = status.lastNewCount ?? 0;

  // 上次检查
  if (status.lastCheck) {
    const d = new Date(status.lastCheck);
    $lastCheck.textContent = `上次检查：${d.toLocaleString('zh-CN')}`;
  } else {
    $lastCheck.textContent = '尚未检查';
  }

  // 追踪名单
  const trackingList = await chrome.runtime.sendMessage({ type: 'getTrackingList' });
  _trackingListCache = trackingList || {};
  activityPanel.update(_trackingListCache);
  renderTrackingList(_trackingListCache, $searchTracking.value, _sortMode);
}

function renderTrackingList(list, filter, sortMode) {
  let mids = Object.keys(list || {});
  const q = (filter || '').trim().toLowerCase();
  if (q) {
    mids = mids.filter(mid => {
      const u = list[mid];
      return mid.includes(q) || (u.name || '').toLowerCase().includes(q);
    });
  }

  mids = sortTrackingMids(mids, list, sortMode);

  if (mids.length === 0) {
    $trackingList.innerHTML = `<div class="empty-hint">${q ? '无匹配结果' : '暂无追踪UP主'}</div>`;
    return;
  }

  $trackingList.innerHTML = mids.map(mid => {
    const u = list[mid];
    const metaParts = [`UID: ${mid}`];
    if (u.fans != null) metaParts.push(`粉丝 ${formatFans(u.fans)}`);
    metaParts.push(u.lastPubdate > 0 ? `更新于 ${new Date(u.lastPubdate * 1000).toLocaleString('zh-CN', { hour12: false })}` : '更新时间待采集');
    return `
      <div class="list-item">
        <img class="avatar" src="${escapeHtml(u.face || '')}" alt="">
        <div class="info">
          <div class="name" title="${escapeHtml(u.name)}">${escapeHtml(u.name)}</div>
          <div class="meta">${metaParts.join(' · ')}</div>
        </div>
        <button class="delete-btn" data-mid="${mid}" title="移除">×</button>
      </div>`;
  }).join('');

  // 绑定删除事件
  $trackingList.querySelectorAll('.delete-btn').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      // 刷新期间名单整表读改写，移除可能被覆盖，暂时禁止
      if (_metaPollInterval) {
        alert('数据刷新进行中，请稍后再移除');
        return;
      }
      const mid = btn.dataset.mid;
      await chrome.runtime.sendMessage({ type: 'removeCreator', mid });
      refreshStatus();
    });
  });
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str || '';
  return div.innerHTML.replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

// ── 排序与格式化 ──

function trimZero(s) {
  return s.endsWith('.0') ? s.slice(0, -2) : s;
}

function formatFans(n) {
  if (n >= 100000000) return trimZero((n / 100000000).toFixed(1)) + '亿';
  if (n >= 10000) return trimZero((n / 10000).toFixed(1)) + '万';
  return String(n);
}

// ── 进度轮询（共享，支持关闭popup后再打开恢复进度） ──

function stopProgress() {
  if (_progressInterval) clearInterval(_progressInterval);
  if (_stopProgressTimeout) clearTimeout(_stopProgressTimeout);
  _progressInterval = null;
  _stopProgressTimeout = null;
}

function startProgressPolling() {
  stopProgress(); // 清除旧状态
  stopMetaPolling(); // 检查与刷新互斥，防止两个轮询同时写进度条

  $checkProgress.style.display = 'block';
  $btnCheck.disabled = true;
  $btnCheck.textContent = '检查中...';
  $progressFill.style.width = '0%';
  $progressLabel.textContent = '正在检查...';

  const doPoll = async () => {
    const p = await chrome.runtime.sendMessage({ type: 'getCheckProgress' });
    if (!p) return;

    if (p.type === 'progress') {
      $progressFill.style.width = `${(p.current / p.total) * 100}%`;
      $progressLabel.textContent = `检查中${p.checkerName ? ` · ${p.checkerName}` : ''}... ${p.current}/${p.total} ${p.name || ''}`;
    } else if (p.type === 'complete') {
      stopProgress();
      $checkProgress.style.display = 'none';
      $btnCheck.disabled = false;
      $btnCheck.textContent = '立即检查';
      const report = p.report;
      if (report) {
        if (report.status === 'empty_list') {
          // 名单为空，不做特别提示
        } else if (report.status === 'not_logged_in') {
          $statusBadge.textContent = '未登录';
          $statusBadge.className = 'badge not-logged-in';
        } else if (report.status === 'cancelled') {
          // 已取消
        }
        if (report.added > 0) {
          $statNew.textContent = report.added;
        }
      }
      refreshStatus();
    }
  };

  _progressInterval = setInterval(doPoll, 500);
  _stopProgressTimeout = setTimeout(() => {
    stopProgress();
    $checkProgress.style.display = 'none';
    $btnCheck.disabled = false;
    $btnCheck.textContent = '立即检查';
    refreshStatus();
  }, 120000);
}

function resumeProgress() {
  startProgressPolling();
}

// ── 元数据刷新进度轮询（排序数据刷新，支持重开popup恢复） ──

function stopMetaPolling() {
  if (_metaPollInterval) clearInterval(_metaPollInterval);
  if (_metaStopTimeout) clearTimeout(_metaStopTimeout);
  _metaPollInterval = null;
  _metaStopTimeout = null;
}

function resetMetaUi() {
  $checkProgress.style.display = 'none';
  $btnCheck.disabled = false;
  $btnCheck.textContent = '立即检查';
  $btnAdd.disabled = false;
  $btnImport.disabled = false;
  $btnReimport.disabled = false;
}

function startMetaPolling() {
  stopMetaPolling(); // 清除旧状态
  stopProgress(); // 检查与刷新互斥，防止两个轮询同时写进度条

  $checkProgress.style.display = 'block';
  $btnCheck.disabled = true;
  // 刷新期间名单整表读改写，禁用增删入口避免写入竞态
  $btnAdd.disabled = true;
  $btnImport.disabled = true;
  $btnReimport.disabled = true;
  $progressFill.style.width = '0%';
  $progressLabel.textContent = '刷新数据中...';

  const doPoll = async () => {
    const p = await chrome.runtime.sendMessage({ type: 'getMetaProgress' });
    if (!p) return;

    if (p.type === 'progress') {
      $progressFill.style.width = `${(p.current / p.total) * 100}%`;
      $progressLabel.textContent = `刷新数据中... ${p.current}/${p.total} ${p.name || ''}`;
    } else if (p.type === 'complete') {
      stopMetaPolling();
      resetMetaUi();
      refreshStatus();
    }
  };

  _metaPollInterval = setInterval(doPoll, 500);
  _metaStopTimeout = setTimeout(() => {
    stopMetaPolling();
    resetMetaUi();
    refreshStatus();
  }, 120000);
}

// ── 调试日志面板 ──
const $debugSection = document.getElementById('debug-section');
const $debugContent = document.getElementById('debug-content');
const $debugToggle = document.getElementById('debug-toggle');

// 存储最近的调试日志
let _debugLogs = [];

function addDebugLog(msg, type) {
  _debugLogs.push({ msg, type, time: Date.now() });
  if (_debugLogs.length > 50) _debugLogs.shift();
  renderDebugLogs();
}

function renderDebugLogs() {
  if (_debugLogs.length === 0) return;
  $debugSection.style.display = 'block';
  $debugContent.innerHTML = _debugLogs.map(l =>
    `<div class="log-line ${l.type === 'error' ? 'log-error' : l.type === 'success' ? 'log-success' : ''}">${escapeHtml(l.msg)}</div>`
  ).join('');
}

$debugToggle.addEventListener('click', () => {
  const content = $debugContent;
  if (content.style.display === 'none') {
    content.style.display = 'block';
    $debugToggle.textContent = '📋 调试日志 ▼';
  } else {
    content.style.display = 'none';
    $debugToggle.textContent = '📋 调试日志 ▶';
  }
});

// 在检查按钮点击时记录日志
$btnCheck.addEventListener('click', () => {
  addDebugLog('手动触发检查...', 'info');
});
