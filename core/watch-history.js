import * as api from './api.js';
import * as storage from './storage.js';
import { historyCutoffSeconds } from './history-lifecycle.js';

// 先读本地，再增量采集；只保留生命周期内的观看时间，成功的每页立即保存。
export async function getWatchedVideos(cookies, { retentionHours = 72 } = {}) {
  let uid = cookies.DedeUserID;
  if (!/^[1-9]\d*$/.test(String(uid))) uid = (await api.getCurrentUser())?.uid;
  if (!/^[1-9]\d*$/.test(String(uid))) throw Error('无法识别观看历史所属账号');

  const cached = await storage.getWatchedHistory(uid, retentionHours);
  let watched = new Set(Object.keys(cached.videos));
  if (!await storage.reserveHistorySync()) return watched;
  const startedAt = Math.floor(Date.now() / 1000);
  // 留一分钟重叠，避免同秒记录和服务端上报延迟漏掉。
  const since = Math.max(historyCutoffSeconds(retentionHours, startedAt), cached.syncedAt - 60);
  let savedPages = 0;
  const savePage = async (bvids, records = []) => {
    savedPages++;
    const saved = await storage.mergeWatchedVideos(uid, bvids, { records, retentionHours });
    watched = new Set(Object.keys(saved.videos));
  };
  try {
    const recent = await api.getWatchHistory(500, savePage, since);
    if (!savedPages && recent.length) await savePage(recent);
    await storage.mergeWatchedVideos(uid, [], { retentionHours, syncedAt: startedAt });
  } catch (e) {
    console.warn('[checker] 同步观看历史失败，继续使用本地已观看记录:', e.message);
  }
  return watched;
}
