// Identical bounded workloads on the installed baseline/candidate production APK.
// Counts and JS timings describe these workloads, not whole-device energy or FPS.
import { connectDevice } from './device-cdp.mjs';
import { writeFile } from 'node:fs/promises';
import assert from 'node:assert/strict';

const label = process.argv[2];
if (!/^[A-Za-z0-9.-]+$/.test(label)) throw new Error('Pass an evidence label');
const page = await connectDevice();
try {
    await page.send('Page.navigate', { url: 'https://www.bilibili.com/__bilispeed__/me' });
    for (let i = 0; i < 100; i++) {
        if (await page.evaluate('location.pathname==="/__bilispeed__/me" && document.readyState==="complete" && !!window.__BiliSpeed && !!window.__BiliBrowserState')) break;
        await new Promise(resolve => setTimeout(resolve, 100));
    }
    const result = await page.evaluate(`(async () => {
        const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
        const fixture = document.createElement('main');
        fixture.style.cssText = 'position:relative;width:100%;background:white';
        fixture.innerHTML = Array.from({length:600}, (_, i) => '<a href="/video/BVfixture' + i + '" style="display:block;height:70px">Video ' + i + '</a>').join('');
        document.body.replaceChildren(fixture);
        scrollTo(0, 70 * 520);
        await delay(350);
        const rect = Element.prototype.getBoundingClientRect;
        const query = Element.prototype.querySelectorAll;
        let rectangles = 0, subtreeScans = 0, visited = 0;
        Element.prototype.getBoundingClientRect = function() { rectangles++; return rect.call(this); };
        Element.prototype.querySelectorAll = function(selector) {
            const nodes = query.call(this, selector);
            if (selector === '*' && this.dataset?.mediaBatch) { subtreeScans++; visited += nodes.length; }
            return nodes;
        };
        const positions = [];
        try {
            for (let batch = 0; batch < 20; batch++) {
                const start = performance.now(), before = rectangles;
                window.__BiliBrowserState.position();
                positions.push({ms:performance.now()-start,rectangles:rectangles-before});
            }
            const before = window.__BiliSpeed.snapshot().videos;
            for (let i = 0; i < 12; i++) {
                const subtree = document.createElement('section');
                subtree.dataset.mediaBatch = String(i + 1);
                subtree.style.display = 'none';
                subtree.innerHTML = '<div>' + '<span><i>card</i><b>text</b></span>'.repeat(500) + '</div><video></video>';
                fixture.append(subtree);
                await delay(150);
            }
            await delay(200);
            const mediaDiscovered = window.__BiliSpeed.snapshot().videos - before;
            const host = document.createElement('div');
            const shadow = host.attachShadow({mode:'open'});
            shadow.innerHTML = '<video></video>';
            fixture.append(host);
            await delay(300);
            const shadowDiscovered = window.__BiliSpeed.snapshot().videos === before + 13;
            host.remove();
            await delay(1700);
            fixture.append(host);
            await delay(350);
            const shadowReconnected = window.__BiliSpeed.snapshot().videos === before + 13;
            return {positions,subtreeScans,visited,mediaDiscovered,shadowDiscovered,shadowReconnected};
        } finally {
            Element.prototype.getBoundingClientRect = rect;
            Element.prototype.querySelectorAll = query;
        }
    })()`);
    assert.equal(result.mediaDiscovered, 12);
    assert.equal(result.shadowDiscovered, true);
    assert.equal(result.shadowReconnected, true);
    const values = result.positions.map(item => item.ms).sort((a,b) => a-b);
    result.positionSummary = {n:values.length,median:(values[9]+values[10])/2,p95:values[18],rectangles:result.positions.reduce((sum,item) => sum+item.rectangles,0)};
    await writeFile(`artifacts/device-${label}-overhead.json`, JSON.stringify({label,...result},null,2)+'\n');
    console.log(JSON.stringify({label,...result},null,2));
} finally { page.close(); }
