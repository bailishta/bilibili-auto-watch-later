const $ = id => document.getElementById(id);
const $check = $('btn-check');
const $reimport = $('btn-reimport');
let checking = false;
let refreshingMeta = false;
let importing = false;

function feedback(message = '') {
  $('feedback').textContent = message;
  $('feedback').hidden = !message;
}

function renderActions(progress) {
  $check.disabled = checking || refreshingMeta || importing;
  $check.textContent = checking
    ? (progress?.total ? `检查中… ${progress.current}/${progress.total}` : '检查中…')
    : refreshingMeta ? '排序数据刷新中…' : '立即检查';
  $reimport.disabled = importing || checking || refreshingMeta;
  $reimport.textContent = importing ? '导入中…' : '重新导入关注';
}

async function refreshStatus() {
  const status = await chrome.runtime.sendMessage({ type: 'getStatus' });
  if (!status || status.error) throw new Error(status?.error || '无法读取扩展状态');
  $('status-badge').textContent = status.authenticated ? '已登录' : '未登录';
  $('status-badge').className = `badge ${status.authenticated ? 'logged-in' : 'not-logged-in'}`;
  $('stat-tracking').textContent = status.trackingCount ?? 0;
  $('stat-new').textContent = status.lastNewCount ?? 0;
}

async function syncState() {
  const [progress, meta, imported] = await Promise.all([
    chrome.runtime.sendMessage({ type: 'getCheckProgress' }),
    chrome.runtime.sendMessage({ type: 'getMetaProgress' }),
    chrome.runtime.sendMessage({ type: 'getImportProgress' })
  ]);
  checking = progress?.type === 'progress';
  refreshingMeta = meta?.type === 'progress';
  importing = imported?.type === 'import-progress';
  renderActions(progress);
  await refreshStatus();
}

$('btn-manage').addEventListener('click', async () => {
  try { await chrome.runtime.openOptionsPage(); }
  catch (e) { feedback(`打开管理中心失败：${e.message}`); }
});

$('btn-open-wl').addEventListener('click', () => {
  chrome.tabs.create({ url: 'https://www.bilibili.com/watchlater/' });
});

$check.addEventListener('click', async () => {
  checking = true;
  feedback();
  renderActions();
  try {
    const result = await chrome.runtime.sendMessage({ type: 'triggerCheck' });
    if (result?.error || result?.success === false) throw new Error(result.error || result.reason);
    if (result?.status === 'not_logged_in') feedback('请先登录 B站。');
    else if (result?.status === 'empty_list') feedback('名单为空，请导入关注或到管理中心添加 UP主。');
    else if (result?.status === 'error') feedback('检查失败，请到管理中心查看。');
  } catch (e) {
    feedback(`检查失败：${e.message}`);
  } finally {
    checking = false;
    renderActions();
    await syncState().catch(e => feedback(e.message));
  }
});

$reimport.addEventListener('click', async () => {
  importing = true;
  feedback();
  renderActions();
  try {
    const result = await chrome.runtime.sendMessage({ type: 'importFollowList' });
    if (result?.error) throw new Error(result.error);
    if (result?.status === 'not_logged_in') feedback('请先登录 B站。');
    else if (result?.status === 'ok') feedback(`导入完成，新增 ${result.imported ?? 0} 位 UP主。`);
    else feedback('导入未完成，请稍后重试。');
  } catch (e) {
    feedback(`导入失败：${e.message}`);
  } finally {
    importing = false;
    renderActions();
    await syncState().catch(e => feedback(e.message));
  }
});

// 只展示后台状态，关闭弹窗不会取消检查；详细进度与取消入口在管理页。
chrome.storage.onChanged.addListener((changes, area) => {
  if (area !== 'local') return;
  if (changes._checkProgress) checking = changes._checkProgress.newValue?.type === 'progress';
  if (changes._metaProgress) refreshingMeta = changes._metaProgress.newValue?.type === 'progress';
  if (changes._importProgress) importing = changes._importProgress.newValue?.type === 'import-progress';
  renderActions(changes._checkProgress?.newValue);
  if (changes.stats || changes.trackingList) refreshStatus().catch(e => feedback(e.message));
});

document.addEventListener('DOMContentLoaded', () => syncState().catch(e => feedback(e.message)));
