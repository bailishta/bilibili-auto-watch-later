import { calculateNextCheckTime, normalizeChecker } from '../core/schedules.js';

const DAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
const escape = text => String(text ?? '').replace(/[&<>"']/g, char => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[char]));

export function initCheckers(settings) {
  const list = document.getElementById('checkers-list');
  const dialog = document.getElementById('checker-dialog');
  const form = document.getElementById('checker-form');
  const error = document.getElementById('checker-error');
  const scope = form.elements.scope;
  const picker = document.getElementById('checker-picker');
  let checkers = settings.checkers || [];
  let editingId;
  const saveButton = document.getElementById('checker-save');
  function showError(message) {
    error.textContent = message;
    error.scrollIntoView({ block: 'nearest' });
    error.focus();
  }

  function render() {
    document.getElementById('checker-count').textContent = `${checkers.filter(c => c.enabled).length} 个已启用 / 共 ${checkers.length} 个`;
    list.innerHTML = checkers.length ? checkers.map(checker => {
      const next = checker.enabled ? calculateNextCheckTime(checker.schedule) : null;
      const schedule = [1,2,3,4,5,6,0].filter(day => checker.schedule[day]).map(day => `${DAYS[day]} ${checker.schedule[day]}`).join(' · ');
      return `<article class="checker-card" data-checker-id="${escape(checker.id)}">
        <div class="checker-card-heading"><h3>${escape(checker.name)}</h3><span class="badge ${checker.enabled ? 'logged-in' : ''}">${checker.enabled ? '已启用' : '已暂停'}</span></div>
        <p>${escape(schedule || '尚未选择检查日')}</p>
        <p>${checker.scope === 'all' ? '全部追踪 UP 主' : `指定 ${checker.mids.length} 位 UP 主`} · 检查 ${checker.newVideoWindowHours} 小时内的新视频</p>
        <p class="checker-next">${next ? `下次：${new Date(next).toLocaleString('zh-CN')}` : '暂无下次检查'}</p>
        <div class="checker-actions"><button class="btn btn-sm" data-action="edit">编辑</button><button class="btn btn-sm" data-action="toggle">${checker.enabled ? '暂停' : '启用'}</button><button class="btn btn-sm danger-button" data-action="delete">删除</button></div>
      </article>`;
    }).join('') : '<p class="empty-hint">暂无检查器，点击“添加”开始设置。</p>';
  }

  async function request(message) {
    async function send(payload) {
      let timer;
      try {
        return await Promise.race([chrome.runtime.sendMessage(payload), new Promise((_, reject) => {
          timer = setTimeout(() => reject(Error('后台暂未返回结果，请重新打开管理页确认是否已保存。')), 15000);
        })]);
      } finally { clearTimeout(timer); }
    }
    const result = await send(message);
    if (!result?.success) {
      const message = result?.error || result?.reason || '后台未返回保存结果，请重新加载扩展后重试';
      throw Error(message === '未知消息类型' ? '扩展后台版本未更新，请在扩展管理页重新加载后重试。' : message);
    }
    const settings = await send({ type: 'getSettings' });
    if (!Array.isArray(settings?.checkers)) throw Error(settings?.error || '读取保存结果失败，请重新打开管理页');
    checkers = settings.checkers;
    render();
  }

  async function openEditor(checker) {
    const creators = await chrome.runtime.sendMessage({ type: 'getTrackingList' });
    const current = checker || { id: crypto.randomUUID(), name: `检查器 ${checkers.length + 1}`, enabled: true,
      schedule: { 1: '18:00' }, newVideoWindowHours: 24, scope: 'all', mids: [] };
    editingId = current.id;
    form.reset(); error.textContent = '';
    document.getElementById('checker-dialog-title').textContent = checker ? '编辑检查器' : '新建检查器';
    form.elements.checkerName.value = current.name;
    form.elements.enabled.checked = current.enabled;
    form.elements.windowHours.value = current.newVideoWindowHours;
    scope.value = current.scope;
    document.getElementById('checker-days').innerHTML = [1,2,3,4,5,6,0].map(day => `<div class="schedule-row">
      <label><input type="checkbox" data-day="${day}" ${current.schedule[day] ? 'checked' : ''}> ${DAYS[day]}</label>
      <input type="time" step="60" aria-label="${DAYS[day]}检查时间" data-time="${day}" ${current.schedule[day] ? '' : 'disabled'} value="${escape(current.schedule[day] || '18:00')}">
    </div>`).join('');
    const mids = [...new Set([...Object.keys(creators), ...current.mids])];
    document.getElementById('checker-people').innerHTML = mids.length ? mids.map(mid => `<label class="person-option">
      <input type="checkbox" value="${escape(mid)}" ${current.mids.includes(mid) ? 'checked' : ''}>
      <span>${escape(creators[mid]?.name || '已移出追踪名单')} <small>UID ${escape(mid)}</small></span></label>`).join('') : '<p>追踪名单为空，请先添加 UP 主。</p>';
    picker.hidden = scope.value !== 'selected';
    updateSelectedCount();
    dialog.showModal();
    form.elements.checkerName.focus();
  }

  function updateSelectedCount() {
    document.getElementById('checker-selected-count').textContent = `已选 ${document.querySelectorAll('#checker-people input:checked').length} 位`;
  }
  scope.addEventListener('change', () => { picker.hidden = scope.value !== 'selected'; });
  document.getElementById('checker-days').addEventListener('change', event => {
    if (event.target.matches('[data-day]')) form.querySelector(`[data-time="${event.target.dataset.day}"]`).disabled = !event.target.checked;
  });
  document.getElementById('checker-people').addEventListener('change', updateSelectedCount);
  document.getElementById('checker-search').addEventListener('input', event => {
    const query = event.target.value.trim().toLowerCase();
    document.querySelectorAll('.person-option').forEach(label => { label.hidden = !label.textContent.toLowerCase().includes(query); });
  });
  document.getElementById('checker-cancel').addEventListener('click', () => dialog.close());
  document.getElementById('btn-add-checker').addEventListener('click', () => openEditor().catch(e => alert(e.message)));
  list.addEventListener('click', async event => {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const checker = checkers.find(item => item.id === button.closest('[data-checker-id]').dataset.checkerId);
    button.disabled = true;
    try {
      if (button.dataset.action === 'edit') await openEditor(checker);
      if (button.dataset.action === 'toggle') await request({ type: 'saveChecker', checker: { ...checker, enabled: !checker.enabled } });
      if (button.dataset.action === 'delete' && confirm(`删除“${checker.name}”？追踪名单会保留。`)) await request({ type: 'deleteChecker', id: checker.id });
    } catch (e) { alert(e.message); }
    finally { button.disabled = false; }
  });
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (saveButton.disabled) return;
    error.textContent = '';
    try {
      const schedule = {};
      form.querySelectorAll('[data-day]:checked').forEach(input => {
        const time = form.querySelector(`[data-time="${input.dataset.day}"]`);
        if (!time.value || !time.validity.valid) throw Error(`请填写${DAYS[Number(input.dataset.day)]}的有效检查时间（精确到分钟）。`);
        schedule[input.dataset.day] = time.value;
      });
      if (!Object.keys(schedule).length) throw Error('请至少选择一个检查日。');
      const mids = [...document.querySelectorAll('#checker-people input:checked')].map(input => input.value);
      const checker = normalizeChecker({ id: editingId, name: form.elements.checkerName.value,
        enabled: form.elements.enabled.checked, newVideoWindowHours: Number(form.elements.windowHours.value),
        schedule, scope: scope.value, mids });
      saveButton.disabled = true; saveButton.textContent = '保存中…'; form.setAttribute('aria-busy', 'true');
      await request({ type: 'saveChecker', checker });
      dialog.close();
    } catch (e) { showError(e.message || '保存失败，请重新加载扩展后重试'); }
    finally { saveButton.disabled = false; saveButton.textContent = '保存检查器'; form.removeAttribute('aria-busy'); }
  });
  chrome.storage.onChanged.addListener((changes, area) => {
    if (area === 'local' && changes.settings?.newValue?.checkers) {
      checkers = changes.settings.newValue.checkers; render();
    }
  });
  render();
}
