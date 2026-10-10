// Read-only page geometry/screenshot evidence from the connected phone's WebView.
// Start DeviceUiInspectionTest and forward its devtools socket to tcp:9222 first.
import { connectDevice } from './device-cdp.mjs';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';

const [name, serial, url] = process.argv.slice(2);
if (!name || !serial || !/^[a-zA-Z0-9._-]+$/.test(name)) throw new Error('Usage: device-page-audit.mjs <evidence-name> <serial> [url]');
const page = await connectDevice();
const preview = process.env.BILISPEED_PREVIEW_LAYOUT === '1';
try {
    if (url) {
        await page.send('Page.navigate', { url });
        const deadline = Date.now() + 45000;
        while (Date.now() < deadline) {
            await new Promise(resolve => setTimeout(resolve, 500));
            if (await page.evaluate('document.readyState === "complete" && (!!window.__BiliTouch || location.pathname === "/__bilispeed__/me")')) break;
        }
        await new Promise(resolve => setTimeout(resolve, 1800));
    }
    // Preview is explicitly labeled and never substitutes for installed-APK QA.
    if (preview) {
        const source = await readFile('app/src/main/assets/desktop-touch.js', 'utf8');
        const css = source.match(/const stylesheet = `([\s\S]*?)`;/)[1].replaceAll('${attribute}', 'data-bilispeed-touch');
        await page.evaluate('document.getElementById("bilispeed-touch-style").textContent=' + JSON.stringify(css));
        await new Promise(resolve => setTimeout(resolve, 500));
    }
    const result = await page.evaluate(`(() => {
        const rect = e => { const r = e.getBoundingClientRect(); return { x: r.x, y: r.y, w: r.width, h: r.height, right: r.right, bottom: r.bottom }; };
        const describe = e => ({ tag: e.localName, id: e.id, class: typeof e.className === 'string' ? e.className : '', ...rect(e) });
        const selectors = ['.history-record', '.watchlater-list', '.space-main', '.space-favlist', '.favlist-main', '.favlist-aside',
            '.favlist-items', '.fav-list-main', '.items-list', '.bili-dyn-home--member', '.bili-dyn-home__center', '.bili-dyn-list', '.bili-dyn-item',
            '.bili-dyn-content__orig', '.bili-dyn-card-video', '.bili-dyn-card-video__body', '.bili-dyn-tabs', '.bili-dyn-up-list',
            '.history-card', '.watchlater-card', '.items-list > *', '.space-video-card', '.space-follow', '.space-follow-list',
            '.breadcrumbs__top', '.list-header-main', '.fav-list-header', '.nav-bar__main', '.upinfo', '.bili-mini-content-wp'];
        const all = Array.from(document.querySelectorAll('body *')).filter(e => !e.closest('.bili-header, script, style, svg') && e.getBoundingClientRect().width > 0);
        return { url: location.href, kind: document.documentElement.dataset.bilispeedPage, adapted: document.documentElement.hasAttribute('data-bilispeed-touch'),
            width: innerWidth, height: innerHeight, scrollWidth: document.documentElement.scrollWidth, scrollY,
            regions: Object.fromEntries(selectors.map(s => [s, Array.from(document.querySelectorAll(s)).slice(0,12).map(describe)]).filter(([,v]) => v.length)),
            overflow: all.filter(e => { const r = rect(e); return r.w > innerWidth + 2 || r.x < -2 && r.right > 0 || r.right > innerWidth + 2 && r.x < innerWidth; }).slice(0,80).map(describe),
            structure: all.filter(e => ['main','div','section','nav','article','header','button','input','a'].includes(e.localName)).slice(0,300).map(describe) };
    })()`);
    result.preview = preview;
    await mkdir('artifacts', { recursive: true });
    await writeFile('artifacts/device-' + name + '.json', JSON.stringify(result, null, 2));
    const shot = await page.send('Page.captureScreenshot', { format: 'png' });
    await writeFile('artifacts/device-' + name + '.png', Buffer.from(shot.data, 'base64'));
    execFileSync('adb', ['-s', serial, 'shell', 'screencap', '-p', '/data/local/tmp/bilispeed-preview.png']);
    execFileSync('adb', ['-s', serial, 'pull', '/data/local/tmp/bilispeed-preview.png', 'artifacts/device-' + name + '-native.png'], { stdio: 'ignore' });
    console.log(JSON.stringify({ name, preview, url: result.url, kind: result.kind, width: result.width, scrollWidth: result.scrollWidth,
        regions: Object.fromEntries(Object.entries(result.regions).map(([k,v]) => [k, v.map(({x,y,w,h})=>({x,y,w,h}))])), overflow: result.overflow.slice(0,8) }));
} finally { page.close(); }
