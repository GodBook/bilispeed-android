// Paired official-site journeys. Timings include ADB/CDP scheduling; no energy/FPS claims.
import { connectDevice } from './device-cdp.mjs';
import { readFile, writeFile, rename, rm } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';
const [serial,label]=process.argv.slice(2);
if(!serial || !/^[A-Za-z0-9.-]+$/.test(label))throw new Error('Pass device serial and evidence label');
const page=await connectDevice();
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const adb=args=>execFileSync('adb',['-s',serial,...args],{encoding:'utf8',timeout:30000,maxBuffer:16*1024*1024});
const result={label,date:new Date().toISOString(),definition:'Warm WebView official-site journeys, browser HTTP cache disabled in both groups; host wall clock includes ADB/CDP input and polling. Theme baseline changes My only; candidate changes native and web.',
    device:adb(['shell','getprop','ro.build.fingerprint']).trim(),webview:adb(['shell','dumpsys','webviewupdate']),cycles:[]};
let legacy;
let lastPoint;
const preferenceRecord='artifacts/device-web-preference-'+serial.replace(/[^A-Za-z0-9.-]/g,'_')+'.json';
async function wait(expression,timeout=45000) {
    const end=Date.now()+timeout;
    while(Date.now()<end){
        try{if(await page.evaluate(expression))return;}
        catch(error){if(!/context|navigation|navigated|Cannot find/i.test(error.message))throw error;}
        await delay(60);
    }
    throw new Error('Timed out: '+expression);
}
async function painted(){await page.evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(()=>r(true))))');}
async function navigate(url,ready){
    const origin=await page.evaluate('performance.timeOrigin');
    await page.send('Page.navigate',{url});await wait(`performance.timeOrigin!==${origin} && (${ready})`);await painted();
}
async function point(selector){
    if(selector.includes('data-bilispeed-control') && await page.evaluate('document.querySelector("#bilispeed-touch-controls")?.dataset.hidden==="true"')){
        const [x,y]=await point('.bpx-player-video-area');adb(['shell','input','tap',x,y]);
        await wait('document.querySelector("#bilispeed-touch-controls")?.dataset.hidden==="false"');
    }
    await page.evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});if(!window.__BILI_TOUCH_FULLSCREEN__ && e.closest('#bilispeed-touch-controls'))document.getElementById('playerWrap').scrollIntoView({block:'start',behavior:'instant'});e.scrollIntoView({block:'nearest',behavior:'instant'});return true})()`);
    await delay(150);
    const box=await page.evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)}),r=e.getBoundingClientRect(),x=r.x+r.width/2,y=r.y+r.height/2;return {x,y,width:visualViewport.width,hit:!!document.elementFromPoint(x,y)?.closest(${JSON.stringify(selector)})}})()`);
    assert(box.hit,'Obscured target '+selector);
    const targets=await(await fetch('http://127.0.0.1:9222/json')).json(),surface=JSON.parse(targets.find(t=>t.id===page.target.id).description);
    const scale=surface.width/box.width;
    const device=[String(Math.round(surface.screenX+box.x*scale)),String(Math.round(surface.screenY+box.y*scale))];
    lastPoint={selector,box,surface,device};return device;
}
async function tap(selector){const [x,y]=await point(selector);adb(['shell','input','tap',x,y]);}
const playerReady='!!window.__BiliTouchPlayer && document.querySelector("video")?.readyState>=2 && !!document.querySelector("[data-bilispeed-control=next]")';
const homeReady='location.pathname==="/" && innerWidth<600 && !!window.__BiliTouch && document.querySelectorAll(".feed-card .bili-video-card__info--tit").length>=4';
const myReady='location.pathname==="/__bilispeed__/me" && innerWidth<600 && !!document.querySelector("#night") && document.readyState==="complete"';
const partReady=n=>`new URL(location.href).searchParams.get('p')==='${n}' && (${playerReady}) && document.querySelector('[data-episode-kind=parts] a[aria-current]')?.href.includes('p=${n}')`;
const control=name=>`[data-bilispeed-control=${name}]`;
async function memory(){
    const processes=adb(['shell','dumpsys','activity','processes','app.bilispeed.browser']);
    const pids=[...new Set([...processes.matchAll(/ProcessRecord\{\w+ (\d+):com\.android\.webview:[^\n]+\/u0a\d+i\d+/g)].map(match=>match[1]))];
    return {dom:await page.send('Memory.getDOMCounters'),heap:await page.send('Runtime.getHeapUsage'),
        native:adb(['shell','dumpsys','meminfo','app.bilispeed.browser']),
        renderers:pids.map(pid=>({pid,footprint:adb(['shell','ps','-p',pid,'-o','PID,VSZ,RSS,NAME']),meminfo:adb(['shell','dumpsys','meminfo',pid])}))};
}
async function save(){
    for(const key of ['homeMs','returnMs','episodeMs','themeMs']){
        const values=result.cycles.map(c=>c[key]).sort((a,b)=>a-b),n=values.length;
        if(n)result[key]={n,median:n%2?values[(n-1)/2]:(values[n/2-1]+values[n/2])/2,p95:values[Math.ceil(n*.95)-1]};
    }
    await writeFile(`artifacts/device-${label}-journey-benchmark.json`,JSON.stringify(result,null,2)+'\n');
}
try{
    await page.send('Network.enable');await page.send('Network.setCacheDisabled',{cacheDisabled:true});
    await navigate('https://www.bilibili.com/__bilispeed__/me',myReady);
    let interrupted;
    try { interrupted=JSON.parse(await readFile(preferenceRecord,'utf8')); }
    catch(error){if(error.code!=='ENOENT')throw error;}
    if(interrupted){
        assert(interrupted.value===null || typeof interrupted.value==='string');
        legacy=interrupted.value;
        await page.evaluate(`{const v=${JSON.stringify(legacy)};if(v===null)localStorage.removeItem('bilispeed-profile-night');else localStorage.setItem('bilispeed-profile-night',v);}true`);
    } else {
        legacy=await page.evaluate('localStorage.getItem("bilispeed-profile-night")');
        await writeFile(preferenceRecord+'.pending',JSON.stringify({value:legacy,date:new Date().toISOString()}));
        await rename(preferenceRecord+'.pending',preferenceRecord);
    }
    result.initialMemory=await memory();
    for(let index=0;index<20;index++){
        const cycle={iteration:index+1};
        result.currentIteration=index+1;result.phase='home';
        let start=Date.now();await navigate('https://www.bilibili.com/',homeReady);cycle.homeMs=Date.now()-start;
        await wait('document.documentElement.scrollHeight>innerHeight+120');
        const scrollTarget=await page.evaluate('(()=>{const y=Math.min(620,document.documentElement.scrollHeight-innerHeight);scrollTo(0,y);window.__BiliBrowserState?.position();return y})()');
        await wait(`Math.abs(scrollY-${scrollTarget})<3`);await delay(150);
        const before=await page.evaluate('({document:performance.timeOrigin,scroll:scrollY})');
        result.phase='video-before-back';
        await navigate('https://www.bilibili.com/video/BV1T6VFzAE1c?p=2',partReady(2));
        start=Date.now();adb(['shell','input','keyevent','KEYCODE_BACK']);await wait(homeReady);await painted();cycle.returnMs=Date.now()-start;
        cycle.listBefore=before;cycle.listAfter=await page.evaluate('({document:performance.timeOrigin,scroll:scrollY})');
        result.phase='video-before-fullscreen';
        await navigate('https://www.bilibili.com/video/BV1T6VFzAE1c?p=2',partReady(2));
        await tap(control('fullscreen'));await wait('!!window.__BILI_TOUCH_FULLSCREEN__ && innerWidth>innerHeight');await delay(350);
        const [x,y]=await point(control('next'));start=Date.now();adb(['shell','input','tap',x,y]);
        cycle.nextInput=lastPoint;
        await wait(partReady(3));await painted();cycle.episodeMs=Date.now()-start;
        assert.equal(await page.evaluate('!!window.__BILI_TOUCH_FULLSCREEN__'),true);
        assert.equal(await page.evaluate('document.querySelectorAll("#bilispeed-touch-controls").length'),1);
        cycle.fullscreenKept=true;
        result.phase='exit-fullscreen-and-theme';
        await tap(control('fullscreen'));await wait('!window.__BILI_TOUCH_FULLSCREEN__ && innerHeight>innerWidth');
        await navigate('https://www.bilibili.com/__bilispeed__/me',myReady);
        const night=await page.evaluate('document.body.classList.contains("night")');
        const moon=await point('#night');start=Date.now();adb(['shell','input','tap',...moon]);
        await wait(`document.body.classList.contains('night')===${!night}`);await painted();cycle.themeMs=Date.now()-start;
        await tap('#night');await wait(`document.body.classList.contains('night')===${night}`);
        result.cycles.push(cycle);await save();console.log(`PASS cycle ${index+1}/20 (${label})`);
        if((index+1)%5===0){(result.resourceSamples??=[]).push({iteration:index+1,...await memory()});await save();}
    }
    result.finalMemory=await memory();result.complete=true;await save();
    console.log(JSON.stringify({label,n:result.cycles.length,home:result.homeMs,back:result.returnMs,episode:result.episodeMs,theme:result.themeMs}));
} catch(error){
    result.failure={message:error.message,lastPoint,state:await page.evaluate('({url:location.href,width:innerWidth,height:innerHeight,scroll:scrollY,hidden:document.querySelector("#bilispeed-touch-controls")?.dataset.hidden,controls:[...document.querySelectorAll("[data-bilispeed-control=next],[data-bilispeed-control=fullscreen]")].map(e=>{const r=e.getBoundingClientRect();return {r:r.toJSON(),hit:document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)?.outerHTML.slice(0,800)}})})').catch(()=>null)};
    await writeFile(`artifacts/device-${label}-benchmark-failure.png`,execFileSync('adb',['-s',serial,'exec-out','screencap','-p'],{maxBuffer:16*1024*1024}));
    throw error;
} finally{
    let cleanupFailure;
    if(legacy!==undefined)try{
        const restored=await page.evaluate(`(()=>{if(location.origin!=='https://www.bilibili.com')return false;const v=${JSON.stringify(legacy)};if(v===null)localStorage.removeItem('bilispeed-profile-night');else localStorage.setItem('bilispeed-profile-night',v);return localStorage.getItem('bilispeed-profile-night')===v})()`);
        assert(restored,'Original My night preference was not restored');
        await rm(preferenceRecord);result.webPreferenceRestored=true;
    } catch(error){result.webPreferenceRestored=false;cleanupFailure=error;}
    await page.send('Network.setCacheDisabled',{cacheDisabled:false}).catch(()=>{});
    await save();page.close();
    if(cleanupFailure)throw cleanupFailure;
}
