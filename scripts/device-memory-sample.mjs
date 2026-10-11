// Read-only renderer measurements: never forces GC or changes the inspected page.
import { connectDevice } from './device-cdp.mjs';
import { execFileSync } from 'node:child_process';
import { writeFile } from 'node:fs/promises';
const [serial, label] = process.argv.slice(2);
if (!serial || !/^[A-Za-z0-9.-]+$/.test(label)) throw new Error('Pass serial and evidence label');
const adb = args => execFileSync('adb', ['-s', serial, ...args], { encoding: 'utf8', timeout: 30000 });
const processes = adb(['shell', 'dumpsys', 'activity', 'processes', 'app.bilispeed.browser']);
const rendererPids = [...new Set([...processes.matchAll(/ProcessRecord\{\w+ (\d+):com\.android\.webview:[^\n]+\/u0a\d+i\d+/g)].map(match => match[1]))];
const result = { date: new Date().toISOString(), label, processes, renderers: [] };
const page = await connectDevice();
try {
    result.dom = await page.send('Memory.getDOMCounters');
    result.heap = await page.send('Runtime.getHeapUsage');
    result.page = await page.evaluate('({url:location.href,timeOrigin:performance.timeOrigin,history:history.length})');
    for (const pid of rendererPids) result.renderers.push({ pid,
        footprint: adb(['shell', 'ps', '-p', pid, '-o', 'PID,VSZ,RSS,NAME']),
        meminfo: adb(['shell', 'dumpsys', 'meminfo', pid]) });
    await writeFile(`artifacts/device-${label}-memory.json`, JSON.stringify(result, null, 2) + '\n');
    console.log(JSON.stringify({ label, dom: result.dom, heap: result.heap,
        page: result.page, renderers: result.renderers.map(({ pid, footprint }) => ({ pid, footprint })) }));
} finally { page.close(); }
