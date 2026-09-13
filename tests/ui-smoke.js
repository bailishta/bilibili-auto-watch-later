// Run with: playwright-cli -s=watch-later-ui run-code --filename=tests/ui-smoke.js
// Start a fresh browser session and a local static server on 127.0.0.1:8765.
// Uses isolated sample data; never calls Bilibili or changes a real account.
async page => {
  const errors = [];
  const check = (condition, message) => { if (!condition) throw Error(message); };
  page.context().on('page', p => {
    p.on('pageerror', e => errors.push(e.message));
    p.on('dialog', d => d.accept());
  });
  page.on('pageerror', e => errors.push(e.message));
  page.on('dialog', d => d.accept());
  await page.context().addInitScript(() => {
    const monday = new Date(); monday.setHours(0,0,0,0); monday.setDate(monday.getDate() - (monday.getDay()+6)%7);
    const stamp = (day, hour, minute=0) => { const d = new Date(monday); d.setDate(d.getDate()+day); d.setHours(hour,minute); return d.getTime()/1000; };
    const seed = {
      settings: { checkers: [{id:'default',name:'默认检查器',enabled:true,schedule:{0:'18:00',6:'18:00'},scope:'all',mids:[],newVideoWindowHours:24}], sortMode: 'default', newVideoWindowHours: 24 },
      trackingList: Object.fromEntries(Array.from({length: 12}, (_, i) => [String(100 + i), {
        name: ['科技观察室', '周末厨房', '城市漫游', '一席读书'][i % 4] + (i < 4 ? '' : ` ${i + 1}`),
        fans: (i + 1) * 32100, lastPubdate: stamp(-7 + i%7, 9),
        publications: [{bvid:'v'+i,pubdate:stamp(-7+i%7,9)},{bvid:'duplicate'+i,pubdate:stamp(-7+i%7,9)},...(i===0?[
          {bvid:'evening',pubdate:stamp(-7,21,17)},
          {bvid:'minute',pubdate:stamp(-7,9,17)},{bvid:'same-minute',pubdate:stamp(-7,9,17)+59},
          {bvid:'next-minute',pubdate:stamp(-7,9,18)},
          {bvid:'midnight',pubdate:stamp(-7,23,59)},{bvid:'noon',pubdate:stamp(-7,12)},
          {bvid:'before-noon',pubdate:stamp(-7,11,59)}
        ]:[])],
        face: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" rx="20" fill="#fff0f5"/><text x="20" y="26" text-anchor="middle" font-size="18" fill="#fb7299">U</text></svg>')
      }])),
      stats: {lastNewCount:16, lastCheck:Date.now(), totalAdded:35}
    };
    if (!localStorage.biliUiTest) localStorage.biliUiTest = JSON.stringify(seed);
    const read = () => JSON.parse(localStorage.biliUiTest);
    const listeners = [];
    const emit = changes => listeners.forEach(fn => fn(changes, 'local'));
    const save = (key, value) => {
      const data = read(); const oldValue = data[key]; data[key] = value;
      localStorage.biliUiTest = JSON.stringify(data); emit({[key]:{oldValue,newValue:value}});
    };
    window.addEventListener('storage', e => {
      if (e.key !== 'biliUiTest') return;
      const old = JSON.parse(e.oldValue || '{}'), next = JSON.parse(e.newValue || '{}');
      const changes = {};
      for (const key of new Set([...Object.keys(old), ...Object.keys(next)])) {
        if (JSON.stringify(old[key]) !== JSON.stringify(next[key])) changes[key] = {oldValue:old[key],newValue:next[key]};
      }
      emit(changes);
    });
    window.__messages = [];
    window.__exports = [];
    window.chrome = {
      runtime: {
        openOptionsPage: async () => { window.open('/manage/manage.html'); },
        sendMessage: async msg => {
          window.__messages.push(msg.type);
          const data = read();
          switch(msg.type) {
            case 'getStatus': return { authenticated:true, trackingCount:Object.keys(data.trackingList).length, ...data.stats };
            case 'getSettings': return data.settings;
            case 'saveChecker': {
              if (window.__failCheckerSave) return {error:'测试：后台保存失败'};
              const next=data.settings.checkers.filter(c=>c.id!==msg.checker.id); next.push(msg.checker);
              save('settings',{...data.settings,checkers:next}); return {success:true};
            }
            case 'deleteChecker': save('settings',{...data.settings,checkers:data.settings.checkers.filter(c=>c.id!==msg.id)}); return {success:true};
            case 'saveSettings': save('settings', {...data.settings,...msg.settings}); return {success:true};
            case 'getTrackingList': case 'exportTrackingList': return data.trackingList;
            case 'getCheckProgress': return data._checkProgress || null;
            case 'getMetaProgress': return data._metaProgress || null;
            case 'getImportProgress': return data._importProgress || null;
            case 'addCreator': save('trackingList', {...data.trackingList,[msg.mid]:{name:'新增测试UP主',face:''}}); return {success:true};
            case 'removeCreator': delete data.trackingList[msg.mid]; save('trackingList',data.trackingList); return {success:true};
            case 'importTrackingList': {
              let added = 0; for (const c of msg.creators) if (!data.trackingList[c.mid]) {data.trackingList[c.mid]=c;added++;}
              save('trackingList',data.trackingList); return {success:true,added,skipped:msg.creators.length-added};
            }
            case 'triggerCheck':
              save('_checkProgress',{type:'progress',current:1,total:12,name:'测试UP主'});
              await new Promise(r=>setTimeout(r,800));
              save('stats',{...data.stats,lastNewCount:0});
              save('_checkProgress',{type:'complete',report:{status:'ok',added:0}});
              return {status:'ok',added:0};
            case 'importFollowList': return {status:'ok',imported:0};
            case 'cancelCheck': save('_checkProgress',{type:'complete',report:{status:'cancelled'}}); return {success:true};
          }
          throw Error('Unexpected message: '+msg.type);
        }
      },
      storage:{onChanged:{addListener:fn=>listeners.push(fn)}},
      tabs:{create:async ({url})=>{window.__openedUrl=url;}},
      downloads:{download:async value=>{window.__exports.push(value);}}
    };
  });
  await page.setViewportSize({width:340,height:240});
  await page.goto('http://127.0.0.1:8765/popup/popup.html');
  await page.locator('#status-badge').filter({hasText:'已登录'}).waitFor();
  check(await page.locator('#select-window, #tracking-list, #schedule-list').count() === 0, 'Popup still contains advanced controls');
  check(await page.locator('#stat-new').innerText() === '16', 'Popup statistics missing');
  const [manager] = await Promise.all([page.context().waitForEvent('page'),page.locator('#btn-manage').click()]);
  await manager.waitForLoadState();
  await manager.setViewportSize({width:1280,height:1000});
  await manager.locator('.list-item').first().waitFor();
  check(manager.url().endsWith('/manage/manage.html'), 'Manager did not open in a new tab');
  check(await manager.locator('#btn-refresh-meta').count()===0, 'Refresh data button still present');
  await manager.locator('#select-sort').selectOption('fans-desc');
  check(await manager.locator('.delete-btn').first().getAttribute('data-mid') === '111', 'Cached sorting failed');
  await manager.locator('#search-tracking').fill('100');
  check(await manager.locator('.list-item').count() === 1, 'Search failed');
  await manager.locator('#search-tracking').fill('');
  await manager.locator('#select-window').selectOption('48');
  await manager.locator('#btn-add-checker').click();
  await manager.locator('[name="checkerName"]').fill('工作日检查');
  await manager.locator('[name="windowHours"]').selectOption('72');
  await manager.locator('[data-time="1"]').fill('09:30');
  await manager.locator('[name="scope"]').selectOption('selected');
  await manager.locator('#checker-save').click();
  check((await manager.locator('#checker-error').innerText()).includes('至少选择'), 'Empty selection accepted');
  await manager.locator('#checker-search').fill('100');
  await manager.locator('#checker-people input[value="100"]').check();
  await manager.evaluate(()=>window.__failCheckerSave=true);
  await manager.locator('#checker-save').click();
  await manager.locator('#checker-error').filter({hasText:'后台保存失败'}).waitFor();
  check(await manager.locator('#checker-save').isEnabled(), 'Save button stuck after an error');
  await manager.evaluate(()=>window.__failCheckerSave=false);
  await manager.locator('#checker-save').click();
  await manager.locator('#checker-dialog').waitFor({state:'hidden'});
  check(await manager.locator('.checker-card').count() === 2, 'Second checker missing');
  await manager.reload();
  await manager.locator('.checker-card').nth(1).waitFor();
  check(await manager.locator('#select-window').inputValue() === '48', 'Manual window setting was not saved');
  const custom=manager.locator('.checker-card').filter({hasText:'工作日检查'});
  await custom.locator('[data-action="edit"]').click();
  check(await manager.locator('[data-time="1"]').inputValue() === '09:30', 'Independent schedule not saved');
  check(await manager.locator('[name="windowHours"]').inputValue() === '72', 'Independent window not saved');
  check(await manager.locator('#checker-people input[value="100"]').isChecked(), 'Scope selection not saved');
  await manager.locator('#checker-cancel').click();
  await custom.locator('[data-action="toggle"]').click();
  await custom.getByText('已暂停',{exact:true}).waitFor();
  check(await manager.locator('.checker-card').filter({hasText:'默认检查器'}).getByText('已启用',{exact:true}).count()===1, 'Pausing one checker affected another');
  const before=await manager.evaluate(()=>window.__messages.length);
  await manager.locator('#activity-prev').click();
  check(await manager.locator('.week-chart .activity-bar').count()===7, 'Week chart missing');
  check(await manager.locator('.week-chart .bar-count').first().innerText()==='2', 'Daily creator deduplication failed');
  await manager.locator('.week-chart .activity-bar').first().click();
  check(await manager.locator('[data-period="0"]').getAttribute('aria-pressed')==='true', 'Clock does not default to morning');
  check(await manager.locator('.clock-minute-tick').count()===60, 'Normal clock minute ticks missing');
  check(await manager.locator('.clock-hand, .clock-hour, .clock-minutes').count()===0, 'Hands or two-stage controls remain');
  check((await manager.locator('.clock-tick').allTextContents()).join(',')==='12,1,2,3,4,5,6,7,8,9,10,11','Dial numbers are not a real clock');
  check(await manager.locator('.clock-count').textContent()==='2', 'Half-day creator deduplication failed');
  check(await manager.locator('.clock-record').count()===4, 'Exact times require an extra click');
  const exactRecord = manager.locator('.clock-record[data-minute="557"]');
  check(await exactRecord.isVisible(), '9:17 is not shown immediately');
  check(await exactRecord.locator('.clock-record-time span').innerText()==='9:17', 'Exact minute missing');
  check(await exactRecord.locator('.clock-person').count()===1 && await exactRecord.locator('.clock-avatar img').count()===1, 'Minute creators are duplicated or hidden');
  check(await manager.locator('.clock-record[data-minute="540"] .clock-person').count()===2, 'Same-minute creator count incorrect');
  check(await manager.locator('.clock-record[data-minute="558"] .clock-record-time span').innerText()==='9:18', 'Adjacent update minute missing');
  const minutePosition = await manager.locator('.clock-event[data-minute="557"] .clock-event-dot').evaluate(el=>({x:Number(el.getAttribute('cx')),y:Number(el.getAttribute('cy'))}));
  check(Math.abs(minutePosition.x-(240+220*Math.cos((557/2-90)*Math.PI/180)))<.01, 'Marker position loses minute precision');
  await manager.locator('.clock-event[data-minute="557"]').focus();
  await manager.keyboard.press('ArrowRight');
  check(await manager.locator('.clock-event[data-minute="558"]').getAttribute('aria-pressed')==='true', 'Clock keyboard navigation failed');
  check(await manager.locator('.clock-record').count()===4, 'Highlight hid other update times');
  await manager.locator('[data-period="1"]').click();
  check(await manager.locator('.clock-count').textContent()==='1','Afternoon data was mixed with morning');
  check(await manager.locator('.clock-record').count()===3, 'Afternoon records not directly visible');
  check(await manager.locator('.clock-record[data-minute="1277"] .clock-record-time span').innerText()==='9:17', 'Afternoon minute incorrect');
  check(await manager.locator('.clock-record[data-minute="1439"] .clock-record-time span').innerText()==='11:59', 'Last minute of day incorrect');
  check(await manager.locator('.clock-record[data-minute="720"] .clock-record-time span').innerText()==='12:00', 'Noon minute incorrect');
  await manager.locator('[data-period="0"]').click();
  check(await manager.locator('.clock-record[data-minute="719"] .clock-record-time span').innerText()==='11:59', 'Minute before noon incorrect');
  await manager.locator('#activity-back').click();
  await manager.locator('#activity-today').click();
  check(await manager.locator('#activity-empty').isVisible(), 'Empty week state missing');
  check(await manager.evaluate(()=>window.__messages.length)===before, 'Chart made backend requests');
  await manager.locator('#select-sort').selectOption('updated-desc');
  check(await manager.locator('.delete-btn').first().getAttribute('data-mid')==='106', 'Latest publication sort failed');
  await manager.locator('#select-sort').selectOption('updated-asc');
  check(await manager.locator('.delete-btn').first().getAttribute('data-mid')==='100', 'Oldest publication sort failed');
  await manager.locator('#btn-add').click();
  await manager.locator('#add-input').fill('999');
  await manager.locator('#btn-confirm-add').click();
  await manager.locator('.delete-btn[data-mid="999"]').waitFor();
  await manager.locator('.delete-btn[data-mid="999"]').click();
  await manager.locator('.delete-btn[data-mid="999"]').waitFor({state:'detached'});
  await manager.locator('#btn-export').click();
  check(await manager.evaluate(()=>window.__exports.length) === 1, 'Export action failed');
  await manager.locator('#import-file-input').evaluate(input => {
    const transfer = new DataTransfer();
    transfer.items.add(new File([JSON.stringify({creators:[{mid:'998',name:'导入测试'}]})], 'test.json', {type:'application/json'}));
    input.files = transfer.files;
    input.dispatchEvent(new Event('change', {bubbles:true}));
  });
  await manager.locator('.delete-btn[data-mid="998"]').waitFor();
  await page.locator('#btn-check').click();
  await manager.locator('#check-progress').waitFor({state:'visible'});
  await page.getByRole('button',{name:'立即检查',exact:true}).waitFor();
  check(await page.locator('#stat-new').innerText() === '0', 'Zero additions rendered incorrectly');
  await page.locator('#btn-reimport').click();
  await page.locator('#feedback').filter({hasText:'导入完成'}).waitFor();
  await page.locator('#btn-open-wl').click();
  check(await page.evaluate(()=>window.__openedUrl) === 'https://www.bilibili.com/watchlater/', 'Watch later shortcut failed');
  await manager.setViewportSize({width:390,height:844});
  check(await manager.evaluate(()=>document.documentElement.scrollWidth<=innerWidth), 'Narrow layout overflows');
  await manager.locator('#activity-prev').click();
  await manager.locator('.week-chart .activity-bar').first().click();
  check(await manager.evaluate(()=>document.documentElement.scrollWidth<=innerWidth), 'Hourly chart overflows on mobile');
  await manager.locator('#btn-add-checker').click();
  check(await manager.locator('#checker-dialog').evaluate(el=>el.scrollWidth<=el.clientWidth), 'Checker dialog overflows');
  await manager.locator('#checker-cancel').click();
  await page.setViewportSize({width:340,height:360});
  check(await page.locator('.manage-arrow').count()===0, 'Management arrow returned');
  for (const theme of ['light','dark']) {
    await page.emulateMedia({colorScheme:theme}); await manager.emulateMedia({colorScheme:theme});
    await manager.setViewportSize({width:1280,height:1000});
    await manager.locator('#activity-back').click();
    await page.locator('.container').screenshot({path:'.playwright-cli/bili-v2-popup-'+theme+'.png',animations:'disabled'});
    await manager.screenshot({path:'.playwright-cli/bili-v2-manager-'+theme+'.png',fullPage:true,animations:'disabled'});
    await manager.locator('.week-chart .activity-bar').first().click();
  }
  await custom.locator('[data-action="delete"]').click();
  await custom.waitFor({state:'detached'});
  check(await manager.locator('.checker-card').count()===1, 'Deleting a checker failed');
  const side=await manager.locator('#checkers-panel').boundingBox(), main=await manager.locator('.activity-panel').boundingBox();
  check(side.x < main.x && side.width < main.width, 'Checkers are not in the sidebar');
  check(!await manager.locator('#activity-weeks').isVisible(), 'Statistics options are not collapsed');
  await manager.locator('#activity-toggle').click();
  await manager.locator('#activity-content').waitFor({state:'hidden'});
  await manager.locator('#activity-toggle').click();
  await manager.locator('#activity-content').waitFor({state:'visible'});
  await manager.evaluate(() => Promise.all(document.getAnimations().map(animation => animation.finished.catch(() => {}))));
  await manager.locator('.activity-options summary').click();
  await manager.locator('#activity-weeks').waitFor({state:'visible'});
  check(await manager.locator('#activity-weeks').inputValue()==='3', 'Default retention is not three weeks');
  await manager.locator('#activity-today').click();
  await manager.locator('#activity-prev').click(); await manager.locator('#activity-prev').click();
  check(await manager.locator('#activity-prev').isDisabled(), 'Three-week boundary not enforced');
  await manager.locator('#activity-weeks').fill('5'); await manager.locator('#activity-save-weeks').click();
  await manager.locator('#activity-settings-status').filter({hasText:'已保存：最近 5 周'}).waitFor();
  check(await manager.locator('#activity-prev').isEnabled(), 'Extending retention did not unlock weeks');
  await manager.locator('#activity-prev').click();
  await manager.locator('#activity-weeks').fill('1'); await manager.locator('#activity-save-weeks').click();
  await manager.locator('#activity-settings-status').filter({hasText:'已保存：最近 1 周'}).waitFor();
  check(await manager.locator('#activity-period').innerText().then(s=>s.includes('本周')), 'Shrinking retention did not clamp selected week');
  check(await manager.locator('#activity-prev').isDisabled(), 'One-week boundary not enforced');
  await manager.reload();
  await manager.locator('.activity-options summary').click();
  await manager.locator('#activity-weeks').filter({visible:true}).waitFor();
  await manager.waitForFunction(()=>document.getElementById('activity-weeks').value==='1');
  check(errors.length === 0, 'Browser errors: '+errors.join('; '));
  return {passed:true,checks:['compact popup','new manager tab','cached sort','search','settings persistence','independent checker persistence','checker scope and validation','checker pause/delete','weekly/hourly/minute deduplication','cached chart navigation','publication time sorting','light/dark themes','add/remove','export/import','cross-page progress','zero statistics','basic shortcuts','responsive layout','custom retention persistence and navigation bounds','visible save errors','sidebar placement','12-hour clock, exact minutes, AM/PM and keyboard','collapsed statistics','refresh button removed'],errors};
}
