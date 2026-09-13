// 只保存检查接口已经返回的发布时间，不额外按图表查询 B 站。
export const DEFAULT_HISTORY_WEEKS = 3;
export const MAX_HISTORY_WEEKS = 52;
export function normalizeHistoryWeeks(value) {
  return Number.isInteger(value) && value >= 1 && value <= MAX_HISTORY_WEEKS ? value : DEFAULT_HISTORY_WEEKS;
}
export function mergePublications(previous = [], incoming = [], now = Date.now(), weeks = DEFAULT_HISTORY_WEEKS) {
  const start = weekStart(now);
  start.setDate(start.getDate() - (normalizeHistoryWeeks(weeks) - 1) * 7);
  const cutoff = start.getTime() / 1000;
  const entries = new Map();
  for (const item of [...previous, ...incoming]) {
    const pubdate = Number(item.pubdate);
    if (!Number.isFinite(pubdate) || pubdate <= 0 || pubdate < cutoff || pubdate > now / 1000) continue;
    const bvid = typeof item.bvid === 'string' ? item.bvid : '';
    entries.set(bvid || String(pubdate), { bvid, pubdate });
  }
  return [...entries.values()].sort((a, b) => b.pubdate - a.pubdate).slice(0, 500);
}

export function weekStart(value = new Date()) {
  const date = new Date(value);
  date.setHours(0, 0, 0, 0);
  date.setDate(date.getDate() - (date.getDay() + 6) % 7);
  return date;
}

export function summarizeWeek(list, start) {
  const days = Array.from({ length: 7 }, (_, index) => {
    const date = new Date(start); date.setDate(date.getDate() + index);
    return { date, mids: new Set(), hours: Array.from({ length: 24 }, () => new Set()), minutes: new Map() };
  });
  const end = new Date(start); end.setDate(end.getDate() + 7);
  const mids = new Set();
  for (const [mid, creator] of Object.entries(list)) {
    const records = [...(creator.publications || [])];
    if (creator.lastPubdate) records.push({ pubdate: creator.lastPubdate });
    for (const record of records) {
      const date = new Date(record.pubdate * 1000);
      if (!(date >= start && date < end) || date.getTime() > Date.now()) continue;
      const day = days[(date.getDay() + 6) % 7];
      day.mids.add(mid); day.hours[date.getHours()].add(mid); mids.add(mid);
      const minute = date.getHours() * 60 + date.getMinutes();
      if (!day.minutes.has(minute)) day.minutes.set(minute, new Set());
      day.minutes.get(minute).add(mid);
    }
  }
  return { days, mids };
}

export function sortTrackingMids(mids, list, mode) {
  const key = { 'fans-desc': 'fans', 'fans-asc': 'fans',
    'updated-desc': 'lastPubdate', 'updated-asc': 'lastPubdate' }[mode];
  if (!key) return mids;
  return [...mids].sort((a, b) => {
    const va = list[a][key], vb = list[b][key];
    const valid = v => typeof v === 'number' && Number.isFinite(v) && (key !== 'lastPubdate' || v > 0);
    if (!valid(va)) return valid(vb) ? 1 : 0;
    if (!valid(vb)) return -1;
    return mode.endsWith('desc') ? vb - va : va - vb;
  });
}
