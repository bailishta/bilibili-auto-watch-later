import { validateBackup, MAX_BACKUP_BYTES } from '../core/backup.js';

export function initConfigBackup() {
  const exportButton = document.getElementById('config-export');
  const importButton = document.getElementById('config-import');
  const input = document.getElementById('config-file');
  const status = document.getElementById('config-status');
  const dialog = document.getElementById('config-dialog');
  const apply = document.getElementById('config-apply');
  const error = document.getElementById('config-error');
  let pending = null;
  if (sessionStorage.getItem('configurationRestored')) {
    status.textContent = '配置已恢复'; sessionStorage.removeItem('configurationRestored');
  }
  exportButton.addEventListener('click', async () => {
    exportButton.disabled = true; status.textContent = '正在生成备份…';
    try {
      const response = await chrome.runtime.sendMessage({ type: 'exportConfiguration' });
      if (!response?.success) throw Error(response?.error || response?.reason || '导出失败');
      const json = JSON.stringify(response.backup, null, 2);
      await chrome.downloads.download({ url: 'data:application/json;charset=utf-8,' + encodeURIComponent(json),
        filename: `bilibili-configuration-${new Date().toISOString().slice(0,10)}.json`, saveAs: true });
      status.textContent = '备份下载已创建';
    } catch (e) { status.textContent = e.message; }
    finally { exportButton.disabled = false; }
  });
  importButton.addEventListener('click', () => input.click());
  input.addEventListener('change', async () => {
    const file = input.files[0]; if (!file) return;
    pending = null; status.textContent = ''; error.textContent = '';
    try {
      if (file.size > MAX_BACKUP_BYTES) throw Error('备份文件超过 25 MB，请检查文件');
      const backup = JSON.parse(await file.text());
      const config = validateBackup(backup);
      pending = backup;
      document.getElementById('config-filename').textContent = file.name;
      document.getElementById('config-preview').textContent = `${config.settings.checkers.length} 个检查器 · ${Object.keys(config.trackingList).length} 位 UP 主 · 保留 ${config.settings.historyWeeks} 周记录`;
      dialog.showModal();
    } catch (e) { status.textContent = `导入失败：${e.message}`; }
    finally { input.value = ''; }
  });
  document.getElementById('config-cancel').addEventListener('click', () => { pending = null; dialog.close(); });
  dialog.addEventListener('close', () => { pending = null; });
  dialog.addEventListener('cancel', event => { if (apply.disabled) event.preventDefault(); });
  apply.addEventListener('click', async () => {
    if (!pending) return;
    apply.disabled = true; apply.textContent = '导入中…'; error.textContent = '';
    const cancel = document.getElementById('config-cancel'); cancel.disabled = true;
    try {
      const result = await chrome.runtime.sendMessage({type:'importConfiguration', backup:pending});
      if (!result?.success) throw Error(result?.error || result?.reason || '恢复失败');
      sessionStorage.setItem('configurationRestored','1');
      location.reload();
    } catch (e) { error.textContent = e.message; }
    finally { apply.disabled = false; cancel.disabled = false; apply.textContent = '导入并替换配置'; }
  });
}
