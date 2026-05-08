const KEYS = {
  TRACKING_LIST: 'trackingList',
  TRACKED_VIDEOS: 'trackedVideos',
  STATS: 'stats',
  SETTINGS: 'settings'
};

// ── 追踪名单（用户手动管理的UP主列表） ──

export async function getTrackingList() {
  const result = await chrome.storage.local.get(KEYS.TRACKING_LIST);
  return result[KEYS.TRACKING_LIST] || {};
}

export async function addToTrackingList(mid, info) {
  const list = await getTrackingList();
  list[mid] = {
    name: info.name,
    face: info.face || '',
    addedAt: Date.now()
  };
  await chrome.storage.local.set({ [KEYS.TRACKING_LIST]: list });
}

export async function removeFromTrackingList(mid) {
  const list = await getTrackingList();
  delete list[mid];
  await chrome.storage.local.set({ [KEYS.TRACKING_LIST]: list });
}

export async function isInTrackingList(mid) {
  const list = await getTrackingList();
  return !!list[mid];
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

export async function getSettings() {
  const result = await chrome.storage.local.get(KEYS.SETTINGS);
  return result[KEYS.SETTINGS] || { checkIntervalMinutes: 1440, importDone: false };
}

export async function saveSettings(settings) {
  const current = await getSettings();
  await chrome.storage.local.set({ [KEYS.SETTINGS]: { ...current, ...settings } });
}
