// Select the production theme through the phone's touch input, inside a protected session.
import { connectDevice } from './device-cdp.mjs';
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';
const serial=process.argv[2];if(!serial)throw new Error('Pass device serial');
const page=await connectDevice();
async function wait(expression){
    const deadline=Date.now()+45000;
    while(Date.now()<deadline){
        try{if(await page.evaluate(expression))return;}catch(error){if(!/context|navigation|Cannot find/i.test(error.message))throw error;}
        await new Promise(resolve=>setTimeout(resolve,200));
    }
    throw new Error('Theme preparation timed out: '+expression);
}
try{
    await page.send('Page.navigate',{url:'https://www.bilibili.com/__bilispeed__/me'});
    await wait('document.readyState==="complete" && !!document.querySelector("#night") && !!window.__BILI_THEME__');
    if(!await page.evaluate('!!__BILI_THEME__.dark')){
        const point=await page.evaluate('(()=>{const e=document.querySelector("#night"),r=e.getBoundingClientRect(),x=r.x+r.width/2,y=r.y+r.height/2;return {x,y,width:visualViewport.width,hit:!!document.elementFromPoint(x,y)?.closest("#night")}})()');
        assert(point.hit);
        const targets=await(await fetch('http://127.0.0.1:9222/json')).json();
        const surface=JSON.parse(targets.find(target=>target.id===page.target.id).description),scale=surface.width/point.width;
        execFileSync('adb',['-s',serial,'shell','input','tap',String(Math.round(surface.screenX+point.x*scale)),String(Math.round(surface.screenY+point.y*scale))]);
    }
    await wait('document.documentElement.dataset.bilispeedTheme==="dark"');
    console.log('PASS native-and-web-dark-theme-prepared');
}finally{page.close();}
