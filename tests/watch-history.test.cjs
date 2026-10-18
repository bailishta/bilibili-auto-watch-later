// node --experimental-vm-modules --test tests/watch-history.test.cjs
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');

// Real API, persistence and sync modules; only HTTP and Chrome storage are replaced.
function harness(initial = {}, reply = () => ({ code: 0, data: { list: [] } })) {
  const data = structuredClone(initial);
  const urls = [];
  const warnings = [];
  const writes = [];
  const context = vm.createContext({
    URLSearchParams,
    console: { warn: (...args) => warnings.push(args), error: console.error },
    chrome: {
      cookies: { get: async ({ name }) => ({ value: name === 'DedeUserID' ? '123' : 'test' }) },
      storage: { local: {
        get: async key => key === null ? structuredClone(data) : ({ [key]: structuredClone(data[key]) }),
        set: async values => { writes.push(structuredClone(values)); Object.assign(data, structuredClone(values)); }
      } }
    },
    fetch: async url => {
      urls.push(new URL(url));
      return { json: async () => reply(urls.at(-1), urls.length) };
    }
  });
  const modules = new Map();
  function moduleFor(filename) {
    if (!modules.has(filename)) modules.set(filename, new vm.SourceTextModule(
      fs.readFileSync(filename, 'utf8'), { context, identifier: filename }));
    return modules.get(filename);
  }
  async function load(relative) {
    const module = moduleFor(path.join(root, relative));
    if (module.status === 'unlinked') await module.link((specifier, from) =>
      moduleFor(path.resolve(path.dirname(from.identifier), specifier)));
    if (module.status === 'linked') await module.evaluate();
    return module.namespace;
  }
  return { data, urls, warnings, writes, load, context };
}

const historyPage = (bvids, max, view_at, business = 'archive', watchedAt) => ({
  code: 0,
  data: { list: bvids.map(bvid => ({ history: { bvid }, view_at: watchedAt })), cursor: { max, view_at, business } }
});

test('pagination replays all three server cursor fields, deduplicates and stops at repeated cursors', async () => {
  const h = harness({}, (_, page) => page === 1
    ? historyPage(['BVone', 'BVtwo'], 456, 1000)
    : historyPage(['BVtwo', 'BVthree'], 456, 1000));
  const savedPages = [];
  const result = await (await h.load('core/api.js')).getWatchHistory(500, async ids => savedPages.push([...ids]));
  assert.deepEqual([...result], ['BVone', 'BVtwo', 'BVthree']);
  assert.equal(h.urls.length, 2);
  assert.equal(h.urls[0].pathname, '/x/web-interface/history/cursor');
  assert.equal(h.urls[0].searchParams.get('type'), 'archive');
  assert.equal(h.urls[1].searchParams.get('max'), '456');
  assert.equal(h.urls[1].searchParams.get('view_at'), '1000');
  assert.equal(h.urls[1].searchParams.get('business'), 'archive');
  assert.equal(savedPages.length, 2);
});

test('a sync reads exactly 500 rows in 17 full-page requests and retains more than 500 locally', async () => {
  const old = Array.from({ length: 600 }, (_, i) => `BVold${i}`);
  const h = harness({ 'watchedVideos:123': old }, (_, page) =>
    historyPage(Array.from({ length: 30 }, (_, i) => `BVnew${(page - 1) * 30 + i}`), page, 1000 - page));
  const watched = await (await h.load('core/watch-history.js')).getWatchedVideos({ DedeUserID: '123' });
  assert.equal(h.urls.length, 17);
  assert.equal(watched.size, 1100);
  assert.equal(Object.keys(h.data['watchedVideos:123'].videos).length, 1100);
  assert.ok(watched.has('BVold0'));
  assert.ok(watched.has('BVnew499'));
  assert.ok(!watched.has('BVnew500'));
  assert.equal(h.writes.length, 20); // migration, quota reservation, 17 pages, sync checkpoint
  const restarted = harness(h.data);
  const later = await (await restarted.load('core/watch-history.js')).getWatchedVideos({ DedeUserID: '123' });
  assert.equal(later.size, 1100);
  assert.ok(later.has('BVold0'));
});

test('a failed later page preserves earlier pages and old history after restart', async () => {
  const h = harness({ 'watchedVideos:123': ['BVold'] }, (_, page) => page === 1
    ? historyPage(['BVrecent'], 123, 1000)
    : { code: -352 });
  const watched = await (await h.load('core/watch-history.js')).getWatchedVideos({ DedeUserID: '123' });
  assert.deepEqual([...watched], ['BVold', 'BVrecent']);
  assert.equal(h.warnings.length, 1);
  const restarted = harness(h.data, () => ({ code: -101 }));
  const later = await (await restarted.load('core/watch-history.js')).getWatchedVideos({ DedeUserID: '123' });
  assert.deepEqual([...later], ['BVold', 'BVrecent']);
});

test('account isolation and concurrent appends preserve independent histories without trimming', async () => {
  const h = harness();
  const storage = await h.load('core/storage.js');
  await Promise.all([
    storage.mergeWatchedVideos('123', ['BVone']),
    storage.mergeWatchedVideos('123', ['BVtwo', 'BVone']),
    storage.mergeWatchedVideos('456', ['BVother'])
  ]);
  assert.deepEqual([...(await storage.getWatchedVideos('123'))], ['BVone', 'BVtwo']);
  assert.deepEqual([...(await storage.getWatchedVideos('456'))], ['BVother']);
  await assert.rejects(storage.mergeWatchedVideos(null, ['BVwrong']));
});

test('missing UID is resolved using the logged-in user before storing history', async () => {
  const h = harness({}, url => url.pathname.endsWith('/nav')
    ? { code: 0, data: { mid: 123, uname: 'test' } }
    : historyPage(['BVone'], 0, 0));
  await (await h.load('core/watch-history.js')).getWatchedVideos({});
  assert.deepEqual(Object.keys(h.data['watchedVideos:123'].videos), ['BVone']);
  assert.equal(h.urls[0].pathname, '/x/web-interface/nav');
});

test('expired watches are pruned by viewing time and polling does not renew their age', async () => {
  const now = Math.floor(Date.now() / 1000);
  const boundary = new Date(now * 1000);
  boundary.setHours(0, 0, 0, 0);
  boundary.setDate(boundary.getDate() - 2);
  const cutoff = boundary.getTime() / 1000;
  const h = harness({ 'watchedVideos:123': { version: 2, videos: {
    BVexpired: cutoff - 1,
    BVedge: cutoff,
    BVrecent: now - 3600
  }, syncedAt: 0 } });
  const storage = await h.load('core/storage.js');
  await storage.mergeWatchedVideos('123', ['BVrecent'], { records: [{ bvid: 'BVrecent', viewAt: now - 3600 }] });
  const retained = await storage.getWatchedVideos('123');
  assert.ok(!retained.has('BVexpired'));
  assert.ok(retained.has('BVedge'));
  assert.equal(h.data['watchedVideos:123'].videos.BVrecent, now - 3600);
  await storage.mergeWatchedVideos('123', ['BVrecent'], { records: [{ bvid: 'BVrecent', viewAt: now - 4000 }] });
  assert.equal(h.data['watchedVideos:123'].videos.BVrecent, now - 3600);
});

test('incremental polling stops after reaching the previous successful sync with overlap', async () => {
  const now = Math.floor(Date.now() / 1000);
  const h = harness({ 'watchedVideos:123': { version: 2, videos: { BVsaved: now - 3600 }, syncedAt: now - 900 } },
    () => ({ code: 0, data: { list: [
      { history: { bvid: 'BVnew' }, view_at: now - 100 },
      { history: { bvid: 'BVoverlap' }, view_at: now - 1000 }
    ], cursor: { max: 100, view_at: now - 1000, business: 'archive' } } }));
  const watched = await (await h.load('core/watch-history.js')).getWatchedVideos({ DedeUserID: '123' });
  assert.equal(h.urls.length, 1);
  assert.ok(watched.has('BVsaved') && watched.has('BVnew') && watched.has('BVoverlap'));
  assert.equal(h.data['watchedVideos:123'].videos.BVnew, now - 100);
  assert.ok(h.data['watchedVideos:123'].syncedAt >= now);
});

test('legacy BVID records migrate once and narrowing retention also cleans other accounts', async () => {
  const now = Math.floor(Date.now() / 1000);
  const h = harness({
    'watchedVideos:123': ['BVlegacy'],
    'watchedVideos:456': { version: 2, videos: { BVold: now - 72 * 3600, BVnew: now - 3600 }, syncedAt: 0 }
  });
  const storage = await h.load('core/storage.js');
  await storage.pruneAllWatchedVideos(48);
  assert.equal(h.data['watchedVideos:123'].version, 2);
  assert.equal(h.data['watchedVideos:123'].videos.BVlegacy, now);
  assert.deepEqual(Object.keys(h.data['watchedVideos:456'].videos), ['BVnew']);
  const restarted = harness(h.data);
  await (await restarted.load('core/storage.js')).getWatchedVideos('123');
  assert.equal(restarted.data['watchedVideos:123'].videos.BVlegacy, now);
});

test('expanding the retention window backfills a new baseline instead of skipping previously pruned history', async () => {
  const now = Math.floor(Date.now() / 1000);
  const h = harness({ 'watchedVideos:123': { version: 2, videos: {}, syncedAt: now - 60, retentionHours: 48 } },
    (_, page) => page === 1 ? historyPage(['BVolder'], 10, now - 72 * 3600, 'archive', now - 72 * 3600)
      : historyPage([], 0, 0));
  const watched = await (await h.load('core/watch-history.js')).getWatchedVideos({ DedeUserID: '123' }, { retentionHours: 144 });
  assert.equal(h.urls.length, 2);
  assert.ok(watched.has('BVolder'));
  assert.equal(h.data['watchedVideos:123'].retentionHours, 144);
});

test('Saturday retains all of Thursday, Friday and Saturday and only expires Thursday on Sunday', async () => {
  let now = new Date(2026, 9, 10, 18).getTime();
  const h = harness({ 'watchedVideos:123': { version: 2, retentionHours: 72, videos: {
    BVwednesday: new Date(2026, 9, 7, 23, 59, 59).getTime() / 1000,
    BVthursday: new Date(2026, 9, 8).getTime() / 1000,
    BVfriday: new Date(2026, 9, 9).getTime() / 1000,
    BVsaturday: new Date(2026, 9, 10, 17).getTime() / 1000
  } } });
  h.context.Date = class extends Date { constructor(...args) { super(...(args.length ? args : [now])); } static now() { return now; } };
  const storage = await h.load('core/storage.js');
  assert.deepEqual([...(await storage.getWatchedVideos('123'))], ['BVthursday', 'BVfriday', 'BVsaturday']);
  now = new Date(2026, 9, 10, 23, 59, 59).getTime();
  assert.ok((await storage.getWatchedVideos('123')).has('BVthursday'));
  now = new Date(2026, 9, 11).getTime();
  assert.deepEqual([...(await storage.getWatchedVideos('123'))], ['BVfriday', 'BVsaturday']);
});

test('all sync triggers share the persisted twice-per-day quota and twelve-hour interval', async () => {
  let now = new Date(2026, 9, 9, 0).getTime();
  const clock = context => { context.Date = class extends Date {
    constructor(...args) { super(...(args.length ? args : [now])); } static now() { return now; }
  }; };
  const h = harness({}, () => historyPage(['BVseen'], 0, 0)); clock(h.context);
  const sync = await h.load('core/watch-history.js');
  await sync.getWatchedVideos({ DedeUserID: '123' });
  await sync.getWatchedVideos({ DedeUserID: '123' });
  assert.equal(h.urls.length, 1);
  now += 12 * 3600000;
  await sync.getWatchedVideos({ DedeUserID: '123' });
  assert.equal(h.urls.length, 2);
  assert.equal(h.data._historySyncBudget.count, 2);
  const restarted = harness(h.data); clock(restarted.context);
  now += 6 * 3600000;
  await (await restarted.load('core/watch-history.js')).getWatchedVideos({ DedeUserID: '123' });
  assert.equal(restarted.urls.length, 0);
  now = new Date(2026, 9, 10, 0).getTime();
  await (await restarted.load('core/watch-history.js')).getWatchedVideos({ DedeUserID: '123' });
  assert.equal(restarted.urls.length, 1);
  assert.equal(restarted.data._historySyncBudget.count, 1);
});

test('failed and simultaneous sync attempts cannot bypass the collection quota', async () => {
  const h = harness({}, () => ({ code: -352 }));
  const sync = await h.load('core/watch-history.js');
  await Promise.all([sync.getWatchedVideos({ DedeUserID: '123' }), sync.getWatchedVideos({ DedeUserID: '123' })]);
  assert.equal(h.urls.length, 1);
  assert.equal(h.data._historySyncBudget.count, 1);
  assert.equal(h.warnings.length, 1);
});
