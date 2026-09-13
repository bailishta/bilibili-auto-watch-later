// ── 追踪名单排序元数据 ──
// 粉丝数（fans）来自 card API；更新时间（lastPubdate）与更新记录来自视频发布时间。
// checker.js 在检查时复用视频列表并查询粉丝数，
// refreshCreatorMeta() 提供全量刷新（每UP主2个请求：视频列表 + card）。

import * as api from './api.js';
import * as storage from './storage.js';

const CONCURRENCY = 6; // 并行处理的UP主数量（与 checker 保持一致）
const BATCH_STAGGER = 30; // ms，批次内错开启动的间隔
const REQUEST_DELAY = 200; // ms，每批UP主之间的间隔

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// 最近发布时间与已观察到的视频记录；不再计算平均更新间隔。
export function computeUpdateMeta(videos) {
  const valid = (videos || []).filter(v => Number.isFinite(v?.pubdate) && v.pubdate > 0 && v.pubdate <= Date.now() / 1000);
  return {
    lastPubdate: valid.length ? Math.max(...valid.map(v => v.pubdate)) : null,
    publications: valid.map(v => ({ bvid: v.bvid || '', pubdate: v.pubdate }))
  };
}

// 复用本次检查已获取的视频列表，只额外查询粉丝数。
// 两个来源独立更新；失败或缺失的字段不覆盖持久化的旧数据。
export async function collectCreatorMeta(mid, videos) {
  const meta = { mid, hasData: false };
  if (videos) {
    Object.assign(meta, computeUpdateMeta(videos));
    meta.hasData = meta.lastPubdate != null;
  }
  try {
    const info = await api.getUpInfo(mid);
    if (info?.fans != null) {
      meta.fans = info.fans;
      meta.hasData = true;
    }
  } catch {
    // 粉丝数请求失败不影响检查或更新时间。
  }
  return meta;
}

// 全量刷新追踪名单的排序元数据（粉丝数 + 更新时间），
// 复用 checker 的并发参数与 _checkCancelled 取消标志
export async function refreshCreatorMeta(sendProgress) {
  const report = { updated: 0, errors: 0 };
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

  let current = 0;

  for (let i = 0; i < mids.length; i += CONCURRENCY) {
    const batch = mids.slice(i, i + CONCURRENCY);

    const results = await Promise.all(batch.map(async (mid, bi) => {
      await sleep(bi * BATCH_STAGGER);
      current++;
      sendProgress?.({ type: 'progress', current, total: mids.length, name: trackingList[mid].name });

      let videos;
      // 视频列表（WBI签名）→ 更新时间
      try {
        const videosRes = await api.getUserVideos(mid);
        if (!videosRes.error) {
          videos = videosRes.videos;
        } else {
          report.errors++;
        }
      } catch {
        report.errors++;
      }
      return collectCreatorMeta(mid, videos);
    }));

    // 两项数据都没拿到时跳过写库，避免无意义更新
    const writable = results.filter(r => r.hasData);
    await storage.updateCreatorMetaBatch(writable);
    report.updated += writable.length;

    // 每批完成后检查是否取消（与 checker 共用 _checkCancelled 标志）
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

  report.status = 'ok';
  sendProgress?.({ type: 'complete', report });
  return report;
}
