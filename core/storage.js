import { migrateSettings, normalizeChecker } from './schedules.js';
import { mergePublications, normalizeHistoryWeeks, MAX_HISTORY_WEEKS } from './activity.js';
import { createBackup, validateBackup } from './backup.js';

const KEYS = {
  TRACKING_LIST: 'trackingList',
  TRACKED_VIDEOS: 'trackedVideos',
  STATS: 'stats',
  SETTINGS: 'settings'
};

// ── 追踪名单（用户手动管理的UP主列表） ──

// 名单增删、导入和检查后的元数据写入都由后台串行处理，
// 避免并发读改写整张名单时覆盖已保存的数据。
let trackingWrites = Promise.resolve();

function updateTrackingList(update) {
  const pending = trackingWrites.then(async () => {
    const list = await getTrackingList();
    const result = await update(list);
    await chrome.storage.local.set({ [KEYS.TRACKING_LIST]: list });
    return result;
  });
  trackingWrites = pending.catch(() => {});
  return pending;
}

export async function getTrackingList() {
  const result = await chrome.storage.local.get(KEYS.TRACKING_LIST);
  return result[KEYS.TRACKING_LIST] || {};
}

export async function addToTrackingList(mid, info) {
  return updateTrackingList(list => {
    list[mid] = {
      name: info.name,
      face: info.face || '',
      addedAt: Date.now(),
      // 排序元数据：fans 粉丝数，
      // lastPubdate 最近发布时间，metaUpdatedAt 元数据更新时间。缺失时为 null
      fans: info.fans ?? null,
      lastPubdate: info.lastPubdate ?? null,
      publications: [],
      metaUpdatedAt: null
    };
  });
}

// 批量合并写入排序元数据：一次读一次写，只更新已存在的条目，
// 不覆盖 name/face/addedAt；新值为 null 时保留旧值
export async function updateCreatorMetaBatch(metas) {
  if (!Array.isArray(metas) || metas.length === 0) return;
  return updateTrackingList(async list => {
    const { historyWeeks } = await getSettings();
    for (const m of metas) {
      const entry = list[m.mid];
      if (!entry) continue;
      list[m.mid] = {
        ...entry,
        fans: m.fans ?? entry.fans ?? null,
        lastPubdate: Math.max(m.lastPubdate || 0, entry.lastPubdate || 0) || null,
        publications: mergePublications(entry.publications, m.publications, Date.now(), historyWeeks),
        metaUpdatedAt: m.metaUpdatedAt ?? Date.now()
      };
    }
  });
}

export async function removeFromTrackingList(mid) {
  return updateTrackingList(list => { delete list[mid]; });
}

export async function isInTrackingList(mid) {
  const list = await getTrackingList();
  return !!list[mid];
}

export async function importTrackingList(creators) {
  return updateTrackingList(list => {
    let added = 0;
    let skipped = 0;
    for (const item of creators) {
      if (!item.mid) continue;
      if (list[item.mid]) {
        skipped++;
        continue;
      }
      list[item.mid] = {
        name: item.name || `UP主_${item.mid}`,
        face: item.face || '',
        addedAt: Date.now()
      };
      added++;
    }
    return { added, skipped };
  });
}

export async function getTrackingListCount() {
  const list = await getTrackingList();
  return Object.keys(list).length;
}

// ── 已处理视频 ──

export async function getTrackedVideos() {
  const result = await chrome.storage.local.get(KEYS.TRACKED_VIDEOS);
  return result[KEYS.TRACKED_VIDEOS] || {};
}

const MAX_TRACKED_VIDEOS = 5000;

export async function markVideoTracked(bvid, meta) {
  const videos = await getTrackedVideos();
  videos[bvid] = {
    title: meta.title,
    mid: meta.mid,
    author: meta.author,
    pubdate: meta.pubdate,
    addedAt: Date.now()
  };

  // 超过上限时清理最旧的记录
  const entries = Object.entries(videos);
  if (entries.length > MAX_TRACKED_VIDEOS) {
    entries.sort((a, b) => a[1].addedAt - b[1].addedAt);
    const removeCount = entries.length - MAX_TRACKED_VIDEOS;
    for (let i = 0; i < removeCount; i++) {
      delete videos[entries[i][0]];
    }
  }

  await chrome.storage.local.set({ [KEYS.TRACKED_VIDEOS]: videos });
}

export async function isVideoTracked(bvid) {
  const videos = await getTrackedVideos();
  return !!videos[bvid];
}

export async function getTrackedVideoCount() {
  const videos = await getTrackedVideos();
  return Object.keys(videos).length;
}

// ── 统计信息 ──

export async function getStats() {
  const result = await chrome.storage.local.get(KEYS.STATS);
  return result[KEYS.STATS] || { totalAdded: 0, lastCheck: null, lastNewCount: 0 };
}

export async function saveStats(stats) {
  await chrome.storage.local.set({ [KEYS.STATS]: stats });
}

// ── 设置 ──

let settingsWrites = Promise.resolve();
function updateSettings(update) {
  const pending = settingsWrites.then(async () => {
    const result = await chrome.storage.local.get(KEYS.SETTINGS);
    const settings = migrateSettings(result[KEYS.SETTINGS]);
    const value = update(settings);
    await chrome.storage.local.set({ [KEYS.SETTINGS]: settings });
    return value;
  });
  settingsWrites = pending.catch(() => {});
  return pending;
}

export async function getSettings() {
  await settingsWrites;
  const result = await chrome.storage.local.get(KEYS.SETTINGS);
  const raw = result[KEYS.SETTINGS] || {};
  const migrated = migrateSettings(raw);
  if (JSON.stringify(raw) !== JSON.stringify(migrated)) return updateSettings(settings => ({ ...settings }));
  return migrated;
}

export async function saveSettings(settings) {
  if (settings.historyWeeks !== undefined && normalizeHistoryWeeks(settings.historyWeeks) !== settings.historyWeeks) {
    throw Error(`记录周数必须为 1–${MAX_HISTORY_WEEKS} 的整数`);
  }
  await updateSettings(current => {
    for (const key of ['importDone', 'sortMode', 'newVideoWindowHours', 'historyWeeks']) {
      if (settings[key] !== undefined) current[key] = settings[key];
    }
  });
  if (settings.historyWeeks !== undefined) await prunePublicationHistory();
}

export async function prunePublicationHistory() {
  return updateTrackingList(async list => {
    const { historyWeeks } = await getSettings();
    for (const creator of Object.values(list)) {
      if (creator.publications) creator.publications = mergePublications(creator.publications, [], Date.now(), historyWeeks);
    }
  });
}

export async function saveChecker(value) {
  const checker = normalizeChecker(value);
  return updateSettings(settings => {
    const index = settings.checkers.findIndex(item => item.id === checker.id);
    if (index < 0) settings.checkers.push(checker);
    else settings.checkers[index] = checker;
    return checker;
  });
}

export async function deleteChecker(id) {
  return updateSettings(settings => { settings.checkers = settings.checkers.filter(item => item.id !== id); });
}

export async function exportConfiguration() {
  await getSettings();
  await Promise.all([trackingWrites, settingsWrites]);
  const data = await chrome.storage.local.get([KEYS.SETTINGS, KEYS.TRACKING_LIST]);
  return createBackup(data[KEYS.SETTINGS], data[KEYS.TRACKING_LIST] || {});
}

// The worker prevents other mutations during restore. Validate before the single storage write.
export async function restoreConfiguration(backup) {
  const config = validateBackup(backup);
  await Promise.all([trackingWrites, settingsWrites]);
  await chrome.storage.local.set({ [KEYS.SETTINGS]: config.settings, [KEYS.TRACKING_LIST]: config.trackingList });
  return { checkers: config.settings.checkers.length, creators: Object.keys(config.trackingList).length };
}
