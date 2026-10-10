import { connectDevice } from './device-cdp.mjs';
import { writeFile, mkdir } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';

const page = await connectDevice();
const stage = process.argv[2];
const serial = process.argv[3] || process.env.BILISPEED_TEST_SERIAL || process.env.ANDROID_SERIAL;
const evidencePrefix = process.env.BILISPEED_EVIDENCE_PREFIX || '';
const accountMid = process.env.BILISPEED_ACCOUNT_MID || '';
if (accountMid && !/^[1-9][0-9]*$/.test(accountMid)) throw new Error('Invalid BILISPEED_ACCOUNT_MID');
const stages = {
    home: ['https://www.bilibili.com/', '.bili-video-card'],
    search: ['https://search.bilibili.com/all?keyword=%E7%94%B5%E8%B7%AF5%E5%B0%8F%E6%97%B6', '.bili-video-card__info--tit'],
    popular: ['https://www.bilibili.com/v/popular/all', '.video-card__info'],
    weekly: ['https://www.bilibili.com/v/popular/weekly', '.video-card__info'],
    history: ['https://www.bilibili.com/v/popular/history', '.video-card__info'],
    rank: ['https://www.bilibili.com/v/popular/rank/all', '.rank-item'],
    dynamic: ['https://t.bilibili.com/', '.bili-dyn-item'],
    'account-history': ['https://www.bilibili.com/account/history', '.history-record'],
    watchlater: ['https://www.bilibili.com/watchlater/list', '.watchlater-list'],
    me: ['https://www.bilibili.com/__bilispeed__/me', 'main'],
    login: ['https://passport.bilibili.com/login', '.login-pwd input'],
    video: ['https://www.bilibili.com/video/BV17x411w7KC/', '#bilispeed-touch-controls'],
    portrait: ['https://www.bilibili.com/video/BV1qMp46wE1n/', '#bilispeed-touch-controls'],
    parts: ['https://www.bilibili.com/video/BV17x411w7KC/', '#bilispeed-episodes[data-ready]'],
    collection: ['https://www.bilibili.com/video/BV12gpt6UER4/', '#bilispeed-episodes[data-ready]']
};
if (accountMid) for (const [name, path, selector] of [
    ['favorites', 'favlist', '.favlist-main'], ['space', '', '.space-home'],
    ['following', 'relation/follow', '.space-follow'], ['fans', 'relation/fans', '.space-fans'],
    ['uploads', 'upload/video', '.space-upload'], ['space-dynamic', 'dynamic', '.space-dynamic'],
    ['space-settings', 'settings', '.space-settings'], ['space-lists', 'lists', '.space-main'],
    ['space-bangumi', 'bangumi', '.space-subscribe']
]) stages[name] = ['https://space.bilibili.com/' + accountMid + '/' + path, selector];
export const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function waitFor(expression, seconds = 40) {
    const deadline = Date.now() + seconds * 1000;
    while (Date.now() < deadline) {
        if (await page.evaluate('Boolean(' + expression + ')')) return;
        await delay(400);
    }
    throw new Error('Timeout: ' + expression);
}
async function screenshot(name) {
    const result = await page.send('Page.captureScreenshot', { format: 'png' });
    await writeFile('artifacts/device-' + evidencePrefix + name + '-after.png', Buffer.from(result.data, 'base64'));
    if (serial) {
        execFileSync('adb', ['-s', serial, 'shell', 'screencap', '-p', '/data/local/tmp/bilispeed-preview.png']);
        execFileSync('adb', ['-s', serial, 'pull', '/data/local/tmp/bilispeed-preview.png', 'artifacts/device-' + evidencePrefix + name + '-native.png'], { stdio: 'ignore' });
    }
}
try {
    await mkdir('artifacts', { recursive: true });
    if (stages[stage]) {
        const [url, selector] = stages[stage];
        await page.send('Page.navigate', { url });
        await waitFor('document.querySelector(' + JSON.stringify(selector) + ')');
        if (['video', 'portrait', 'parts', 'collection'].includes(stage)) await waitFor('document.querySelector("video")?.readyState >= 2');
        await delay(1000);
    } else if (stage === 'comments') {
        await waitFor('document.querySelector("#commentapp, bili-comments")');
        await page.evaluate('document.querySelector("#commentapp, bili-comments").scrollIntoView({behavior:"instant",block:"center"});true');
        await waitFor('document.querySelector("bili-comments")');
        await page.evaluate('document.documentElement.style.scrollBehavior="auto";document.querySelector("bili-comments").scrollIntoView({behavior:"instant",block:"start"});true');
        await waitFor('document.querySelector("bili-comments").shadowRoot?.querySelector("bili-comment-thread-renderer")');
        await delay(1200);
    } else if (stage === 'inline-login') {
        await page.evaluate('document.querySelector("bili-comments").shadowRoot.querySelector("bili-comments-header-renderer").shadowRoot.querySelector("#edit button").click();true');
        await waitFor('document.querySelector(".bili-mini-content-wp")');
        await delay(600);
    } else throw new Error('Unknown stage: ' + stage);

    const result = await page.evaluate(`(()=>{
        const rect = e => {const r=e.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height,right:r.right,bottom:r.bottom};};
        const cards=Array.from(document.querySelectorAll('.recommended-container_floor-aside .feed-card, .video-list > *, .popular-container .video-card, .rank-list .rank-item, .bili-dyn-item, .history-card, .watchlater-list-grid > *, .fav-list-main .items__item, .relation-card'))
            .filter(e=>e.getBoundingClientRect().width>0).slice(0,8);
        const actions=document.querySelector('bili-comments')?.shadowRoot?.querySelector('bili-comment-thread-renderer')?.shadowRoot?.querySelector('bili-comment-renderer')?.shadowRoot?.querySelector('bili-comment-action-buttons-renderer')?.shadowRoot;
        const regions=Array.from(document.querySelectorAll('.history-record, .history-record .main-head, .breadcrumbs__top, .watchlater-list, .list-header-main, .space-main, .favlist-main, .fans-main, #bilispeed-folder-toggle, .relation-content, .upload-content, .space-settings .section, .bili-dyn-home--member > main, html[data-bilispeed-page="dynamic"] #app > .content, .bili-dyn-card-video'))
            .filter(e=>e.getBoundingClientRect().width>0).map(e=>({class:e.className,...rect(e)}));
        return {stage:${JSON.stringify(stage)},url:location.href,title:document.title,width:innerWidth,height:innerHeight,scrollWidth:document.documentElement.scrollWidth,
            cards:cards.map(rect),regions,kind:document.documentElement.dataset.bilispeedPage,
            dynamicHighlight:document.querySelector('.bili-dyn-list-tabs__highlight')?rect(document.querySelector('.bili-dyn-list-tabs__highlight')):null,
            tabs:Array.from(document.querySelectorAll('.nav-tabs__item')).map(e=>({text:e.innerText,...rect(e)})),
            player:document.querySelector('.bpx-player-container')?{screen:document.querySelector('.bpx-player-container').dataset.screen,position:getComputedStyle(document.querySelector('.bpx-player-container')).position,...rect(document.querySelector('.bpx-player-container'))}:null,
            video:Array.from(document.querySelectorAll('video')).map(v=>({width:v.videoWidth,height:v.videoHeight,ready:v.readyState,time:v.currentTime,paused:v.paused,rate:v.playbackRate})),
            videoHost:document.querySelector('[data-bilispeed-player]')?rect(document.querySelector('[data-bilispeed-player]')):null,
            commentActions:actions?{date:rect(actions.querySelector('#pubdate')),reply:rect(actions.querySelector('#reply')),nowrap:getComputedStyle(actions.querySelector('#reply button')).whiteSpace}:null,
            modal:document.querySelector('.bili-mini-content-wp')?rect(document.querySelector('.bili-mini-content-wp')):null,
            modalTitle:document.querySelector('.bili-mini-customer-title')?rect(document.querySelector('.bili-mini-customer-title')):null,
            modalAgreement:document.querySelector('.bili-mini-mask .login-agreement-wp')?rect(document.querySelector('.bili-mini-mask .login-agreement-wp')):null,
            inputs:Array.from(document.querySelectorAll('input')).filter(e=>e.getBoundingClientRect().width>0).map(rect)};
    })()`);
    assert(result.scrollWidth <= result.width + 2, 'Page overflows horizontally: ' + JSON.stringify(result));
    for (const card of result.cards) {
        assert(card.x >= -1 && card.right <= result.width + 2, 'Card extends outside viewport');
        assert(card.w > result.width * .3, 'Card is compressed');
    }
    for (const tab of result.tabs) assert(tab.x >= 0 && tab.right <= result.width + 2, 'Popular category is clipped');
    for (const region of result.regions) assert(region.x >= -1 && region.right <= result.width + 2, 'Content region is clipped: ' + JSON.stringify(region));
    if (stage === 'account-history') assert(result.kind === 'history', 'Redirected history route is not recognized');
    if (result.dynamicHighlight) assert(result.dynamicHighlight.h <= 8, 'Dynamic tab indicator covers its label');
    if (stage === 'comments') assert(result.player.bottom <= 1 && result.player.position !== 'fixed', 'Player obscures comments');
    if (stage === 'comments') assert(result.commentActions?.nowrap === 'nowrap' && result.commentActions.reply.w >= 24, 'Comment action text is compressed');
    if (stage === 'portrait') assert(result.videoHost.h <= result.height * .7 + 1, 'Portrait player takes too much page height');
    if (stage === 'login') for (const input of result.inputs) assert(input.x >= 0 && input.right <= result.width + 1 && input.w > 80, 'Login input is clipped');
    if (stage === 'inline-login') assert(result.modal.x >= 0 && result.modal.right <= result.width + 1
        && (!result.modalTitle || result.modalTitle.right <= result.modal.right)
        && result.modalAgreement.x >= result.modal.x && result.modalAgreement.right <= result.modal.right, 'Inline login dialog is clipped');
    await screenshot(stage);
    await writeFile('artifacts/device-' + evidencePrefix + stage + '-result.json', JSON.stringify(result, null, 2));
    console.log(JSON.stringify({status:'PASS',stage,url:result.url,width:result.width,cards:result.cards.length,firstCardWidth:result.cards[0]?.w,video:result.video}));
} catch (error) {
    await screenshot(stage + '-failure').catch(() => {});
    console.error(error);
    process.exitCode = 1;
} finally { page.close(); }
