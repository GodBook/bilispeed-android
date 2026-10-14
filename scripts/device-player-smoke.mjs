// Run against the opt-in DeviceUiInspectionTest, with adb forward tcp:9222.
// Native taps exercise the installed APK; no replacement app scripts are injected.
import { connectDevice } from './device-cdp.mjs';
import { readFile, writeFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';

const serial = process.argv[2] || process.env.BILISPEED_TEST_SERIAL;
if (!serial) throw new Error('Pass the connected phone serial');
const stage = process.argv[3] || 'course';
const version = (await readFile(new URL('../version.properties', import.meta.url), 'utf8')).match(/^versionName=(.+)$/m)[1].trim();
const prefix = process.env.BILISPEED_EVIDENCE_PREFIX || version + '-';
if (!/^[A-Za-z0-9.-]+$/.test(prefix)) throw new Error('Invalid evidence prefix');
const page = await connectDevice();
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const selector = name => '[data-bilispeed-control="' + name + '"]';
const q = value => 'document.querySelector(' + JSON.stringify(value) + ')';
const records = [];
let lastPoint;

async function waitFor(expression, seconds = 45, stableMilliseconds = 0) {
    const deadline = Date.now() + seconds * 1000;
    let readySince = 0;
    while (Date.now() < deadline) {
        try {
            if (await page.evaluate('Boolean(' + expression + ')')) {
                if (!readySince) readySince = Date.now();
                if (Date.now() - readySince >= stableMilliseconds) return;
            } else readySince = 0;
        } catch (error) {
            // The old execution context can disappear between the native tap
            // and this read. Retry only navigation-related inspection errors.
            if (!/navigated or closed|Execution context was destroyed|Cannot find context/.test(error.message)) throw error;
            readySince = 0;
        }
        await delay(300);
    }
    throw new Error('Timeout: ' + expression);
}

async function point(target, x = .5, y = .5) {
    const css = await page.evaluate(`(()=>{const e=${q(target)},r=e.getBoundingClientRect(),x=r.x+r.width*${x},y=r.y+r.height*${y};return {x,y,width:visualViewport.width,visible:e.checkVisibility(),hit:!!document.elementFromPoint(x,y)?.closest(${JSON.stringify(target)}),disabled:!!e.disabled};})()`);
    const targets = await (await fetch('http://127.0.0.1:9222/json')).json();
    const surface = JSON.parse(targets.find(target => target.id === page.target.id).description);
    const scale = surface.width / css.width;
    const device=[Math.round(surface.screenX + css.x * scale), Math.round(surface.screenY + css.y * scale)];
    lastPoint={target,css,surface,device};
    return Object.assign(device,{hit:css.hit,visible:css.visible,disabled:css.disabled});
}

async function tap(target) {
    for(let preparation=0;preparation<3;preparation++){
    if (await page.evaluate(`!!${q(target)}?.closest('#bilispeed-touch-controls')&&document.getElementById('bilispeed-touch-controls').dataset.hidden==='true'`)) {
        const [x, y] = await point('.bpx-player-video-area', .5, .25);
        execFileSync('adb', ['-s', serial, 'shell', 'input', 'tap', String(x), String(y)]);
        await waitFor('document.getElementById("bilispeed-touch-controls").dataset.hidden==="false"');
    }
    await page.evaluate(`(()=>{
        document.documentElement.style.scrollBehavior='auto';
        const element=${q(target)};
        if (!window.__BILI_TOUCH_FULLSCREEN__ && !document.fullscreenElement && element.closest('#bilispeed-touch-controls'))
            document.getElementById('playerWrap').scrollIntoView({behavior:'instant',block:'start'});
        element.scrollIntoView({behavior:'instant',block:'nearest'});
        return true;
    })()`);
    await delay(250);
    const position=await point(target);
    // Autohide can expire while resolving the native surface. No button input
    // has been sent yet; reveal and remap it before taking the actual action.
    if(!position.hit||!position.visible)continue;
    assert.ok(!position.disabled,'Disabled touch target: '+target);
    const [x, y] = position;
    execFileSync('adb', ['-s', serial, 'shell', 'input', 'tap', String(x), String(y)]);
    await delay(250);
    return;
    }
    throw new Error('Touch target is hidden or obscured: '+target);
}

async function screenshot(name) {
    await delay(300);
    const native = '/data/local/tmp/bilispeed-player-smoke.png';
    execFileSync('adb', ['-s', serial, 'shell', 'screencap', '-p', native]);
    execFileSync('adb', ['-s', serial, 'pull', native, `artifacts/device-${prefix}${name}-native.png`], { stdio: 'ignore' });
}

async function record(name) {
    await waitFor('!document.getElementById("bilispeed-touch-controls").getAnimations().some(animation=>animation.playState==="running")', 5);
    const result = await page.evaluate(`(()=>{
        const rect = element => {const r=element.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height};};
        const info=document.querySelector('#mirror-vdcon .video-info-container');
        const meta=info?.querySelector('.video-info-meta');
        const toolbar=document.querySelector('#mirror-vdcon .video-toolbar-container');
        return {url:location.href,title:document.title,theme:document.documentElement.dataset.bilispeedTheme,width:innerWidth,documentWidth:document.documentElement.scrollWidth,
            information:info&&meta&&toolbar?{container:rect(info),meta:rect(meta),toolbar:rect(toolbar)}:null,
            fullscreen:!!window.__BILI_TOUCH_FULLSCREEN__||!!document.fullscreenElement,domFullscreen:!!document.fullscreenElement,
            fullscreenLayout:document.documentElement.hasAttribute('data-bilispeed-fullscreen'),
            player:rect(document.querySelector('.bpx-player-video-area')),height:innerHeight,
            initialFullscreen:window.__BILI_TOUCH_FULLSCREEN__,state:window.__BiliSpeed.snapshot(),
            controlState:{hidden:document.getElementById('bilispeed-touch-controls').dataset.hidden,
                ariaHidden:document.getElementById('bilispeed-touch-controls').getAttribute('aria-hidden'),
                inert:document.getElementById('bilispeed-touch-controls').inert,
                opacity:getComputedStyle(document.getElementById('bilispeed-touch-controls')).opacity,
                panelHidden:document.getElementById('bilispeed-player-panel').hidden},
            video:Array.from(document.querySelectorAll('video')).map(v=>({ready:v.readyState,paused:v.paused,time:v.currentTime,rate:v.playbackRate})),
            counts:Array.from(document.querySelectorAll('#bilispeed-episodes .episode-count')).map(e=>e.textContent),
            controls:Array.from(document.querySelectorAll('#bilispeed-touch-controls > button:not([hidden])')).map(e=>({name:e.dataset.bilispeedControl,label:e.textContent,disabled:e.disabled,...rect(e)})),
            groups:Array.from(document.querySelectorAll('.episode-group')).map(e=>({kind:e.dataset.episodeKind,count:e.querySelectorAll('a').length,visible:e.querySelectorAll('a:not([hidden])').length,
                selected:e.querySelector('[aria-current]')?.href,durations:Array.from(e.querySelectorAll('time')).slice(0,2).map(t=>t.textContent)}))};
    })()`);
    records.push({ name, ...result });
    await writeFile(`artifacts/device-${prefix}player-${stage}.json`, JSON.stringify(records, null, 2) + '\n');
    assert.ok(result.documentWidth <= result.width + 2, 'Page overflow');
    if(!result.fullscreen&&result.information){
        const {container,meta,toolbar}=result.information;
        assert.ok(meta.y+meta.h<=container.y+container.h+1,'Video metadata overflows its container');
        assert.ok(toolbar.y>=meta.y+meta.h-1,'Video metadata overlaps the action bar');
    }
    assert.equal(result.fullscreenLayout, result.fullscreen, 'Stale fullscreen layout on a new document');
    assert.equal(result.initialFullscreen, result.fullscreen, 'Stale fullscreen bootstrap');
    for (const button of result.controls) {
        assert.ok(button.w >= 44 && button.h >= 44, button.name + ' touch target');
        assert.ok(button.x >= -1 && button.x + button.w <= result.width + 2, button.name + ' outside screen');
    }
    assert.ok(result.controls.every(button => Math.abs(button.y - result.controls[0].y) <= 12), 'Controls must stay on one row');
    if (result.fullscreen) {
        assert.ok(result.width > result.height, 'Fullscreen must stay landscape');
        assert.ok(Math.abs(result.player.x) < 2 && Math.abs(result.player.y) < 2
            && Math.abs(result.player.w - result.width) < 2 && Math.abs(result.player.h - result.height) < 2, 'Player must fill fullscreen');
    }
    console.log(JSON.stringify({ status: 'PASS', name, url: result.url, counts: result.counts, rate: result.state.selected, fullscreen: result.fullscreen }));
}

async function navigate(url) {
    await page.send('Page.navigate', { url });
    const expected = new URL(url);
    const part = Number(expected.searchParams.get('p')) || 1;
    await waitFor(`location.pathname===${JSON.stringify(expected.pathname)}&&+(new URL(location.href).searchParams.get('p')||1)===${part}&&document.querySelector('video')?.readyState>=2&&${q(selector('speed'))}`);
    await delay(600);
    if (!(await page.evaluate('document.querySelector("video").paused'))) await tap(selector('play'));
}

async function waitPart(part) {
    await waitFor(`+(new URL(location.href).searchParams.get('p')||1)===${part}&&${q('[data-episode-kind="parts"] .episode-count')}?.textContent.startsWith('${part} /')&&document.querySelector('video')?.readyState>=2`);
    if (!(await page.evaluate('document.querySelector("video").paused'))) await tap(selector('play'));
}

try {
    if (stage === 'autohide') {
        await navigate('https://www.bilibili.com/video/BV1T6VFzAE1c/?p=1');
        await tap(selector('play'));
        await waitFor('!document.querySelector("video").paused');
        // Official initialization can replace the first player host. Verify the
        // complete hidden state stays settled after that mount and its fade.
        await waitFor(`(()=>{const c=document.getElementById('bilispeed-touch-controls');return c.dataset.hidden==='true'&&c.inert&&c.getAttribute('aria-hidden')==='true'&&getComputedStyle(c).opacity==='0'&&getComputedStyle(c).visibility==='hidden';})()`, 45, 600);
        await screenshot('portrait-auto-hidden'); await record('portrait-playing-auto-hides');
        await tap('.bpx-player-video-area');
        await waitFor('document.getElementById("bilispeed-touch-controls").dataset.hidden==="false"');
        assert.equal(await page.evaluate('document.querySelector("video").paused'), false);
        await screenshot('portrait-tap-controls'); await record('portrait-single-tap-reveals-without-pausing');
        await tap(selector('speed')); await tap('[data-rate="1.5"]');
        await waitFor('document.querySelector("video").playbackRate===1.5');
        await delay(3400);
        assert.ok(await page.evaluate('!document.getElementById("bilispeed-player-panel").hidden&&document.getElementById("bilispeed-touch-controls").dataset.hidden==="false"'));
        await screenshot('portrait-speed-panel'); await record('portrait-speed-panel-stays-visible');
        await tap(selector('close-panel'));
        await waitFor('document.getElementById("bilispeed-touch-controls").dataset.hidden==="true"');
        await record('portrait-panel-close-resumes-auto-hide');
        await tap('.bpx-player-video-area');
        await waitFor('document.getElementById("bilispeed-touch-controls").dataset.hidden==="false"');
        await tap('.bpx-player-video-area');
        await waitFor('document.getElementById("bilispeed-touch-controls").dataset.hidden==="true"');
        assert.equal(await page.evaluate('document.querySelector("video").paused'), false);
        await record('portrait-second-tap-hides-without-pausing');
        const [x, y] = await point('.bpx-player-video-area', .5, .25);
        execFileSync('adb', ['-s', serial, 'shell', `input tap ${x} ${y}; input tap ${x} ${y}`]);
        await waitFor('document.querySelector("video").paused&&document.getElementById("bilispeed-touch-controls").dataset.hidden==="false"');
        await record('portrait-double-tap-pauses-once');
        // A four-second real finger drag must outlast the idle timeout.
        const from = await point(selector('seek'), .15, .5), to = await point(selector('seek'), .25, .5);
        execFileSync('adb', ['-s', serial, 'shell', 'input', 'swipe', ...from.map(String), ...to.map(String), '4000']);
        assert.equal(await page.evaluate('document.getElementById("bilispeed-touch-controls").dataset.hidden'), 'false');
        await record('portrait-long-seek-keeps-controls');
        await waitFor('document.getElementById("bilispeed-touch-controls").dataset.hidden==="true"');
        await record('portrait-seek-release-resumes-auto-hide');
        await tap(selector('more'));
        await screenshot('portrait-more-panel'); await record('portrait-more-options-fit');
        await tap(selector('close-panel'));
        await page.send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
        try {
            await tap(selector('speed'));
            assert.equal(await page.evaluate('getComputedStyle(document.getElementById("bilispeed-touch-controls")).transitionDuration'), '0s');
            assert.equal(await page.evaluate('getComputedStyle(document.getElementById("bilispeed-player-panel")).animationName'), 'none');
            await record('reduced-motion-disables-control-and-panel-animation');
            await tap(selector('close-panel'));
        } finally { await page.send('Emulation.setEmulatedMedia', { features: [] }); }
    } else if (stage === 'course') {
        await navigate('https://www.bilibili.com/video/BV1T6VFzAE1c/?p=1');
        await waitFor('document.querySelectorAll("[data-episode-kind=parts] a").length===73&&document.querySelectorAll("[data-episode-kind=collection] a").length===2');
        assert.equal(await page.evaluate(q('[data-episode-kind=collection] .episode-count') + '.textContent'), '2 / 2');
        assert.equal(await page.evaluate(q('[data-episode-kind=collection] a[aria-current] time') + '.textContent'), '25:58:00');
        await page.evaluate(q('#bilispeed-episodes') + '.scrollIntoView({block:"start",behavior:"instant"});true');
        await screenshot('course-collapsed'); await record('course-collapsed');
        await tap('[data-episode-toggle]');
        assert.equal(await page.evaluate(q('[data-episode-toggle]') + '.getAttribute("aria-expanded")'), 'true');
        assert.equal(await page.evaluate('document.querySelectorAll("[data-episode-kind=parts] a:not([hidden])").length'), 73);
        const from = await point('.episode-list', .5, .75), to = await point('.episode-list', .5, .25);
        execFileSync('adb', ['-s', serial, 'shell', 'input', 'swipe', ...from.map(String), ...to.map(String), '450']);
        assert.ok(await page.evaluate(q('.episode-list') + '.scrollTop>0'));
        await tap('.episode-actions button:nth-child(2)');
        assert.equal(await page.evaluate(q('.episode-list') + '.scrollTop'), 0);
        await screenshot('course-expanded'); await record('course-expanded');
        await tap('[data-episode-toggle]');
        await page.evaluate('window.scrollTo({top:0,behavior:"instant"});true');
        await tap(selector('next')); await waitPart(2);
        assert.equal(await page.evaluate(q('[data-episode-kind=collection] .episode-count') + '.textContent'), '2 / 2');
        await record('toolbar-next-p2');
        await tap(selector('previous')); await waitPart(1); await record('toolbar-previous-p1');
        await tap(selector('speed')); await tap('[data-rate="2"]');
        await waitFor('document.querySelector("video").playbackRate===2&&window.__BiliSpeed.snapshot().selected===2');
        await tap(selector('close-panel'));
        await tap(selector('next')); await waitPart(2);
        await waitFor('document.querySelector("video").playbackRate===2');
        await screenshot('course-controls'); await record('speed-persists-across-parts');
        await tap(selector('fullscreen'));
        await waitFor('document.fullscreenElement&&innerWidth>innerHeight&&document.getElementById("bilispeed-touch-controls").dataset.hidden==="true"');
        await delay(700);
        // A possible Android immersive-mode hint is dismissed by the fixture's
        // existing UiAutomation helper in the instrumentation suite.
        const [x, y] = await point('.bpx-player-video-area', .5, .25);
        execFileSync('adb', ['-s', serial, 'shell', 'input', 'tap', String(x), String(y)]);
        await waitFor('document.getElementById("bilispeed-touch-controls").dataset.hidden==="false"');
        await screenshot('course-fullscreen-controls');
        await tap(selector('speed')); await tap('[data-rate="1.5"]');
        await waitFor('document.querySelector("video").playbackRate===1.5');
        await delay(3300);
        assert.ok(await page.evaluate('!!document.fullscreenElement&&!document.getElementById("bilispeed-player-panel").hidden&&document.getElementById("bilispeed-touch-controls").dataset.hidden==="false"'));
        await screenshot('course-fullscreen-speed'); await record('fullscreen-speed-keeps-fullscreen');
        await tap(selector('close-panel')); await tap(selector('next')); await waitPart(3);
        assert.equal(await page.evaluate('!!window.__BILI_TOUCH_FULLSCREEN__&&innerWidth>innerHeight'), true);
        await screenshot('course-fullscreen-next-p3');
        await record('fullscreen-next-p3');
        await tap(selector('play'));
        await waitFor('!document.querySelector("video").paused&&document.querySelector("video").currentTime>0.5');
        await record('fullscreen-next-plays');
        await tap(selector('next')); await waitPart(4);
        await record('fullscreen-next-p4');
        await tap(selector('previous')); await waitPart(3);
        await screenshot('course-fullscreen-previous-p3'); await record('fullscreen-previous-p3');
        execFileSync('adb', ['-s', serial, 'shell', 'input', 'keyevent', 'KEYCODE_BACK']);
        await waitFor('!window.__BILI_TOUCH_FULLSCREEN__&&!document.fullscreenElement&&innerHeight>innerWidth');
        await screenshot('course-exit-fullscreen'); await record('fullscreen-back-restores-portrait');
    } else if (stage === 'boundaries') {
        await navigate('https://www.bilibili.com/video/BV1T6VFzAE1c/?p=73');
        await waitPart(73);
        assert.ok(await page.evaluate(q(selector('next')) + '.disabled'));
        assert.ok(await page.evaluate(q('[data-episode-kind=parts] a[aria-current]') + '.checkVisibility()'));
        await page.evaluate(q('#bilispeed-episodes') + '.scrollIntoView({block:"start",behavior:"instant"});true');
        await screenshot('course-last-part'); await record('last-part-visible-next-disabled');
        await tap(selector('previous')); await waitPart(72); await record('last-part-previous-p72');
        await navigate('https://www.bilibili.com/video/BV1T6VFzAE1c/?p=1');
        await waitPart(1);
        const previous = await page.evaluate('window.__BiliTouchVideo.getNavigation().previous.url');
        await tap(selector('previous'));
        await waitFor(`location.pathname===${JSON.stringify(new URL(previous).pathname)}&&document.querySelector('video')?.readyState>=2&&document.querySelector('[data-episode-kind=collection] a[aria-current]')`);
        await record('first-part-previous-collection-video');
    } else if (stage === 'collection-fullscreen') {
        await navigate('https://www.bilibili.com/video/BV1Q4iJBAEsr/?p=35');
        await waitPart(35);
        await waitFor(`${q(selector('next'))}?.dataset.episodeUrl?.includes('BV1T6VFzAE1c')`);
        await screenshot('collection-single-row'); await record('collection-last-part-single-row');
        await tap(selector('fullscreen'));
        await waitFor('document.fullscreenElement&&innerWidth>innerHeight');
        await delay(700);
        await tap(selector('next')); await waitPart(1);
        await waitFor('location.pathname.includes("BV1T6VFzAE1c")');
        await screenshot('collection-fullscreen-next'); await record('fullscreen-next-across-collection');
        await tap(selector('previous'));
        await waitFor('location.pathname.includes("BV1Q4iJBAEsr")&&document.querySelector("video")?.readyState>=2&&document.querySelector("[data-episode-kind=collection] a[aria-current]")');
        await screenshot('collection-fullscreen-previous'); await record('fullscreen-previous-across-collection');
        await tap(selector('fullscreen'));
        await waitFor('!window.__BILI_TOUCH_FULLSCREEN__&&!document.fullscreenElement&&innerHeight>innerWidth');
        await screenshot('collection-exit-fullscreen'); await record('exit-button-restores-portrait');
        await tap(selector('fullscreen'));
        await waitFor('document.fullscreenElement&&innerWidth>innerHeight');
        await delay(700);
        await tap(selector('next')); await waitPart(2);
        await page.send('Page.navigate', { url: 'https://www.bilibili.com/video/BV1qMp46wE1n/' });
        await waitFor('location.pathname.includes("BV1qMp46wE1n")&&document.querySelector("video")?.readyState>=2&&!window.__BILI_TOUCH_FULLSCREEN__&&innerHeight>innerWidth');
        await record('unrelated-video-clears-fullscreen');
    } else if (stage === 'single') {
        await navigate('https://www.bilibili.com/video/BV1qMp46wE1n/');
        await waitFor('document.getElementById("bilispeed-episodes")?.hidden');
        assert.ok(await page.evaluate(q(selector('next')) + '.hidden&&' + q(selector('previous')) + '.hidden'));
        await screenshot('single-video-controls'); await record('single-video-hides-episode-buttons');
        await tap(selector('speed')); await tap('[data-rate="1.25"]');
        await waitFor('document.querySelector("video").playbackRate===1.25');
        await tap(selector('close-panel')); await record('single-video-speed');
    } else throw new Error('Use autohide, course, boundaries, collection-fullscreen, or single');
} catch (error) {
    const state=await page.evaluate(`({url:location.href,ready:document.readyState,hidden:document.querySelector('#bilispeed-touch-controls')?.dataset.hidden,navigation:window.__BiliTouchVideo?.getNavigation?.(),video:[...document.querySelectorAll('video')].map(v=>({ready:v.readyState,paused:v.paused,time:v.currentTime})),previous:document.querySelector('[data-bilispeed-control=previous]')?.outerHTML})`).catch(()=>null);
    await writeFile(`artifacts/device-${prefix}player-${stage}-failure.json`,JSON.stringify({message:error.message,lastPoint,state},null,2)+'\n');
    await screenshot('player-' + stage + '-failure').catch(() => {});
    console.error(error); process.exitCode = 1;
} finally { page.close(); }
