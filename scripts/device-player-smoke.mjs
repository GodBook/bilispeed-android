// Run against the opt-in DeviceUiInspectionTest, with adb forward tcp:9222.
// Native taps exercise the installed APK; no replacement app scripts are injected.
import { connectDevice } from './device-cdp.mjs';
import { writeFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';

const serial = process.argv[2] || process.env.BILISPEED_TEST_SERIAL;
if (!serial) throw new Error('Pass the connected phone serial');
const stage = process.argv[3] || 'course';
const prefix = process.env.BILISPEED_EVIDENCE_PREFIX || '1.2.8-';
if (!/^[A-Za-z0-9.-]+$/.test(prefix)) throw new Error('Invalid evidence prefix');
const page = await connectDevice();
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const selector = name => '[data-bilispeed-control="' + name + '"]';
const q = value => 'document.querySelector(' + JSON.stringify(value) + ')';
const records = [];

async function waitFor(expression, seconds = 45) {
    const deadline = Date.now() + seconds * 1000;
    while (Date.now() < deadline) {
        try {
            if (await page.evaluate('Boolean(' + expression + ')')) return;
        } catch (error) {
            // The old execution context can disappear between the native tap
            // and this read. Retry only navigation-related inspection errors.
            if (!/navigated or closed|Execution context was destroyed|Cannot find context/.test(error.message)) throw error;
        }
        await delay(300);
    }
    throw new Error('Timeout: ' + expression);
}

async function point(target, x = .5, y = .5) {
    const css = await page.evaluate(`(()=>{const r=${q(target)}.getBoundingClientRect();return {x:r.x+r.width*${x},y:r.y+r.height*${y},width:visualViewport.width};})()`);
    const targets = await (await fetch('http://127.0.0.1:9222/json')).json();
    const surface = JSON.parse(targets.find(target => target.id === page.target.id).description);
    const scale = surface.width / css.width;
    return [Math.round(surface.screenX + css.x * scale), Math.round(surface.screenY + css.y * scale)];
}

async function tap(target) {
    await page.evaluate(`(()=>{
        document.documentElement.style.scrollBehavior='auto';
        const element=${q(target)};
        if (!document.fullscreenElement && element.closest('#bilispeed-touch-controls'))
            document.getElementById('playerWrap').scrollIntoView({behavior:'instant',block:'start'});
        element.scrollIntoView({behavior:'instant',block:'nearest'});
        return true;
    })()`);
    await delay(250);
    const [x, y] = await point(target);
    execFileSync('adb', ['-s', serial, 'shell', 'input', 'tap', String(x), String(y)]);
    await delay(250);
}

async function screenshot(name) {
    await delay(300);
    const native = '/data/local/tmp/bilispeed-player-smoke.png';
    execFileSync('adb', ['-s', serial, 'shell', 'screencap', '-p', native]);
    execFileSync('adb', ['-s', serial, 'pull', native, `artifacts/device-${prefix}${name}-native.png`], { stdio: 'ignore' });
}

async function record(name) {
    const result = await page.evaluate(`(()=>{
        const rect = element => {const r=element.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height};};
        return {url:location.href,title:document.title,width:innerWidth,documentWidth:document.documentElement.scrollWidth,
            fullscreen:!!document.fullscreenElement,fullscreenLayout:document.documentElement.hasAttribute('data-bilispeed-fullscreen'),
            initialFullscreen:window.__BILI_TOUCH_FULLSCREEN__,state:window.__BiliSpeed.snapshot(),
            video:Array.from(document.querySelectorAll('video')).map(v=>({ready:v.readyState,paused:v.paused,time:v.currentTime,rate:v.playbackRate})),
            counts:Array.from(document.querySelectorAll('#bilispeed-episodes .episode-count')).map(e=>e.textContent),
            controls:Array.from(document.querySelectorAll('#bilispeed-touch-controls > button:not([hidden])')).map(e=>({name:e.dataset.bilispeedControl,label:e.textContent,disabled:e.disabled,...rect(e)})),
            groups:Array.from(document.querySelectorAll('.episode-group')).map(e=>({kind:e.dataset.episodeKind,count:e.querySelectorAll('a').length,visible:e.querySelectorAll('a:not([hidden])').length,
                selected:e.querySelector('[aria-current]')?.href,durations:Array.from(e.querySelectorAll('time')).slice(0,2).map(t=>t.textContent)}))};
    })()`);
    records.push({ name, ...result });
    await writeFile(`artifacts/device-${prefix}player-${stage}.json`, JSON.stringify(records, null, 2) + '\n');
    assert.ok(result.documentWidth <= result.width + 2, 'Page overflow');
    assert.equal(result.fullscreenLayout, result.fullscreen, 'Stale fullscreen layout on a new document');
    assert.equal(result.initialFullscreen, result.fullscreen, 'Stale fullscreen bootstrap');
    for (const button of result.controls) {
        assert.ok(button.w >= 44 && button.h >= 44, button.name + ' touch target');
        assert.ok(button.x >= -1 && button.x + button.w <= result.width + 2, button.name + ' outside screen');
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
    if (stage === 'course') {
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
        assert.equal(await page.evaluate('!!document.fullscreenElement'), false);
        await record('fullscreen-next-p3');
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
    } else if (stage === 'single') {
        await navigate('https://www.bilibili.com/video/BV1qMp46wE1n/');
        await waitFor('document.getElementById("bilispeed-episodes")?.hidden');
        assert.ok(await page.evaluate(q(selector('next')) + '.hidden&&' + q(selector('previous')) + '.hidden'));
        await screenshot('single-video-controls'); await record('single-video-hides-episode-buttons');
        await tap(selector('speed')); await tap('[data-rate="1.25"]');
        await waitFor('document.querySelector("video").playbackRate===1.25');
        await tap(selector('close-panel')); await record('single-video-speed');
    } else throw new Error('Use course, boundaries, or single');
} catch (error) {
    await screenshot('player-' + stage + '-failure').catch(() => {});
    console.error(error); process.exitCode = 1;
} finally { page.close(); }
