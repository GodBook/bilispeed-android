import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import { connectDevice } from './device-cdp.mjs';
const serial=process.argv[2];if(!serial)throw new Error('Pass device serial');
const version=(await readFile(new URL('../version.properties',import.meta.url),'utf8')).match(/^versionName=(.+)$/m)[1].trim();
const prefix=process.env.BILISPEED_EVIDENCE_PREFIX||version+'-';
if(!/^[A-Za-z0-9.-]+$/.test(prefix))throw new Error('Invalid evidence prefix');
const page=await connectDevice(), evidence=[];
const delay=ms=>new Promise(r=>setTimeout(r,ms));
const adb=args=>execFileSync('adb',['-s',serial,...args],{timeout:30000,maxBuffer:16*1024*1024});
async function wait(expression,timeout=45000) {
    const end=Date.now()+timeout;
    while(Date.now()<end) {try{if(await page.evaluate(expression))return;}catch(_){}await delay(250);}
    throw new Error('Timed out: '+expression);
}
async function screenshot(name) {await writeFile(`artifacts/device-${prefix}${name}.png`,adb(['exec-out','screencap','-p']));}
async function tap(selector) {
    for(let preparation=0;preparation<3;preparation++) {
    if(await page.evaluate(`!!document.querySelector(${JSON.stringify(selector)})?.closest('#bilispeed-touch-controls')&&document.getElementById('bilispeed-touch-controls').dataset.hidden==='true'`)) {
        await tap('.bpx-player-video-area');
        await wait('document.getElementById("bilispeed-touch-controls").dataset.hidden==="false"');
    }
    await page.evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});if(!e)throw Error('Missing target');e.scrollIntoView({block:'center',behavior:'instant'});return true})()`);
    await delay(250);
    const point=await page.evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)}),r=e.getBoundingClientRect(),x=r.x+r.width/2,y=r.y+r.height/2;return {x,y,width:visualViewport.width,visible:e.checkVisibility(),hit:!!document.elementFromPoint(x,y)?.closest(${JSON.stringify(selector)})};})()`);
    // Rotation/autohide may change the target during preparation. Remap only
    // before sending input; an already executed action is never retried.
    if(!point.hit||!point.visible)continue;
    const targets=await(await fetch('http://127.0.0.1:9222/json')).json();
    const native=JSON.parse(targets.find(e=>e.id===page.target.id).description),scale=native.width/point.width;
    adb(['shell','input','tap',String(Math.round(native.screenX+point.x*scale)),String(Math.round(native.screenY+point.y*scale))]);await delay(250);
    return;
    }
    throw new Error('Touch target obscured: '+selector);
}
async function tab(label) {
    adb(['shell','uiautomator','dump','/data/local/tmp/bilispeed-1.3-ui.xml']);
    const xml=adb(['shell','cat','/data/local/tmp/bilispeed-1.3-ui.xml']).toString();
    const node=[...xml.matchAll(/<node\b[^>]*>/g)].find(e=>e[0].includes(`content-desc="B站${label}"`)&&e[0].includes('android.widget.TextView'))?.[0];
    assert(node,'Missing native tab '+label);
    const [,l,t,r,b]=/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/.exec(node).map(Number);
    adb(['shell','input','tap',String(Math.round((l+r)/2)),String(Math.round((t+b)/2))]);await delay(300);
}
function record(name,data={}) {evidence.push({name,...data});console.log('PASS '+name);}
try {
    await page.send('Page.navigate',{url:'https://www.bilibili.com/__bilispeed__/me'});
    await wait('!!document.querySelector("#night") && !!window.__BILI_THEME__');
    if(await page.evaluate('__BILI_THEME__.dark')) {await tap('#night');await wait('!__BILI_THEME__.dark');}
    await page.send('Page.navigate',{url:'https://www.bilibili.com/'});
    await wait('!!document.querySelector(".feed-card") && !!window.__BiliBrowserState && !!window.__BiliTouch');await delay(1200);
    const home=await page.evaluate(`({url:location.href,timeOrigin:performance.timeOrigin,cards:[...document.querySelectorAll('.container>.feed-card')].filter(e=>e.getBoundingClientRect().height>0).slice(0,6).map(e=>{const r=e.getBoundingClientRect();return {y:r.y,h:r.height,m:getComputedStyle(e).marginTop}})})`);
    assert(home.cards.length>=6);assert(home.cards.every(e=>e.m==='0px'));
    assert(Math.abs(home.cards[4].y-Math.max(home.cards[2].y+home.cards[2].h,home.cards[3].y+home.cards[3].h)-18)<2);
    record('official-home-row-spacing',home);await screenshot('home-light');
    await page.evaluate('scrollTo(0,620);__BiliBrowserState.position();true');await delay(300);
    await tab('首页');await wait('scrollY===0');
    assert.equal(await page.evaluate('performance.timeOrigin'),home.timeOrigin);record('repeat-home-keeps-document');
    for(let attempt=0;attempt<4;attempt++){
        if(await page.evaluate('document.documentElement.scrollHeight-innerHeight>=620'))break;
        await page.evaluate('scrollTo({top:document.documentElement.scrollHeight,behavior:"instant"});true');await delay(500);
    }
    await page.evaluate('scrollTo({top:Math.min(620,document.documentElement.scrollHeight-innerHeight),behavior:"instant"});__BiliBrowserState.position();true');await delay(300);
    const listBefore=await page.evaluate(`(()=>{const e=[...document.querySelectorAll('a[href*="/video/"]')].find(e=>{const r=e.getBoundingClientRect();return r.height>0&&r.bottom>0&&r.top<innerHeight});return {y:scrollY,anchor:e?.getAttribute('href'),offset:e?.getBoundingClientRect().top,document:performance.timeOrigin}})()`);
    assert(listBefore.y>100,'Homepage must have a real scrolled position');
    await page.send('Page.navigate',{url:'https://www.bilibili.com/video/BV1T6VFzAE1c?p=2'});
    await wait('location.pathname.includes("BV1T6VFzAE1c") && document.querySelector("video")?.readyState>=2');
    adb(['shell','input','keyevent','KEYCODE_BACK']);
    await wait('location.pathname==="/" && !!window.__BiliBrowserState && !!document.querySelector(".feed-card")');
    await delay(7200); // The bounded restoration also waits for official lazy loading.
    const listAfter=await page.evaluate(`(()=>{const e=[...document.querySelectorAll('a[href]')].find(e=>e.getAttribute('href')===${JSON.stringify(listBefore.anchor)}&&e.getBoundingClientRect().height>0);return {y:scrollY,max:document.documentElement.scrollHeight-innerHeight,anchorOffset:e?.getBoundingClientRect().top,document:performance.timeOrigin}})()`);
    assert(Math.abs(listAfter.y-Math.min(listBefore.y,listAfter.max))<=32 || Number.isFinite(listAfter.anchorOffset)&&Math.abs(listAfter.anchorOffset-listBefore.offset)<=32,'Return must restore the anchor or nearest available position');
    record('official-video-back-restores-list',{before:listBefore,after:listAfter});await screenshot('navigation-return');
    await tab('我的');await wait('location.pathname==="/__bilispeed__/me" && !!document.querySelector("#night") && !!window.__BiliMyRefresh');
    await tap('#night');await wait('document.documentElement.dataset.bilispeedTheme==="dark"');record('my-controls-native-theme');await screenshot('my-dark');
    await tab('设置');await screenshot('settings-dark');await tab('首页');
    await wait('location.pathname==="/" && document.documentElement.dataset.bilispeedTheme==="dark" && document.documentElement.hasAttribute("data-bilispeed-touch") && document.querySelectorAll(".feed-card").length>=4 && getComputedStyle(document.body).backgroundColor==="rgb(25, 26, 31)"');await screenshot('home-dark');record('theme-shared-between-native-and-web');
    await page.send('Page.navigate',{url:'https://www.bilibili.com/video/BV1T6VFzAE1c?p=2'});
    await wait('!!document.querySelector("[data-bilispeed-control=more]") && !!window.player?.getQuality && !!document.querySelector(".bpx-player-ctrl-quality-menu-item") && document.querySelector("video")?.readyState>=2');
    await page.evaluate('player.pause();player.seek(45);true');await wait('Math.abs(player.getCurrentTime()-45)<1');
    await tap('[data-bilispeed-control=speed]');await tap('[data-rate="2.5"]');await wait('Math.abs(player.getPlaybackRate()-2.5)<.01');
    await tap('[data-bilispeed-control=close-panel]');
    for(const value of ['32','16','0']) {
        await tap('[data-bilispeed-control=more]');await tap('[data-bilispeed-control=more-quality]');
        await page.evaluate(`window.qualityClickTrace=[];document.addEventListener('click',e=>qualityClickTrace.push({c:e.target.className,k:e.target.dataset?.bilispeedControl,q:e.target.dataset?.qualityOption,value:e.target.dataset?.value}),{capture:true});true`);
        const before=await page.evaluate('({quality:player.getQuality(),time:player.getCurrentTime(),rate:player.getPlaybackRate()})');
        await tap(`[data-quality-option="${value}"]`);
        await wait('document.querySelector("[data-bilispeed-control=quality-status]")?.textContent.startsWith("已切换")',55000);
        const after=await page.evaluate('({quality:player.getQuality(),time:player.getCurrentTime(),rate:player.getPlaybackRate(),paused:player.isPaused(),width:document.querySelector("video").videoWidth})');
        assert.equal(String(after.quality.nowQ),value);assert(Math.abs(after.time-before.time)<=3);assert.equal(after.rate,2.5);
        record('official-touch-quality-'+value,{before,after});await screenshot('quality-'+value);await tap('[data-bilispeed-control=close-panel]');
    }
    await tap('[data-bilispeed-control=fullscreen]');await wait('document.documentElement.hasAttribute("data-bilispeed-fullscreen")&&innerWidth>innerHeight');
    await delay(500);
    if(await page.evaluate('document.querySelector("#bilispeed-touch-controls").dataset.hidden==="true"'))await tap('.bpx-player-video-area');
    await wait('document.querySelector("#bilispeed-touch-controls").dataset.hidden==="false"');
    await tap('[data-bilispeed-control=more]');await tap('[data-bilispeed-control=more-quality]');await tap('[data-quality-option="32"]');
    await wait('document.querySelector("[data-bilispeed-control=quality-status]")?.textContent.startsWith("已切换")',55000);
    assert.equal(await page.evaluate('document.documentElement.hasAttribute("data-bilispeed-fullscreen")'),true);
    record('fullscreen-quality-keeps-fullscreen',await page.evaluate('({quality:player.getQuality(),time:player.getCurrentTime(),rate:player.getPlaybackRate()})'));await screenshot('quality-fullscreen');
    await tap('[data-bilispeed-control=close-panel]');await tap('[data-bilispeed-control=fullscreen]');await wait('!document.documentElement.hasAttribute("data-bilispeed-fullscreen")');
} finally {
    try { evidence.push({name:'final-observation',state:await page.evaluate(`({url:location.href,quality:window.player?.getQuality?.(),time:window.player?.getCurrentTime?.(),status:document.querySelector('[data-bilispeed-control=quality-status]')?.textContent,clicks:window.qualityClickTrace})`)}); } catch (_) { }
    await writeFile(`artifacts/device-${prefix}new-live.json`,JSON.stringify(evidence,null,2)+'\n');page.close();
}
