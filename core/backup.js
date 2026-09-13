import { normalizeChecker, WINDOW_HOURS } from './schedules.js';
import { mergePublications, normalizeHistoryWeeks } from './activity.js';

export const BACKUP_FORMAT = 'bilibili-watch-later-configuration';
export const BACKUP_VERSION = 1;
export const MAX_BACKUP_BYTES = 25 * 1024 * 1024;
const object = value => value && typeof value === 'object' && !Array.isArray(value);
const number = value => typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : null;

// Only explicitly supported configuration fields are restored; runtime state and login data are excluded.
export function validateBackup(backup) {
  if (!object(backup) || backup.format !== BACKUP_FORMAT || backup.version !== BACKUP_VERSION) throw Error('不是支持的配置备份文件（需要版本 1）');
  const raw = backup.settings;
  if (!object(raw) || !Array.isArray(raw.checkers) || !Array.isArray(backup.creators)) throw Error('备份缺少设置、检查器或追踪名单');
  const hours = raw.newVideoWindowHours ?? 24;
  const weeks = raw.historyWeeks ?? 3;
  if (!WINDOW_HOURS.includes(hours)) throw Error('备份中的手动检查范围无效');
  if (normalizeHistoryWeeks(weeks) !== weeks) throw Error('备份中的记录保留周数无效');
  const sortMode = raw.sortMode ?? 'default';
  if (!['default','fans-desc','fans-asc','updated-desc','updated-asc'].includes(sortMode)) throw Error('备份中的排序方式无效');
  const checkers = raw.checkers.map(normalizeChecker);
  if (new Set(checkers.map(checker => checker.id)).size !== checkers.length) throw Error('备份包含重复的检查器标识');
  const trackingList = {};
  for (const creator of backup.creators) {
    if (!object(creator) || !/^\d+$/.test(String(creator.mid || ''))) throw Error('备份中存在无效 UP 主 UID');
    const mid = String(creator.mid);
    if (Object.hasOwn(trackingList, mid)) throw Error(`备份包含重复 UID：${mid}`);
    if (creator.publications != null && !Array.isArray(creator.publications)) throw Error('备份中的更新记录格式无效');
    if (creator.publications?.some(record => !object(record) || number(record.pubdate) === null)) throw Error('备份中的发布时间无效');
    trackingList[mid] = {
      name: typeof creator.name === 'string' && creator.name.trim() ? creator.name : `UP主_${mid}`,
      face: typeof creator.face === 'string' ? creator.face : '',
      addedAt: number(creator.addedAt) ?? Date.now(), fans: number(creator.fans),
      lastPubdate: number(creator.lastPubdate), metaUpdatedAt: number(creator.metaUpdatedAt),
      publications: mergePublications(creator.publications || [], [], Date.now(), weeks)
    };
  }
  return { settings: {checkers, newVideoWindowHours: hours, historyWeeks: weeks, sortMode, importDone: raw.importDone === true}, trackingList };
}

export function createBackup(settings, trackingList) {
  const source = { format: BACKUP_FORMAT, version: BACKUP_VERSION, settings,
    creators: Object.entries(trackingList).map(([mid, creator]) => ({ ...creator, mid })) };
  const clean = validateBackup(source);
  return { format: BACKUP_FORMAT, version: BACKUP_VERSION, exportedAt: new Date().toISOString(),
    settings: clean.settings, creators: Object.entries(clean.trackingList).map(([mid, creator]) => ({mid, ...creator})) };
}
