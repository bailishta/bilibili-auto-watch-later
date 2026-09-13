import { renderActivityClock } from './activity-clock.js';
import { weekStart, summarizeWeek, normalizeHistoryWeeks, DEFAULT_HISTORY_WEEKS } from '../core/activity.js';

export function createActivityPanel() {
  let list = {}, offset = 0, selectedDay = null;
  let historyWeeks = DEFAULT_HISTORY_WEEKS;
  const weeksInput = document.getElementById('activity-weeks');
  const status = document.getElementById('activity-settings-status');
  const chart = document.getElementById('activity-chart');
  const back = document.getElementById('activity-back');
  const dateText = date => `${date.getMonth() + 1}月${date.getDate()}日`;
  const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  let chartAnimation;
  function render(animate = false) {
    const oldHeight = chart.getBoundingClientRect().height;
    const start = weekStart(); start.setDate(start.getDate() + offset * 7);
    const summary = summarizeWeek(list, start);
    const end = new Date(start); end.setDate(end.getDate() + 6);
    document.getElementById('activity-period').textContent = `${start.getFullYear()}年 ${dateText(start)} – ${dateText(end)}${offset === 0 ? ' · 本周' : ''}`;
    document.getElementById('activity-next').disabled = offset === 0;
    document.getElementById('activity-prev').disabled = offset === -(historyWeeks - 1);
    document.getElementById('activity-retention').textContent = historyWeeks;
    back.hidden = selectedDay === null;
    const buckets = selectedDay === null ? summary.days.map(day => day.mids) : summary.days[selectedDay].hours;
    const max = Math.max(1, ...buckets.map(bucket => bucket.size));
    const total = selectedDay === null ? summary.mids.size : summary.days[selectedDay].mids.size;
    document.getElementById('activity-summary').textContent = selectedDay === null
      ? `${total} 位 UP 主更新 · 点击星期查看时段`
      : `${days[selectedDay]} · ${total} 位 UP 主更新`;
    chart.className = selectedDay === null ? 'activity-chart week-chart' : 'activity-chart clock-chart';
    chart.innerHTML = '';
    if (selectedDay !== null) renderActivityClock(chart, buckets, list, summary.days[selectedDay].minutes);
    else buckets.forEach((bucket, index) => {
      const label = selectedDay === null ? days[index] : `${String(index).padStart(2, '0')}:00`;
      const element = document.createElement(selectedDay === null ? 'button' : 'div');
      element.className = 'activity-bar';
      if (selectedDay === null) {
        element.type = 'button'; element.setAttribute('aria-label', `${label}，${bucket.size} 位 UP 主更新，查看每小时`);
        element.addEventListener('click', () => { selectedDay = index; render(true); back.focus(); });
      }
      const names = [...bucket].map(mid => list[mid]?.name || mid);
      element.title = `${label} · ${bucket.size} 人${names.length ? '\n' + names.slice(0, 30).join('、') : ''}`;
      const count = document.createElement('span'); count.className = 'bar-count'; count.textContent = bucket.size;
      const track = document.createElement('span'); track.className = 'bar-track';
      const fill = document.createElement('span'); fill.className = 'bar-fill'; fill.style.height = `${bucket.size / max * 100}%`;
      track.append(fill);
      const caption = document.createElement('span'); caption.className = 'bar-caption'; caption.textContent = label;
      element.append(count, track, caption); chart.append(element);
    });
    document.getElementById('activity-empty').hidden = total !== 0 || selectedDay !== null;
    chartAnimation?.cancel();
    if (animate && !reducedMotion.matches && !chart.closest('[hidden]')) {
      const height = chart.getBoundingClientRect().height;
      chartAnimation = chart.animate([
        {height: `${oldHeight}px`, opacity: .25, transform: 'translateY(8px)'},
        {height: `${height}px`, opacity: 1, transform: 'translateY(0)'}
      ], {duration: 420, easing: 'cubic-bezier(.16, 1, .3, 1)'});
      chart.querySelectorAll('.bar-fill').forEach((bar, index) => bar.animate(
        [{transform:'scaleY(.15)'},{transform:'scaleY(1)'}],
        {duration:480,delay:index*24,easing:'cubic-bezier(.22, 1, .36, 1)',fill:'backwards'}));
    }
  }
  function configure(settings) {
    historyWeeks = normalizeHistoryWeeks(settings?.historyWeeks);
    weeksInput.value = historyWeeks;
    if (offset < -(historyWeeks - 1)) { offset = -(historyWeeks - 1); selectedDay = null; }
    render();
  }
  document.getElementById('activity-settings').addEventListener('submit', async event => {
    event.preventDefault();
    const button = document.getElementById('activity-save-weeks');
    const value = weeksInput.valueAsNumber;
    if (!weeksInput.reportValidity()) return;
    button.disabled = true;
    status.textContent = '保存中…';
    try {
      const result = await chrome.runtime.sendMessage({ type: 'saveSettings', settings: { historyWeeks: value } });
      if (!result?.success) throw Error(result?.error || '保存失败');
      configure({ historyWeeks: value });
      status.textContent = `已保存：最近 ${value} 周（含本周）`;
    } catch (error) { status.textContent = error.message; }
    finally { button.disabled = false; }
  });
  chrome.storage.onChanged.addListener((changes, area) => {
    if (area === 'local' && changes.settings) configure(changes.settings.newValue);
  });
  document.getElementById('activity-prev').addEventListener('click', () => { offset = Math.max(-(historyWeeks - 1), offset - 1); selectedDay = null; render(true); });
  document.getElementById('activity-next').addEventListener('click', () => { offset = Math.min(0, offset + 1); selectedDay = null; render(true); });
  document.getElementById('activity-today').addEventListener('click', () => { offset = 0; selectedDay = null; render(true); });
  back.addEventListener('click', () => { const index = selectedDay; selectedDay = null; render(true); chart.children[index]?.focus(); });
  const toggle = document.getElementById('activity-toggle');
  const content = document.getElementById('activity-content');
  let expanded = true, collapseAnimation, toggleVersion = 0;
  toggle.addEventListener('click', async () => {
    const version = ++toggleVersion;
    collapseAnimation?.cancel();
    const oldHeight = content.hidden ? 0 : content.getBoundingClientRect().height;
    expanded = !expanded;
    toggle.textContent = expanded ? '收起' : '展开';
    toggle.setAttribute('aria-expanded', String(expanded));
    content.hidden = false;
    if (!reducedMotion.matches) {
      collapseAnimation = content.animate([
        {height: `${oldHeight}px`, opacity: expanded ? 0 : 1},
        {height: `${expanded ? content.scrollHeight : 0}px`, opacity: expanded ? 1 : 0}
      ], {duration:360,easing:'cubic-bezier(.16, 1, .3, 1)',fill:'both'});
      await collapseAnimation.finished.catch(() => {});
      if (version !== toggleVersion) return;
      collapseAnimation.cancel();
    }
    content.hidden = !expanded;
  });
  return { configure, update(creators) { list = creators; render(); } };
}
