import { normalizeHistoryWeeks } from './activity.js';

export const DEFAULT_SCHEDULE = { 0: '18:00', 6: '18:00' };
export const WINDOW_HOURS = [12, 24, 48, 72, 168];

export function normalizeChecker(value) {
  if (!value || !/^[\w-]{1,80}$/.test(value.id || '')) throw Error('检查器标识无效');
  const name = String(value.name || '').trim();
  if (!name || name.length > 60) throw Error('检查器名称需为 1–60 个字符');
  const schedule = {};
  for (const [day, time] of Object.entries(value.schedule || {})) {
    if (!/^[0-6]$/.test(day) || !/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) throw Error('排程时间无效');
    schedule[day] = time;
  }
  const hours = Number(value.newVideoWindowHours ?? 24);
  if (!WINDOW_HOURS.includes(hours)) throw Error('新视频时间范围无效');
  const scope = value.scope === 'selected' ? 'selected' : 'all';
  const mids = [...new Set((value.mids || []).map(String))];
  if (mids.some(mid => !/^\d+$/.test(mid))) throw Error('UP 主 UID 无效');
  if (scope === 'selected' && !mids.length) throw Error('请至少选择一位 UP 主');
  return { id: value.id, name, enabled: value.enabled !== false, schedule,
    newVideoWindowHours: hours, scope, mids: scope === 'all' ? [] : mids };
}

export function migrateSettings(raw = {}) {
  const settings = { importDone: false, newVideoWindowHours: 24, ...raw };
  settings.historyWeeks = normalizeHistoryWeeks(raw.historyWeeks);
  if (!Array.isArray(raw.checkers)) {
    const schedule = raw.schedule ?? (raw.checkDays
      ? Object.fromEntries(raw.checkDays.map(day => [day, raw.checkTime || '18:00']))
      : DEFAULT_SCHEDULE);
    settings.checkers = [normalizeChecker({ id: 'default', name: '默认检查器',
      schedule, newVideoWindowHours: settings.newVideoWindowHours })];
  }
  if (settings.sortMode === 'freq-desc') settings.sortMode = 'updated-desc';
  if (settings.sortMode === 'freq-asc') settings.sortMode = 'updated-asc';
  delete settings.schedule;
  delete settings.checkDays;
  delete settings.checkTime;
  return settings;
}

export function calculateNextCheckTime(schedule, now = new Date()) {
  for (let offset = 0; offset <= 7; offset++) {
    const date = new Date(now);
    date.setDate(date.getDate() + offset);
    const time = schedule?.[date.getDay()];
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(time || '')) continue;
    const [hour, minute] = time.split(':').map(Number);
    date.setHours(hour, minute, 0, 0);
    if (date > now) return date.getTime();
  }
  return null;
}
