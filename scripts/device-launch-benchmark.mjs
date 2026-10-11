// Native Activity launch timing; does not claim webpage readiness or energy gains.
import { execFileSync } from 'node:child_process';
import { writeFile } from 'node:fs/promises';
const [serial, label] = process.argv.slice(2);
if (!serial || !/^[A-Za-z0-9.-]+$/.test(label)) throw new Error('Pass serial and version');
const adb = args => execFileSync('adb',['-s',serial,...args],{encoding:'utf8',timeout:30000});
const result = { label, date:new Date().toISOString(), device:adb(['shell','getprop','ro.build.fingerprint']).trim(),
    webview:adb(['shell','dumpsys','webviewupdate']), definition:'am start -W TotalTime; native Activity display only, warm=bring existing Activity to foreground', cold:[],warm:[] };
for (let i=0;i<20;i++) {
    adb(['shell','am','force-stop','app.bilispeed.browser']);
    let raw=adb(['shell','am','start','-W','-n','app.bilispeed.browser/.MainActivity']);
    result.cold.push({iteration:i+1,ms:Number(/TotalTime:\s*(\d+)/.exec(raw)?.[1]),raw});
    adb(['shell','input','keyevent','KEYCODE_HOME']);
    raw=adb(['shell','am','start','-W','-n','app.bilispeed.browser/.MainActivity']);
    result.warm.push({iteration:i+1,ms:Number(/TotalTime:\s*(\d+)/.exec(raw)?.[1] || 0),raw});
}
for (const name of ['cold','warm']) {
    const values=result[name].map(e=>e.ms).sort((a,b)=>a-b);
    if (values.some(e=>!Number.isFinite(e))) throw new Error('Missing launch timing');
    result[name+'Summary']={n:values.length,median:(values[9]+values[10])/2,p95:values[18]};
}
adb(['shell','input','keyevent','KEYCODE_HOME']);
await writeFile(`artifacts/device-${label}-native-launch.json`,JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify({label,cold:result.coldSummary,warm:result.warmSummary}));
