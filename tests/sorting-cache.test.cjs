// node --experimental-vm-modules --test tests/sorting-cache.test.cjs
// Run real extension modules with isolated Chrome storage and Bilibili API doubles.
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const clone = value => structuredClone(value);

function harness(initial = {}, overrides = {}) {
  const data = clone(initial);
  const calls = [];
  const events = {};
  const alarmTimes = [];
  const nodes = new Map();
  const event = name => ({ addListener(fn) { events[name] = fn; } });
  const chrome = {
    storage: {
      onChanged: event('storage'),
      local: {
        async get(key) { return Object.fromEntries((Array.isArray(key) ? key : [key]).map(k => [k, clone(data[k])])); },
        async set(values) {
          const changes = {};
          for (const [key, value] of Object.entries(values)) {
            changes[key] = { oldValue: clone(data[key]), newValue: clone(value) };
            data[key] = clone(value);
          }
          events.storage?.(changes, 'local');
        },
        async remove(key) { delete data[key]; }
      }
    },
    runtime: {
      onInstalled: event('installed'), onStartup: event('startup'), onMessage: event('message'),
      sendMessage(msg) { return new Promise(resolve => events.message(msg, {}, resolve)); }
    },
    alarms: {
      onAlarm: event('alarm'),
      async clear(name) { const index = alarmTimes.findIndex(a => a.name === name); if (index >= 0) alarmTimes.splice(index, 1); },
      async getAll() { return clone(alarmTimes); },
      async create(name, info) { await this.clear(name); alarmTimes.push({ name, ...clone(info) }); }
    },
    tabs: { create() {} }
  };
  const api = {
    getCookies: async () => ({ SESSDATA: 'test' }),
    getWatchHistory: async () => [],
    getUserVideos: async () => ({ videos: [
      { bvid: 'BVnew', aid: 1, pubdate: 100000, title: 'new' },
      { bvid: 'BVold', aid: 2, pubdate: 13600, title: 'old' }
    ] }),
    getUpInfo: async () => ({ fans: 1234 }),
    addToWatchLaterBatch: async aids => aids.map(aid => ({ aid, code: 0 })),
    ...overrides
  };
  const context = vm.createContext({
    console, chrome,
    setTimeout(fn, ms) { if (ms < 1000) queueMicrotask(fn); return 1; },
    clearTimeout() {}, setInterval() { return 1; }, clearInterval() {},
    document: {
      addEventListener(name, fn) { events[name] = fn; },
      getElementById(id) {
        if (!nodes.has(id)) nodes.set(id, {
          value: id === 'select-sort' ? 'default' : '', style: {}, innerHTML: '',
          listeners: {}, addEventListener(name, fn) { this.listeners[name] = fn; },
          querySelectorAll() { return []; }
        });
        return nodes.get(id);
      },
      createElement() {
        return { textContent: '', get innerHTML() { return this.textContent; } };
      }
    }
  });
  const modules = new Map();
  function getModule(filename) {
    if (!modules.has(filename)) {
      const module = filename === path.join(root, 'core', 'api.js')
        ? new vm.SyntheticModule(Object.keys(api), function () {
          for (const [name, fn] of Object.entries(api)) this.setExport(name, async (...args) => {
            calls.push({ name, args });
            return fn(...args);
          });
        }, { context, identifier: filename })
        : new vm.SourceTextModule(fs.readFileSync(filename, 'utf8'), { context, identifier: filename });
      modules.set(filename, module);
    }
    return modules.get(filename);
  }
  async function load(relative) {
    const module = getModule(path.join(root, relative));
    if (module.status === 'unlinked') await module.link((specifier, from) =>
      getModule(path.resolve(path.dirname(from.identifier), specifier)));
    if (module.status === 'linked') await module.evaluate();
    return module.namespace;
  }
  return { data, calls, events, alarmTimes, chrome, nodes, load, context };
}

test('imported creators gain both persisted sort fields during a normal check', async () => {
  const h = harness();
  const storage = await h.load('core/storage.js');
  await storage.importTrackingList([{ mid: '1', name: 'Imported' }]);
  const checker = await h.load('core/checker.js');
  await checker.checkForNewVideos();
  assert.equal(h.data.trackingList['1'].fans, 1234);
  assert.equal(h.data.trackingList['1'].lastPubdate, 100000);
  assert.ok(h.data.trackingList['1'].metaUpdatedAt > 0);
  assert.equal(h.calls.filter(c => c.name === 'getUserVideos').length, 1);
  assert.equal(h.calls.filter(c => c.name === 'getUpInfo').length, 1);
  const restarted = harness(h.data);
  const saved = await (await restarted.load('core/storage.js')).getTrackingList();
  assert.deepEqual(clone(saved), h.data.trackingList);
  assert.equal(restarted.calls.length, 0);
});

test('fan failure preserves old fans and still updates publication time and adds new video', async () => {
  const now = Math.floor(Date.now() / 1000);
  const h = harness({ trackingList: { 1: { name: 'A', fans: 88 } } }, {
    getUpInfo: async () => { throw Error('offline'); },
    getUserVideos: async () => ({ videos: [
      { bvid: 'BVnew', aid: 1, pubdate: now, title: 'new' },
      { bvid: 'BVold', aid: 2, pubdate: now - 172800, title: 'old' }
    ] })
  });
  const report = await (await h.load('core/checker.js')).checkForNewVideos();
  assert.equal(report.added, 1);
  assert.equal(h.data.trackingList['1'].fans, 88);
  assert.equal(h.data.trackingList['1'].lastPubdate, now);
  assert.equal(h.data.trackingList['1'].publications.length, 2);
  assert.ok(h.data.trackedVideos.BVnew);
});

test('video failure preserves old publication time while independently updating fans', async () => {
  const h = harness({ trackingList: { 1: { name: 'A', lastPubdate: 123 } } }, {
    getUserVideos: async () => ({ error: 'unavailable' }),
    getUpInfo: async () => ({ fans: 0 })
  });
  await (await h.load('core/checker.js')).checkForNewVideos();
  assert.equal(h.data.trackingList['1'].lastPubdate, 123);
  assert.equal(h.data.trackingList['1'].fans, 0);
});

test('complete API failure does not erase metadata or advance its timestamp', async () => {
  const original = { name: 'A', fans: 77, avgIntervalDays: 4, metaUpdatedAt: 123 };
  const h = harness({ trackingList: { 1: original } }, {
    getUserVideos: async () => { throw Error('offline'); },
    getUpInfo: async () => { throw Error('offline'); }
  });
  await (await h.load('core/checker.js')).checkForNewVideos();
  assert.deepEqual(h.data.trackingList['1'], original);
});

test('concurrent metadata writes, import, add and removal preserve each other', async () => {
  const h = harness({ trackingList: { 1: { name: 'A' }, 2: { name: 'B' } } });
  const storage = await h.load('core/storage.js');
  await Promise.all([
    storage.updateCreatorMetaBatch([{ mid: '1', fans: 100 }, { mid: '2', fans: 200 }]),
    storage.removeFromTrackingList('2'),
    storage.importTrackingList([{ mid: '3', name: 'C' }]),
    storage.addToTrackingList('4', { name: 'D' })
  ]);
  assert.deepEqual(Object.keys(h.data.trackingList), ['1', '3', '4']);
  assert.equal(h.data.trackingList['1'].fans, 100);
  await storage.importTrackingList([{ mid: '1', name: 'Overwrite' }]);
  assert.equal(h.data.trackingList['1'].fans, 100);
});

test('publication sorting handles both directions, missing values and saved choices without API requests', async () => {
  const h = harness();
  const { sortTrackingMids } = await h.load('core/activity.js');
  const storage = await h.load('core/storage.js');
  const list = {1:{lastPubdate:100},2:{lastPubdate:500},3:{},4:{lastPubdate:0}};
  assert.deepEqual(clone(sortTrackingMids(Object.keys(list), list, 'updated-desc')), ['2','1','3','4']);
  assert.deepEqual(clone(sortTrackingMids(Object.keys(list), list, 'updated-asc')), ['1','2','3','4']);
  await storage.saveSettings({sortMode:'updated-desc'});
  assert.equal((await storage.getSettings()).sortMode, 'updated-desc');
  assert.equal(h.calls.length, 0);
});

const config = (id, props = {}) => ({id,name:id,enabled:true,schedule:{0:'18:00',6:'18:00'},scope:'all',mids:[],newVideoWindowHours:24,...props});

test('legacy schedule migrates once, independent edits survive concurrent writes and deleting all persists', async () => {
  const h = harness({settings:{checkDays:[1,3],checkTime:'09:45',sortMode:'freq-desc',newVideoWindowHours:48}});
  const storage = await h.load('core/storage.js');
  let settings = await storage.getSettings();
  assert.deepEqual(clone(settings.checkers[0].schedule), {1:'09:45',3:'09:45'});
  assert.equal(settings.checkers[0].newVideoWindowHours,48);
  assert.equal(settings.sortMode,'updated-desc');
  await Promise.all([storage.saveChecker(config('a')), storage.saveChecker(config('b',{newVideoWindowHours:72})), storage.saveSettings({sortMode:'fans-desc'})]);
  settings = await storage.getSettings();
  assert.equal(settings.checkers.length,3);
  assert.equal(settings.sortMode,'fans-desc');
  assert.equal(settings.checkers.find(c=>c.id==='b').newVideoWindowHours,72);
  await assert.rejects(storage.saveChecker(config('bad',{schedule:{1:'24:00'}})));
  await assert.rejects(storage.saveChecker(config('bad',{scope:'selected',mids:[]})));
  for (const c of settings.checkers) await storage.deleteChecker(c.id);
  const restarted = harness(h.data);
  assert.equal((await (await restarted.load('core/storage.js')).getSettings()).checkers.length,0);
});

test('automatic alarm caches publication time and schedules exactly next selected weekday', async () => {
  const h = harness({trackingList:{1:{name:'A'}},settings:{schedule:{0:'18:00',6:'18:00'}}});
  let now = new Date(2026,8,12,17,0).getTime();
  h.context.Date = class extends Date { constructor(...args){super(...(args.length?args:[now]));} static now(){return now;} };
  await h.load('service-worker.js'); await h.events.startup();
  const alarm = () => h.alarmTimes.find(a=>a.name==='check-watch-later:default');
  assert.equal(new Date(alarm().when).getDay(),6);
  now = new Date(2026,8,12,18,0).getTime();
  await h.events.alarm({name:'check-watch-later:default'});
  assert.equal(h.data.trackingList['1'].lastPubdate,100000);
  assert.equal(h.data._checkProgress.type,'complete');
  assert.equal(new Date(alarm().when).getDay(),0);
  assert.equal(alarm().periodInMinutes,undefined);
  now = new Date(2026,8,13,18,1).getTime();
  const storage = await h.load('core/storage.js');
  await storage.saveChecker(config('default',{schedule:{0:'18:00'}}));
  await h.events.startup();
  assert.equal(alarm().when,new Date(2026,8,20,18,0).getTime());
  await storage.saveChecker(config('default',{enabled:false}));
  await h.events.startup(); assert.equal(h.alarmTimes.length,0);
});

test('selected creators and per-checker video window are honored', async () => {
  const now = Math.floor(Date.now()/1000);
  const h = harness({trackingList:{1:{name:'A'},2:{name:'B'}},settings:{newVideoWindowHours:12}}, {
    getUserVideos: async () => ({videos:[{bvid:'BV48',aid:1,title:'48h',pubdate:now-30*3600}]})
  });
  const checker = await h.load('core/checker.js');
  const report = await checker.checkForNewVideos(null, config('selected',{scope:'selected',mids:['2'],newVideoWindowHours:48}));
  assert.equal(report.added,1);
  assert.deepEqual(h.calls.filter(c=>c.name==='getUserVideos').map(c=>c.args[0]),['2']);
  assert.equal(h.data.trackingList['1'].lastPubdate,undefined);
  assert.ok(h.data.trackingList['2'].lastPubdate);
  const second = harness({trackingList:{1:{name:'A'}}}, {getUserVideos:async()=>({videos:[{bvid:'old',aid:2,pubdate:now-30*3600}]})});
  assert.equal((await (await second.load('core/checker.js')).checkForNewVideos(null,config('short',{newVideoWindowHours:24}))).added,0);
});

test('simultaneous alarms queue independently, overlapping creators do not add a video twice', async () => {
  let release, started;
  const gate = new Promise(r=>release=r), ready = new Promise(r=>started=r);
  let count=0;
  const h = harness({trackingList:{1:{name:'A'}},settings:{checkers:[config('a'),config('b')]}}, {
    getUserVideos: async () => {if (++count===1){started();await gate;} return {videos:[{bvid:'same',aid:1,pubdate:Math.floor(Date.now()/1000)}]};}
  });
  await h.load('service-worker.js'); await h.events.startup();
  assert.equal(h.alarmTimes.filter(a=>a.name.startsWith('check-watch-later:')).length,2);
  const first = h.events.alarm({name:'check-watch-later:a'}); await ready;
  await h.events.alarm({name:'check-watch-later:b'});
  assert.deepEqual(h.data._pendingCheckers,['a','b']);
  release(); await first;
  assert.equal(count,2);
  assert.equal(h.calls.filter(c=>c.name==='addToWatchLaterBatch').length,1);
  assert.deepEqual(h.data._pendingCheckers,[]);
});

test('pending checks resume after worker restart, paused and deleted checks are skipped', async () => {
  const h=harness({trackingList:{1:{name:'A'}},settings:{checkers:[config('a'),config('paused',{enabled:false})]},_pendingCheckers:['deleted','paused','a']});
  await h.load('service-worker.js'); await h.events.startup();
  assert.equal(h.calls.filter(c=>c.name==='getUserVideos').length,1);
  assert.deepEqual(h.data._pendingCheckers,[]);
});

test('publication history merges idempotently, expires old records and retains valid cache on stale responses', async () => {
  const now=Date.now(), seconds=Math.floor(now/1000);
  const h=harness({trackingList:{1:{name:'A',lastPubdate:seconds,publications:[{bvid:'new',pubdate:seconds}]}}});
  const {mergePublications}=await h.load('core/activity.js');
  const entries=mergePublications([{bvid:'old',pubdate:seconds-90*86400}], [{bvid:'a',pubdate:seconds},{bvid:'a',pubdate:seconds},{bvid:'future',pubdate:seconds+1000},{pubdate:NaN}], now);
  assert.equal(entries.length,1);
  await (await h.load('core/storage.js')).updateCreatorMetaBatch([{mid:'1',lastPubdate:seconds-100,publications:[{bvid:'older',pubdate:seconds-100}]}]);
  assert.equal(h.data.trackingList['1'].lastPubdate,seconds);
  assert.equal(h.data.trackingList['1'].publications.length,2);
});

test('weekly, hourly and minute counts deduplicate creators and respect local week boundaries', async () => {
  const h=harness(); const {summarizeWeek,weekStart}=await h.load('core/activity.js');
  const monday=new Date(2026,7,31); const stamp=(day,hour,minute=0)=>new Date(2026,7,31+day,hour,minute).getTime()/1000;
  assert.equal(weekStart(new Date(2026,8,6,12)).getTime(),monday.getTime());
  const list={1:{lastPubdate:stamp(0,9,30),publications:[{pubdate:stamp(0,9)},{pubdate:stamp(0,9,30)},{pubdate:stamp(0,9,30)+59},{pubdate:stamp(0,10)},{pubdate:stamp(6,23,59)},{pubdate:stamp(7,0)}]},2:{lastPubdate:stamp(0,9)},3:{publications:[{pubdate:stamp(-1,23)}]}};
  const result=summarizeWeek(list,monday);
  assert.equal(result.mids.size,2); assert.equal(result.days[0].mids.size,2);
  assert.equal(result.days[0].hours[9].size,2); assert.equal(result.days[0].hours[10].size,1);
  assert.equal(result.days[0].minutes.get(540).size,2);
  assert.equal(result.days[0].minutes.get(570).size,1);
  assert.equal(result.days[0].minutes.has(571),false);
  assert.equal(result.days[6].minutes.get(1439).size,1);
  assert.equal(result.days[0].minutes.has(0),false);
  assert.equal(result.days[6].mids.size,1);
  assert.equal(result.days[1].mids.size,0);
});


test('history defaults to three natural weeks and honors the exact oldest Monday boundary', async () => {
  const h=harness();
  const {mergePublications}=await h.load('core/activity.js');
  const now=new Date(2026,8,13,12).getTime();
  const boundary=new Date(2026,7,24).getTime()/1000;
  const records=[{bvid:'edge',pubdate:boundary},{bvid:'expired',pubdate:boundary-1}];
  assert.deepEqual(clone(mergePublications(records,[],now)).map(v=>v.bvid),['edge']);
  assert.equal(mergePublications(records,[],now,4).length,2);
  assert.equal(mergePublications(records,[],now,1).length,0);
  assert.equal((await (await h.load('core/storage.js')).getSettings()).historyWeeks,3);
});

test('custom history retention persists, prunes on save and is respected by later metadata writes', async () => {
  const h=harness();
  const storage=await h.load('core/storage.js');
  const {weekStart}=await h.load('core/activity.js');
  const monday=weekStart().getTime()/1000;
  const old={bvid:'old',pubdate:monday-28*86400};
  const recent={bvid:'recent',pubdate:monday};
  await storage.importTrackingList([{mid:'1',name:'A'}]);
  await storage.saveSettings({historyWeeks:6});
  await storage.updateCreatorMetaBatch([{mid:'1',lastPubdate:recent.pubdate,publications:[old,recent]}]);
  assert.equal(h.data.trackingList['1'].publications.length,2);
  await Promise.all([storage.saveSettings({historyWeeks:1}),storage.updateCreatorMetaBatch([{mid:'1',publications:[old]}])]);
  assert.deepEqual(h.data.trackingList['1'].publications.map(v=>v.bvid),['recent']);
  assert.equal(h.data.trackingList['1'].lastPubdate,recent.pubdate);
  const restarted=harness(h.data);
  assert.equal((await (await restarted.load('core/storage.js')).getSettings()).historyWeeks,1);
  for (const invalid of [0,53,1.5,'4',null]) await assert.rejects(storage.saveSettings({historyWeeks:invalid}));
  assert.equal((await storage.getSettings()).historyWeeks,1);
  await storage.saveSettings({historyWeeks:52});
  assert.equal((await storage.getSettings()).historyWeeks,52);
});


test('configuration backup restores settings, scoped checkers and metadata without runtime or login data', async () => {
  const now=Math.floor(Date.now()/1000);
  const h=harness({settings:{checkers:[config('restored',{scope:'selected',mids:['1']})],historyWeeks:5,sortMode:'updated-desc',newVideoWindowHours:48,secret:'never-export'},trackingList:{1:{name:'A',face:'https://example.com/avatar.png',lastPubdate:now,publications:[{bvid:'BV1',pubdate:now}]}},trackedVideos:{keep:{title:'local'}},_pendingCheckers:['old'],cookies:{SESSDATA:'never-export'}});
  await h.load('service-worker.js');
  const response=await h.chrome.runtime.sendMessage({type:'exportConfiguration'});
  assert.equal(response.success,true);
  const backup=JSON.parse(JSON.stringify(response.backup));
  assert.ok(!JSON.stringify(backup).includes('never-export'));
  await (await h.load('core/storage.js')).saveChecker(config('extra'));
  const restored=await h.chrome.runtime.sendMessage({type:'importConfiguration',backup});
  assert.equal(restored.success,true);
  assert.equal(h.data.settings.checkers.length,1);
  assert.equal(h.data.settings.historyWeeks,5);
  assert.deepEqual(h.data.settings.checkers[0].mids,['1']);
  assert.equal(h.data.trackingList['1'].publications.length,1);
  assert.equal(h.data.trackedVideos.keep.title,'local');
  assert.deepEqual(h.data._pendingCheckers,[]);
  assert.ok(h.alarmTimes.find(a=>a.name==='check-watch-later:restored'));
  const snapshot=clone(h.data);
  await assert.rejects((await h.load('core/storage.js')).restoreConfiguration({...backup,version:999}));
  await assert.rejects((await h.load('core/storage.js')).restoreConfiguration({...backup,settings:{...backup.settings,checkers:[backup.settings.checkers[0],backup.settings.checkers[0]]}}));
  await assert.rejects((await h.load('core/storage.js')).restoreConfiguration({...backup,creators:[{mid:'__proto__'}]}));
  assert.deepEqual(h.data,snapshot);
});

test('configuration import is rejected while an existing mutation is awaiting its API response', async () => {
  let started,release; const ready=new Promise(r=>started=r), gate=new Promise(r=>release=r);
  const h=harness({}, {getUpInfo:async()=>{started();await gate;return {name:'A',fans:1};}});
  await h.load('service-worker.js');
  const adding=h.chrome.runtime.sendMessage({type:'addCreator',mid:'1'}); await ready;
  const result=await h.chrome.runtime.sendMessage({type:'importConfiguration',backup:{}});
  assert.equal(result.success,false); assert.match(result.reason,/等待/);
  release(); assert.equal((await adding).success,true);
});
