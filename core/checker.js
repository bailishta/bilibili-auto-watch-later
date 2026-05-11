import * as storage from './storage.js';
import * as api from './api.js';

const DELETED_NAMES = ['已注销用户', '账号已注销', '已注销'];
const REQUEST_DELAY = 200; // ms，每批UP主之间的间隔
const CONCURRENCY = 6; // 并行处理的UP主数量
const BATCH_STAGGER = 30; // ms，批次内错开启动的间隔

function getNewVideoWindowMs(hours) {
  return (hours || 24) * 60 * 60 * 1000;
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function isDeletedAccount(name) {
  return DELETED_NAMES.includes(name?.trim());
}

// ── 检测追踪名单中UP主的新视频 ──

export async function checkForNewVideos(sendProgress) {
  const report = { newVideos: [], skipped: [], added: 0, errors: 0 };
  const cookies = await api.getCookies();
  if (!cookies.SESSDATA) {
    report.status = 'not_logged_in';
    sendProgress?.({ type: 'complete', report });
    return report;
  }

  const trackingList = await storage.getTrackingList();
  const mids = Object.keys(trackingList);
  if (mids.length === 0) {
    report.status = 'empty_list';
    sendProgress?.({ type: 'complete', report });
    return report;
  }

  const settings = await storage.getSettings();
  const windowMs = getNewVideoWindowMs(settings.newVideoWindowHours);
  const cutOff = Math.floor((Date.now() - windowMs) / 1000);

  let watchedBvids = new Set();
  try {
    const history = await api.getWatchHistory(200);
    watchedBvids = new Set(history);
  } catch (e) {
    console.warn('[checker] 获取观看历史失败，跳过此过滤:', e.message);
  }

  let checked = 0;

  // 并行批处理：每次并发处理 CONCURRENCY 个UP主
  for (let i = 0; i < mids.length; i += CONCURRENCY) {
    const batch = mids.slice(i, i + CONCURRENCY);

    // 阶段1：并行拉取所有UP主的视频列表
    const batchPromises = batch.map(async (mid, bi) => {
      await sleep(bi * BATCH_STAGGER);

      const upName = trackingList[mid].name;
      try {
        checked++;
        sendProgress?.({ type: 'progress', current: checked, total: mids.length, name: upName });

        const result = await api.getUserVideos(mid);
        if (result.error) {
          report.skipped.push({ mid, name: upName, reason: result.error });
          report.errors++;
          return { mid, upName, aidsToAdd: [], newVids: [] };
        }

        const newVids = [];
        const aidsToAdd = [];
        for (const v of (result.videos || [])) {
          if (v.pubdate < cutOff) continue;
          if (watchedBvids.has(v.bvid)) continue;
          if (await storage.isVideoTracked(v.bvid)) continue;
          aidsToAdd.push(v.aid);
          newVids.push(v);
        }
        return { mid, upName, aidsToAdd, newVids };
      } catch {
        report.skipped.push({ mid, name: upName, reason: 'exception' });
        report.errors++;
        return { mid, upName, aidsToAdd: [], newVids: [] };
      }
    });

    const batchResults = await Promise.all(batchPromises);

    // 阶段2：合并整批待添加视频，一次往返完成
    const allAids = [];
    const allNewVids = [];
    for (const r of batchResults) {
      if (r.aidsToAdd.length > 0) {
        allAids.push(...r.aidsToAdd);
        allNewVids.push(...r.newVids);
      }
    }

    if (allAids.length > 0) {
      const results = await api.addToWatchLaterBatch(allAids);
      for (let j = 0; j < allAids.length; j++) {
        const r = results[j];
        if (r && r.code === 0) {
          const v = allNewVids[j];
          await storage.markVideoTracked(v.bvid, {
            title: v.title,
            mid: v.mid || '',
            author: v.author || '',
            pubdate: v.pubdate
          });
          report.newVideos.push({ bvid: v.bvid, title: v.title, author: v.author || '' });
          report.added++;
        }
      }
    }

    // 每批完成后检查是否取消
    const { _checkCancelled } = await chrome.storage.local.get('_checkCancelled');
    if (_checkCancelled) {
      await chrome.storage.local.remove('_checkCancelled');
      report.status = 'cancelled';
      sendProgress?.({ type: 'complete', report });
      return report;
    }

    // 批次间间隔
    if (i + CONCURRENCY < mids.length) {
      await sleep(REQUEST_DELAY);
    }
  }

  const stats = await storage.getStats();
  stats.totalAdded += report.added;
  stats.lastCheck = Date.now();
  stats.lastNewCount = report.added;
  await storage.saveStats(stats);

  report.status = 'ok';
  sendProgress?.({ type: 'complete', report });
  return report;
}

// ── 导入关注列表到追踪名单 ──

export async function importFollowList(uid, sendProgress) {
  const result = { imported: 0, skipped: 0, total: 0 };
  const cookies = await api.getCookies();
  if (!cookies.SESSDATA) {
    result.status = 'not_logged_in';
    return result;
  }

  let page = 1;
  let hasMore = true;

  while (hasMore) {
    try {
      const { list, total, hasMore: more } = await api.getFollowList(uid, page);
      if (page === 1) result.total = total;

      for (const user of list) {
        // 跳过已注销账号
        if (isDeletedAccount(user.name)) {
          result.skipped++;
          continue;
        }

        // 已存在则跳过
        if (await storage.isInTrackingList(user.mid)) continue;

        await storage.addToTrackingList(user.mid, {
          name: user.name,
          face: user.face
        });
        result.imported++;
      }

      sendProgress?.({
        type: 'import-progress',
        page,
        imported: result.imported,
        skipped: result.skipped,
        total: result.total
      });

      hasMore = more;
      page++;
      await sleep(500);
    } catch {
      break;
    }
  }

  await storage.saveSettings({ importDone: true });
  result.status = 'ok';
  sendProgress?.({ type: 'import-complete', result });
  return result;
}
