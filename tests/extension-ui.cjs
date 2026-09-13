// Requires Playwright via NODE_PATH or a local installation. Optional CHROMIUM_EXECUTABLE.
// Loads the actual extension in an isolated Chromium profile, without Bilibili login.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const path = require('node:path');

(async () => {
  const root = path.resolve(__dirname, '..');
  const context = await chromium.launchPersistentContext('', {
    headless: true, executablePath: process.env.CHROMIUM_EXECUTABLE || undefined,
    args: [`--disable-extensions-except=${root}`, `--load-extension=${root}`],
    viewport: { width: 1280, height: 1000 }
  });
  try {
    const worker = context.serviceWorkers()[0] || await context.waitForEvent('serviceworker');
    const id = new URL(worker.url()).host;
    await worker.evaluate(() => chrome.storage.local.set({ trackingList: {100:{name:'测试 UP 主'}} }));
    const page = await context.newPage(), errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.goto(`chrome-extension://${id}/manage/manage.html`);
    await page.locator('#btn-add-checker').click();
    await page.locator('#checker-dialog').waitFor({state:'visible'});
    await page.locator('[name=checkerName]').fill('');
    await page.locator('#checker-save').click();
    await page.locator('#checker-error').filter({hasText:'名称'}).waitFor();
    await page.locator('[name=checkerName]').fill('真实后台检查器');
    await page.locator('[data-time="1"]').fill('');
    await page.locator('#checker-save').click();
    await page.locator('#checker-error').filter({hasText:'检查时间'}).waitFor();
    await page.locator('[data-time="1"]').fill('09:30');
    // An invalid value in an unchecked day used to block native form submission.
    await page.locator('[data-time="2"]').evaluate(input => { input.value='18:00:01'; });
    assert.equal(await page.locator('[data-time="2"]').isDisabled(), true);
    await page.locator('[name=scope]').selectOption('selected');
    await page.locator('#checker-people input[value="100"]').check();
    await page.locator('#checker-save').click();
    await page.locator('#checker-dialog').waitFor({state:'hidden'});
    const {settings} = await worker.evaluate(() => chrome.storage.local.get('settings'));
    const saved = settings.checkers.find(c => c.name === '真实后台检查器');
    assert.ok(saved); assert.deepEqual(saved.mids, ['100']); assert.equal(saved.schedule[1], '09:30');
    const alarms = await worker.evaluate(() => chrome.alarms.getAll());
    assert.ok(alarms.find(a => a.name === 'check-watch-later:' + saved.id));
    await page.reload();
    await page.locator('.checker-card').filter({hasText:'真实后台检查器'}).waitFor();
    const exported=await page.evaluate(()=>chrome.runtime.sendMessage({type:'exportConfiguration'}));
    assert.equal(exported.success,true);
    const backup=exported.backup;
    await page.evaluate(()=>chrome.runtime.sendMessage({type:'saveSettings',settings:{historyWeeks:1}}));
    await page.evaluate(()=>chrome.runtime.sendMessage({type:'removeCreator',mid:'100'}));
    const changed=await worker.evaluate(()=>chrome.storage.local.get(['settings','trackingList']));
    assert.equal(changed.settings.historyWeeks,1); assert.equal(Object.keys(changed.trackingList).length,0);
    await page.locator('#config-file').setInputFiles({name:'bad.json',mimeType:'application/json',buffer:Buffer.from('{broken')});
    await page.locator('#config-status').filter({hasText:'导入失败'}).waitFor();
    const file={name:'configuration.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(backup))};
    await page.locator('#config-file').setInputFiles(file);
    await page.locator('#config-dialog').waitFor({state:'visible'});
    assert.match(await page.locator('#config-preview').textContent(),/1 位 UP 主/);
    await page.locator('#config-cancel').click();
    assert.equal((await worker.evaluate(()=>chrome.storage.local.get('settings'))).settings.historyWeeks,1);
    await page.locator('#config-file').setInputFiles(file);
    await page.locator('#config-apply').click();
    await page.locator('#config-status').filter({hasText:'配置已恢复'}).waitFor();
    const recovered=await worker.evaluate(()=>chrome.storage.local.get(['settings','trackingList']));
    assert.equal(recovered.settings.historyWeeks,backup.settings.historyWeeks);
    assert.equal(recovered.trackingList['100'].name,'测试 UP 主');
    assert.ok((await worker.evaluate(()=>chrome.alarms.getAll())).find(a=>a.name==='check-watch-later:'+saved.id));
    assert.deepEqual(errors, []);
    console.log(JSON.stringify({passed:true,checks:['visible name/time validation','unchecked time ignored','actual service worker save','actual Chrome alarm','selected creator persistence','reload persistence','backup schema export','invalid JSON rejection','import preview and cancel','file import roundtrip','alarms rebuilt'],errors}));
  } finally { await context.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
