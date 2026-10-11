// Opt-in real website checks on the connected phone; preserves account data and settings.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { writeFile } from 'node:fs/promises';
import { connectDevice } from './device-cdp.mjs';

const serial = process.argv[2] || process.env.BILISPEED_TEST_SERIAL;
if (!serial) throw new Error('Pass the connected phone serial');
const prefix = process.env.BILISPEED_EVIDENCE_PREFIX || '1.2.10-';
if (!/^[A-Za-z0-9.-]+$/.test(prefix)) throw new Error('Invalid evidence prefix');
const page = await connectDevice();
const evidence = [];
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const adb = args => execFileSync('adb', ['-s', serial, ...args], { timeout: 25000, maxBuffer: 16 * 1024 * 1024 });
async function waitFor(expression) {
    const deadline = Date.now() + 45000;
    while (Date.now() < deadline) {
        try { if (await page.evaluate('Boolean(document.documentElement && (' + expression + '))')) return; }
        catch (error) { if (!/context|navigation|Cannot find/.test(error.message)) throw error; }
        await delay(350);
    }
    throw new Error('Timeout: ' + expression);
}
async function nativeUi() {
    adb(['shell', 'uiautomator', 'dump', '/data/local/tmp/bilispeed-home-ui.xml']);
    const xml = adb(['shell', 'cat', '/data/local/tmp/bilispeed-home-ui.xml']).toString();
    return [...xml.matchAll(/<node\b[^>]*content-desc="B站(首页|热门|动态|我的|设置|搜索)"[^>]*>/g)]
        .filter(match => /class="android.widget.TextView"/.test(match[0])).map(match => {
        const bounds = /bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/.exec(match[0]);
        return { label: match[1], selected: /selected="true"/.test(match[0]),
            bounds: bounds.slice(1).map(Number) };
    });
}
async function screen(name) {
    await writeFile('artifacts/device-' + prefix + 'home-' + name + '-native.png', adb(['exec-out', 'screencap', '-p']));
}
async function tapTab(label) {
    const tab = (await nativeUi()).find(tab => tab.label === label);
    assert(tab, 'Missing navigation tab: ' + label);
    const [left, top, right, bottom] = tab.bounds;
    adb(['shell', 'input', 'tap', String(Math.round((left+right)/2)), String(Math.round((top+bottom)/2))]);
}
async function state() {
    return page.evaluate(`(()=>{
        const rect = element => { const r=element.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height}; };
        return {url:location.href,document:performance.timeOrigin,scroll:scrollY,width:innerWidth,height:innerHeight,
            scrollWidth:document.documentElement.scrollWidth,kind:document.documentElement.dataset.bilispeedPage,
            banner:[...document.querySelectorAll('.recommended-swipe')].map(rect),
            search:document.querySelector('.nav-search-input')?rect(document.querySelector('.nav-search-input')):null,
            cards:[...document.querySelectorAll('.recommended-container_floor-aside .feed-card')]
                .filter(e=>e.getBoundingClientRect().height>0).slice(0,6).map(rect)};
    })()`);
}
async function surface() {
    const targets = await (await fetch('http://127.0.0.1:9222/json')).json();
    return JSON.parse(targets.find(target => target.id === page.target.id).description);
}
async function swipe(x1, y1, x2, y2, duration = 450) {
    const native = await surface();
    const width = await page.evaluate('visualViewport.width');
    const scale = native.width / width;
    adb(['shell', 'input', 'swipe', String(Math.round(native.screenX+x1*scale)), String(Math.round(native.screenY+y1*scale)),
        String(Math.round(native.screenX+x2*scale)), String(Math.round(native.screenY+y2*scale)), String(duration)]);
}
async function tap(selector) {
    const point = await page.evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.scrollIntoView({block:'nearest',behavior:'instant'});const r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()`);
    const native = await surface();
    const scale = native.width / await page.evaluate('visualViewport.width');
    adb(['shell', 'input', 'tap', String(Math.round(native.screenX+point.x*scale)), String(Math.round(native.screenY+point.y*scale))]);
}
function record(name, details) { evidence.push({ name, ...details }); console.log('PASS ' + name); }
try {
    await page.send('Page.navigate', { url: 'https://www.bilibili.com/' });
    await waitFor('document.documentElement.dataset.bilispeedPage === "home" && document.querySelectorAll(".feed-card .bili-video-card__info--tit").length >= 4');
    await delay(1200);
    const initial = await state();
    assert(initial.banner.every(banner => banner.height === 0));
    assert(initial.search?.width > 150);
    assert(initial.cards.length >= 4);
    assert(Math.abs(initial.cards[0].y-initial.cards[1].y) < 2);
    assert(initial.cards[0].y < 160, 'No empty carousel space above the videos');
    assert(initial.scrollWidth <= initial.width+2);
    const tabs = await nativeUi();
    assert.deepEqual(tabs.map(tab => tab.label), ['首页', '热门', '动态', '我的', '设置']);
    assert(tabs[0].selected);
    record('compact-home-five-tabs', { state: initial, tabs });
    await screen('initial');

    await swipe(initial.width/2, 110, initial.width/2, 340);
    await screen('pull');
    await waitFor('performance.timeOrigin !== ' + initial.document + ' && document.querySelectorAll(".feed-card .bili-video-card__info--tit").length >= 4');
    await delay(800);
    const refreshed = await state();
    assert.equal(new URL(refreshed.url).pathname, '/');
    assert(refreshed.banner.every(banner => banner.height === 0));
    record('native-pull-reloaded-home', { before: initial.document, state: refreshed });
    await screen('refreshed');

    await waitFor('document.documentElement.scrollHeight > innerHeight + 120');
    await page.evaluate('scrollTo({top:Math.min(850,document.documentElement.scrollHeight-innerHeight),behavior:"instant"});true');
    await waitFor('scrollY > 100');
    await swipe(initial.width/2, 110, initial.width/2, 280);
    await delay(1000);
    const scrolled = await state();
    assert.equal(scrolled.document, refreshed.document);
    record('normal-scroll-keeps-document', { state: scrolled });

    await page.evaluate('scrollTo(0,0);true');
    await waitFor('scrollY < 2');
    await swipe(initial.width/2, 110, initial.width/2, 130);
    await delay(800);
    assert.equal((await state()).document, refreshed.document);
    record('short-pull-keeps-document', {});

    await tap('.nav-search-input');
    await page.send('Input.insertText', { text: '电路5小时' });
    await tap('.nav-search-btn');
    await waitFor('location.hostname === "search.bilibili.com" && document.querySelector(".bili-video-card__info--tit")');
    const search = await state();
    assert.equal(new URL(search.url).searchParams.get('keyword'), '电路5小时');
    assert((await nativeUi()).every(tab => !tab.selected));
    record('header-search-opens-results', { state: search });
    await screen('search');

    await tapTab('首页');
    await waitFor('location.hostname === "www.bilibili.com" && location.pathname === "/" && document.querySelectorAll(".feed-card .bili-video-card__info--tit").length >= 4');
    record('home-tab-returns-from-search', { state: await state() });

    for (const [label, condition] of [
        ['热门', 'location.pathname.startsWith("/v/popular") && document.querySelector(".video-card__info")'],
        ['动态', 'location.hostname === "t.bilibili.com" && document.querySelector(".bili-dyn-item")'],
        ['我的', 'location.pathname === "/__bilispeed__/me" && document.querySelector("main")']
    ]) {
        await tapTab(label);
        await waitFor(condition);
        assert((await nativeUi()).find(tab => tab.label === label)?.selected);
        record('navigation-' + label, { url: await page.evaluate('location.href') });
    }
    await tapTab('设置');
    assert((await nativeUi()).find(tab => tab.label === '设置')?.selected);
    record('navigation-settings', {});
    await tapTab('首页');
    await waitFor('location.pathname === "/" && document.querySelectorAll(".feed-card .bili-video-card__info--tit").length >= 4');
    await screen('final');
} finally {
    await writeFile('artifacts/device-' + prefix + 'home-live.json', JSON.stringify(evidence, null, 2)+'\n');
    page.close();
}
