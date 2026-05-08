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
const $selectInterval = document.getElementById('select-interval');

let _progressInterval = null;
let _stopProgressTimeout = null;

// ── 初始化 ──
document.addEventListener('DOMContentLoaded', async () => {
  // 加载设置
  const settings = await chrome.runtime.sendMessage({ type: 'getSettings' });
  if (settings?.newVideoWindowHours) {
    $selectWindow.value = String(settings.newVideoWindowHours);
  }
  if (settings?.checkIntervalMinutes) {
    $selectInterval.value = String(Math.round(settings.checkIntervalMinutes / 1440));
  }

  await refreshStatus();
  // 如果有正在进行的检查，恢复进度显示
  const p = await chrome.runtime.sendMessage({ type: 'getCheckProgress' });
  if (p && p.type === 'progress') {
    resumeProgress();
  }
});

// ── 设置变更 ──
$selectWindow.addEventListener('change', async () => {
  await chrome.runtime.sendMessage({
    type: 'saveSettings',
    settings: { newVideoWindowHours: parseInt($selectWindow.value) }
  });
});

$selectInterval.addEventListener('change', async () => {
  await chrome.runtime.sendMessage({
    type: 'saveSettings',
    settings: { checkIntervalMinutes: parseInt($selectInterval.value) * 1440 }
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
  chrome.runtime.sendMessage({ type: 'triggerCheck' });
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
  $statNew.textContent = status.lastNewCount || '--';

  // 上次检查
  if (status.lastCheck) {
    const d = new Date(status.lastCheck);
    $lastCheck.textContent = `上次检查：${d.toLocaleString('zh-CN')}`;
  } else {
    $lastCheck.textContent = '尚未检查';
  }

  // 追踪名单
  const trackingList = await chrome.runtime.sendMessage({ type: 'getTrackingList' });
  renderTrackingList(trackingList);
}

function renderTrackingList(list) {
  const mids = Object.keys(list || {});
  if (mids.length === 0) {
    $trackingList.innerHTML = '<div class="empty-hint">暂无追踪UP主</div>';
    return;
  }

  $trackingList.innerHTML = mids.map(mid => {
    const u = list[mid];
    return `
      <div class="list-item">
        <img class="avatar" src="${escapeHtml(u.face || '')}" onerror="this.style.display='none'" alt="">
        <div class="info">
          <div class="name" title="${escapeHtml(u.name)}">${escapeHtml(u.name)}</div>
          <div class="meta">UID: ${mid}</div>
        </div>
        <button class="delete-btn" data-mid="${mid}" title="移除">×</button>
      </div>`;
  }).join('');

  // 绑定删除事件
  $trackingList.querySelectorAll('.delete-btn').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      const mid = btn.dataset.mid;
      await chrome.runtime.sendMessage({ type: 'removeCreator', mid });
      refreshStatus();
    });
  });
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str || '';
  return div.innerHTML;
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
      $progressLabel.textContent = `检查中... ${p.current}/${p.total} ${p.name || ''}`;
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
const origCheckClick = $btnCheck.onclick;
$btnCheck.addEventListener('click', () => {
  addDebugLog('手动触发检查...', 'info');
});
