const importBtn = document.getElementById('import-btn');
const skipBtn = document.getElementById('skip-btn');
const progress = document.getElementById('progress');
const progressText = document.getElementById('progress-text');
const result = document.getElementById('result');

importBtn.addEventListener('click', async () => {
  importBtn.disabled = true;
  importBtn.textContent = '正在导入...';
  progress.classList.add('show');
  progressText.textContent = '正在获取关注列表...';

  try {
    const res = await chrome.runtime.sendMessage({ type: 'importFollowList' });

    if (res.status === 'not_logged_in') {
      result.className = 'result warn show';
      result.textContent = '请先在 www.bilibili.com 登录您的B站账号，再回来导入。';
      importBtn.disabled = false;
      importBtn.textContent = '从B站关注列表导入';
      progress.classList.remove('show');
      return;
    }

    if (res.status === 'ok') {
      result.className = 'result success show';
      result.textContent = `导入完成！成功导入 ${res.imported} 位UP主` +
        (res.skipped > 0 ? `，跳过 ${res.skipped} 个特殊账号（如已注销）` : '');
      progress.classList.remove('show');
      importBtn.textContent = '导入完成';
      importBtn.disabled = true;
      // 1.5秒后自动关闭页面
      setTimeout(() => window.close(), 1500);
      return;
    }
  } catch (e) {
    result.className = 'result warn show';
    result.textContent = '导入出错：' + e.message;
  }

  progress.classList.remove('show');
  importBtn.textContent = '从B站关注列表导入';
  importBtn.disabled = false;
});

skipBtn.addEventListener('click', async () => {
  await chrome.runtime.sendMessage({ type: 'saveSettings', settings: { importDone: true } });
  window.close();
});
