// Run during the opt-in device inspection session, with adb forward tcp:9222.
// The same bounded DOM workload can be used against an installed baseline APK.
import { connectDevice } from './device-cdp.mjs';
import { writeFile } from 'node:fs/promises';

const page = await connectDevice();
try {
    const result = await page.evaluate(`(async () => {
        if (!window.__BiliSpeed) throw new Error('Speed controller is not ready');
        const original = Element.prototype.querySelectorAll;
        const fixture = document.createElement('div');
        fixture.dataset.bilispeedProbe = '';
        fixture.style.display = 'none';
        const overlays = document.createElement('div');
        overlays.className = 'bpx-player-dm-container';
        fixture.append(overlays);
        document.body.append(fixture);
        const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
        let scans = 0, visited = 0;
        const start = performance.now();
        try {
            await delay(350);
            Element.prototype.querySelectorAll = function(selector) {
                const result = original.call(this, selector);
                if (selector === '*' && (this === fixture || fixture.contains(this))) { scans++; visited += result.length; }
                return result;
            };
            for (let batch = 0; batch < 12; batch++) {
                overlays.replaceChildren(...Array.from({length: 18}, () => {
                    const item = document.createElement('span');
                    item.innerHTML = '<i>弹幕</i><b>进度</b>';
                    return item;
                }));
                await delay(130);
            }
            await delay(250);
            const result = {scans, visited, overlayBatches:12, overlayElements:648, elapsedMs:Math.round(performance.now()-start)};
            const before = window.__BiliSpeed.snapshot().videos;
            const video = document.createElement('video');
            fixture.append(video);
            for (let attempt=0;attempt<30 && window.__BiliSpeed.snapshot().videos===before;attempt++) await delay(50);
            result.newVideoDiscovered = window.__BiliSpeed.snapshot().videos === before + 1;
            result.rate = video.playbackRate;
            return result;
        } finally { Element.prototype.querySelectorAll = original; fixture.remove(); }
    })()`);
    if (!result.newVideoDiscovered) throw new Error('Newly mounted video was missed: ' + JSON.stringify(result));
    console.log(JSON.stringify(result, null, 2));
    if (process.argv[2]) await writeFile(process.argv[2], JSON.stringify(result, null, 2) + '\n');
} finally { page.close(); }
