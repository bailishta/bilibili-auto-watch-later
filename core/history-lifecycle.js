import { calculateNextCheckTime, WINDOW_HOURS } from './schedules.js';

export const HISTORY_POLL_MS = 12 * 60 * 60 * 1000;

export function historyDayKey(now = Date.now()) {
  const date = new Date(now);
  return `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`;
}

export function historyNextSyncTime(budget = {}, now = Date.now()) {
  let next = Math.max(now, (budget.lastAttemptAt || 0) + HISTORY_POLL_MS);
  if (budget.day === historyDayKey(now) && budget.count >= 2) {
    const tomorrow = new Date(now);
    tomorrow.setDate(tomorrow.getDate() + 1);
    tomorrow.setHours(0, 0, 0, 0);
    next = Math.max(next, tomorrow.getTime());
  }
  return next;
}

function windowHours(value) {
  const hours = Number(value);
  return WINDOW_HOURS.includes(hours) ? hours : 24;
}

// 手动检查也需要缓存；多个启用检查器共用最长范围，避免相互清理。
export function historyRetentionHours(settings = {}) {
  const maximum = Math.max(windowHours(settings.newVideoWindowHours),
    ...(settings.checkers || []).filter(c => c.enabled && Object.keys(c.schedule || {}).length)
      .map(c => windowHours(c.newVideoWindowHours)));
  return (Math.ceil(2 * maximum / 24) + 1) * 24;
}

export function historyCollectionPlan(checkers, now = Date.now(), budget = {}) {
  let active = false;
  let nextTime = null;
  const eligibleAt = historyNextSyncTime(budget, now);
  for (const checker of checkers || []) {
    if (!checker.enabled) continue;
    const checkAt = calculateNextCheckTime(checker.schedule, new Date(now));
    if (!checkAt) continue;
    const startAt = checkAt - 2 * windowHours(checker.newVideoWindowHours) * 3600000;
    const inWindow = startAt <= now;
    active ||= inWindow;
    const next = inWindow ? Math.min(eligibleAt > now ? eligibleAt : now + HISTORY_POLL_MS, checkAt)
      : Math.max(startAt, eligibleAt);
    nextTime = nextTime === null ? next : Math.min(nextTime, next);
  }
  return { active, nextTime };
}

// 保留完整自然日，含今天；72 小时的范围表示今天及此前两个自然日。
export function historyCutoffSeconds(retentionHours, now = Math.floor(Date.now() / 1000)) {
  const date = new Date(now * 1000);
  date.setHours(0, 0, 0, 0);
  date.setDate(date.getDate() - Math.ceil(retentionHours / 24) + 1);
  return Math.floor(date.getTime() / 1000);
}

export function normalizeWatchedHistory(raw, now = Math.floor(Date.now() / 1000)) {
  // 旧版仅有 BVID，迁移时赋予一次采集时间，不会在读取时反复延长。
  const videos = {};
  const entries = Array.isArray(raw) ? raw.map(bvid => [bvid, now]) : Object.entries(raw?.videos || {});
  for (const [bvid, stamp] of entries) {
    if (typeof bvid === 'string' && bvid.startsWith('BV') && Number.isFinite(stamp) && stamp > 0) videos[bvid] = Math.min(stamp, now);
  }
  return { version: 2, videos, syncedAt: Math.min(Number(raw?.syncedAt) || 0, now), retentionHours: Number(raw?.retentionHours) || 0 };
}

export function pruneWatchedHistory(history, retentionHours, now = Math.floor(Date.now() / 1000)) {
  // 扩大检查范围时重新拉取基线，补采原来保留范围之外仍可读取的历史。
  if (history.retentionHours > 0 && retentionHours > history.retentionHours) history.syncedAt = 0;
  history.retentionHours = retentionHours;
  const cutoff = historyCutoffSeconds(retentionHours, now);
  for (const [bvid, viewAt] of Object.entries(history.videos)) {
    if (viewAt < cutoff) delete history.videos[bvid];
  }
  return history;
}
