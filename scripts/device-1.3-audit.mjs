// Opt-in inspection of official DOM. No account writes or stream interception.
import { connectDevice } from './device-cdp.mjs';
import { writeFile } from 'node:fs/promises';
const page = await connectDevice();
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function wait(expression) {
    for (let i = 0; i < 100; i++) {
        try { if (await page.evaluate(expression)) return; } catch (_) { }
        await delay(200);
    }
    throw new Error('Timed out: ' + expression);
}
try {
    const result = { date: new Date().toISOString(), kind: 'official DOM feasibility, baseline 1.2.10' };
    await page.send('Page.navigate', { url: 'https://www.bilibili.com/' });
    await wait('!!document.querySelector(".feed-card") && !!window.__BiliTouch');
    const geometry = `Array.from(document.querySelectorAll('.recommended-container_floor-aside>.container>:is(.feed-card,.bili-video-card,.floor-single-card,.load-more-anchor)')).slice(0,12).map(e=>({class:e.className,y:e.getBoundingClientRect().y,height:e.getBoundingClientRect().height,margin:getComputedStyle(e).margin}))`;
    result.homeBefore = await page.evaluate(geometry);
    await page.evaluate(`(()=>{const s=document.createElement('style');s.id='audit-gap';s.textContent='html[data-bilispeed-touch] .recommended-container_floor-aside>.container>:is(.feed-card,.bili-video-card,.floor-single-card,.load-more-anchor){margin-top:0!important}';document.head.append(s);return true})()`);
    result.homeSameDocumentAfter = await page.evaluate(geometry);
    await page.evaluate(`document.querySelector('#audit-gap').remove();true`);
    await page.send('Page.navigate', { url: 'https://www.bilibili.com/video/BV1T6VFzAE1c' });
    await wait('!!window.player?.getQuality && document.querySelector("video")?.readyState>=2 && !!document.querySelector(".bpx-player-ctrl-quality-menu-item")');
    result.officialMenu = await page.evaluate(`Array.from(document.querySelectorAll('.bpx-player-ctrl-quality-menu-item')).map(e=>({value:e.dataset.value,text:e.textContent.trim(),class:e.className}))`);
    result.quality = [];
    for (const value of ['32', '16', '0']) {
        const before = await page.evaluate(`({quality:player.getQuality(),time:player.getCurrentTime(),rate:player.getPlaybackRate(),width:document.querySelector('video').videoWidth})`);
        await page.evaluate(`document.querySelector('.bpx-player-ctrl-quality-menu-item[data-value="${value}"]').click();true`);
        await wait(`String(player.getQuality().nowQ)==='${value}' && document.querySelector('video').readyState>=2`);
        result.quality.push({ requested: value, before, after: await page.evaluate(`({quality:player.getQuality(),time:player.getCurrentTime(),rate:player.getPlaybackRate(),width:document.querySelector('video').videoWidth,active:document.querySelector('.bpx-player-ctrl-quality-menu-item.bpx-state-active')?.dataset.value})`) });
    }
    await writeFile(process.argv[2] || 'artifacts/device-1.3.0-feasibility.json', JSON.stringify(result, null, 2) + '\n');
    console.log(JSON.stringify(result));
} finally { page.close(); }
