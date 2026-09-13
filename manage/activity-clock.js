const NS = 'http://www.w3.org/2000/svg';
const svgNode = (tag, attrs = {}, text = '') => {
  const node = document.createElementNS(NS, tag);
  for (const [key, value] of Object.entries(attrs)) node.setAttribute(key, value);
  if (text) node.textContent = text;
  return node;
};
const point = (radius, degrees) => {
  const angle = (degrees - 90) * Math.PI / 180;
  return [240 + radius * Math.cos(angle), 240 + radius * Math.sin(angle)];
};
const minuteLabel = minute => `${Math.floor(minute / 60) % 12 || 12}:${String(minute % 60).padStart(2, '0')}`;
const periodLabel = minute => minute < 720 ? '上午' : '下午';

function creatorChip(mid, creator = {}) {
  const name = creator.name || String(mid);
  const item = document.createElement('li'); item.className = 'clock-person'; item.title = `${name} · UID ${mid}`;
  const avatar = document.createElement('span'); avatar.className = 'clock-avatar'; avatar.setAttribute('aria-hidden', 'true');
  avatar.textContent = Array.from(name)[0] || 'U';
  if (creator.face) {
    const image = document.createElement('img');
    image.alt = ''; image.loading = 'lazy'; image.decoding = 'async'; image.referrerPolicy = 'no-referrer';
    image.addEventListener('error', () => image.remove(), { once: true });
    image.src = creator.face.startsWith('//') ? `https:${creator.face}` : creator.face;
    avatar.append(image);
  }
  const label = document.createElement('span'); label.className = 'clock-person-name'; label.textContent = name;
  item.append(avatar, label);
  return item;
}

export function renderActivityClock(host, buckets, creators, minutes) {
  const switcher = document.createElement('div'); switcher.className = 'clock-periods'; switcher.setAttribute('role', 'group'); switcher.setAttribute('aria-label', '选择上午或下午');
  const svg = svgNode('svg', { viewBox: '0 0 480 480', class: 'activity-clock', role: 'group' });
  svg.append(svgNode('circle', { cx: 240, cy: 240, r: 207, class: 'clock-rim' }), svgNode('circle', { cx: 240, cy: 240, r: 200, class: 'clock-face' }));
  for (let tick = 0; tick < 60; tick++) {
    const major = tick % 5 === 0;
    const [x1, y1] = point(major ? 179 : 187, tick * 6), [x2, y2] = point(194, tick * 6);
    svg.append(svgNode('line', { x1, y1, x2, y2, class: `clock-minute-tick${major ? ' clock-major-tick' : ''}` }));
    if (major) {
      const [x, y] = point(157, tick * 6);
      svg.append(svgNode('text', { x, y, 'text-anchor': 'middle', 'dominant-baseline': 'central', class: 'clock-tick' }, String(tick / 5 || 12)));
    }
  }
  const periodText = svgNode('text', { x: 240, y: 208, class: 'clock-period-label', 'text-anchor': 'middle' });
  const count = svgNode('text', { x: 240, y: 256, class: 'clock-count', 'text-anchor': 'middle' });
  svg.append(periodText, count, svgNode('text', { x: 240, y: 285, class: 'clock-unit', 'text-anchor': 'middle' }, '位 UP 主更新'));
  const markers = svgNode('g'); svg.append(markers);
  const detail = document.createElement('div'); detail.className = 'clock-detail';
  const periodButtons = [];
  let entries = [];

  function highlight(minute) {
    for (const entry of entries) {
      const selected = entry.minute === minute;
      entry.marker.setAttribute('aria-pressed', String(selected));
      entry.button.setAttribute('aria-pressed', String(selected));
      entry.row.classList.toggle('is-selected', selected);
    }
  }

  function showPeriod(period, animate = true) {
    entries = []; markers.replaceChildren(); detail.replaceChildren();
    periodButtons.forEach((button, i) => button.setAttribute('aria-pressed', String(i === period)));
    const available = [...minutes.keys()].filter(minute => Math.floor(minute / 720) === period).sort((a, b) => a - b);
    const mids = new Set(available.flatMap(minute => [...minutes.get(minute)]));
    periodText.textContent = period === 0 ? '上午' : '下午'; count.textContent = mids.size;
    svg.setAttribute('aria-label', `${periodText.textContent}更新分布，${mids.size} 位 UP 主，具体时间与名单在下方直接显示`);
    if (!available.length) {
      detail.textContent = `${periodText.textContent}暂无更新`;
      return;
    }
    const records = document.createElement('ul'); records.className = 'clock-records'; records.setAttribute('aria-label', '更新时间与 UP 主');
    for (const minute of available) {
      const bucket = minutes.get(minute);
      const label = `${periodLabel(minute)} ${minuteLabel(minute)}，${bucket.size} 位 UP 主更新`;
      // 按 12 小时表盘的实际时间位置标记，分钟参与角度计算。
      const [x, y] = point(220, (minute % 720) / 2);
      const marker = svgNode('g', { class: 'clock-event', role: 'button', tabindex: '0', 'data-minute': minute, 'aria-label': label, 'aria-pressed': 'false' });
      marker.append(svgNode('title', {}, label), svgNode('circle', { cx: x, cy: y, r: 10, class: 'clock-event-hit' }), svgNode('circle', { cx: x, cy: y, r: 4, class: 'clock-event-dot' }));
      const row = document.createElement('li'); row.className = 'clock-record'; row.dataset.minute = minute;
      const button = document.createElement('button'); button.type = 'button'; button.className = 'clock-record-time'; button.setAttribute('aria-label', label); button.setAttribute('aria-pressed', 'false');
      const time = document.createElement('span'); time.textContent = minuteLabel(minute);
      const countLabel = document.createElement('small'); countLabel.textContent = `${bucket.size}人`;
      button.append(time, countLabel);
      const people = document.createElement('ul'); people.className = 'clock-people';
      for (const mid of bucket) people.append(creatorChip(mid, creators[mid]));
      row.append(button, people); records.append(row); markers.append(marker);
      for (const target of [marker, button]) {
        target.addEventListener('click', () => highlight(minute));
        target.addEventListener('mouseenter', () => highlight(minute));
        target.addEventListener('focus', () => highlight(minute));
      }
      marker.addEventListener('keydown', event => {
        if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); highlight(minute); }
        if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
          event.preventDefault();
          const index = available.indexOf(minute);
          entries[(index + (event.key === 'ArrowRight' ? 1 : available.length - 1)) % available.length].marker.focus();
        }
      });
      entries.push({ minute, marker, button, row });
    }
    detail.append(records);
    if (animate && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      detail.getAnimations().forEach(animation => animation.cancel());
      detail.animate([{opacity:.25,transform:'translateY(6px)'},{opacity:1,transform:'translateY(0)'}], {duration:320,easing:'cubic-bezier(.16,1,.3,1)'});
    }
  }
  ['上午', '下午'].forEach((label, index) => {
    const button = document.createElement('button'); button.type = 'button'; button.textContent = label;
    button.dataset.period = index; button.addEventListener('click', () => showPeriod(index));
    periodButtons.push(button); switcher.append(button);
  });
  host.append(switcher, svg, detail);
  showPeriod(0, false);
}
