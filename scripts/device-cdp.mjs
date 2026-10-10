// USB-only helper for the opt-in DeviceUiInspectionTest. Requires adb forward tcp:9222.
import { readFile, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { execFileSync } from 'node:child_process';

export async function connectDevice() {
    const targets = await (await fetch('http://127.0.0.1:9222/json')).json();
    const target = targets.find(page => page.type === 'page' && page.url.includes('bilibili.com')) || targets[0];
    if (!target) throw new Error('No inspected WebView');
    const socket = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
        socket.addEventListener('open', resolve, { once: true });
        socket.addEventListener('error', reject, { once: true });
    });
    let sequence = 0;
    const pending = new Map();
    socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        const request = pending.get(message.id);
        if (!request) return;
        pending.delete(message.id);
        clearTimeout(request.timer);
        if (message.error) request.reject(new Error(JSON.stringify(message.error)));
        else request.resolve(message.result);
    });
    const send = (method, params = {}) => new Promise((resolve, reject) => {
        const id = ++sequence;
        const timer = setTimeout(() => { pending.delete(id); reject(new Error('CDP timeout: ' + method)); }, 20000);
        pending.set(id, { resolve, reject, timer });
        socket.send(JSON.stringify({ id, method, params }));
    });
    const evaluate = async expression => {
        const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
        if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
        return result.result.value;
    };
    return { send, evaluate, target, close: () => socket.close() };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    const page = await connectDevice();
    try {
        const [action, value, deviceSerial] = process.argv.slice(2);
        if (action === 'navigate') console.log(JSON.stringify(await page.send('Page.navigate', { url: value })));
        else if (action === 'eval') console.log(JSON.stringify(await page.evaluate(value)));
        else if (action === 'eval-file') console.log(JSON.stringify(await page.evaluate(await readFile(value, 'utf8'))));
        else if (action === 'tap' || action === 'double-tap') {
            await page.evaluate(`document.querySelector(${JSON.stringify(value)}).scrollIntoView({behavior:'instant',block:'nearest'});true`);
            await new Promise(resolve => setTimeout(resolve, 250));
            const point = await page.evaluate(`(()=>{const r=document.querySelector(${JSON.stringify(value)}).getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,width:visualViewport.width};})()`);
            const surfaces = await (await fetch('http://127.0.0.1:9222/json')).json();
            const surface = JSON.parse(surfaces.find(target => target.id === page.target.id).description);
            const scale = surface.width / point.width;
            const x = Math.round(surface.screenX + point.x * scale);
            const y = Math.round(surface.screenY + point.y * scale);
            const serial = deviceSerial || process.env.BILISPEED_TEST_SERIAL || process.env.ANDROID_SERIAL;
            if (!serial) throw new Error('Pass the device serial as the third argument or set BILISPEED_TEST_SERIAL');
            if (action === 'double-tap') execFileSync('adb', ['-s', serial, 'shell', `input tap ${x} ${y}; input tap ${x} ${y}`]);
            else execFileSync('adb', ['-s', serial, 'shell', 'input', 'tap', String(x), String(y)]);
            console.log(JSON.stringify({ css: point, device: { x, y } }));
        }
        else if (action === 'screenshot') {
            const result = await page.send('Page.captureScreenshot', { format: 'png' });
            await writeFile(value, Buffer.from(result.data, 'base64'));
            console.log(value);
        } else throw new Error('Use navigate, eval, eval-file, or screenshot');
    } finally { page.close(); }
}
