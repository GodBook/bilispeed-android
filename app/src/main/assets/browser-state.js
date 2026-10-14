(function () {
    'use strict';
    if (window !== top || window.__BiliBrowserState) return;
    let scrollTimer = null;
    let restoration = null;
    const send = data => { try { window.BiliSpeedBridge?.postMessage(JSON.stringify(data)); } catch (_) {} };
    function position() {
        if (!document.body || restoration) return;
        const card = [...document.querySelectorAll('a[href*="/video/"],.video-card a[href],.bili-dyn-item')]
            .find(e => { const r=e.getBoundingClientRect();return r.height>0 && r.bottom>0 && r.top<innerHeight; });
        send({ type:'page-position', url:location.href, y:scrollY,
            anchor:card?.getAttribute('href') || '', offset:card?.getBoundingClientRect().top || 0 });
    }
    function blocked(target) {
        if (document.querySelector('dialog[open],[aria-modal="true"],.bpx-player-dm-setting-wrap[data-bilispeed-danmaku-open]')) return true;
        if (document.activeElement?.matches('input,textarea,select,[contenteditable="true"]')) return true;
        for (let e=target; e && e!==document.body; e=e.parentElement) {
            if (e.matches?.('input,textarea,select,[contenteditable="true"],#bilibili-player,dialog,[role="dialog"]')) return true;
            const style = getComputedStyle(e);
            if (/(auto|scroll)/.test(style.overflowY+style.overflowX)
                    && (e.scrollHeight>e.clientHeight+2 || e.scrollWidth>e.clientWidth+2)) return true;
        }
        return false;
    }
    function gesture(event) { send({type:'refresh-gesture',url:location.href,blocked:blocked(event.target)}); }
    document.addEventListener('pointerdown', event => { cancelRestore(); gesture(event); position(); }, true);
    document.addEventListener('touchstart', gesture, {capture:true,passive:true});
    document.addEventListener('focusin', event => gesture(event));
    function cancelRestore() { if (restoration) { clearTimeout(restoration.timer); restoration=null; } }
    function restore(value) {
        cancelRestore();
        if (!value || !(value.y>0)) return;
        const work = {value, deadline:performance.now()+6000, timer:null}; restoration=work;
        function attempt() {
            if (restoration!==work) return;
            let y=value.y, found=false;
            if (value.anchor) {
                const anchor=[...document.querySelectorAll('a[href]')].find(e=>e.getAttribute('href')===value.anchor && e.getBoundingClientRect().height>0);
                if (anchor) { y=anchor.getBoundingClientRect().top+scrollY-value.offset; found=true; }
            }
            scrollTo({top:y,behavior:'instant'});
            if (performance.now()>work.deadline || (document.readyState==='complete' && Math.abs(scrollY-y)<2 && (found || !value.anchor))) {
                if (value.anchor && !found) send({type:'position-updated',url:location.href});
                restoration=null; position(); return;
            }
            work.timer=setTimeout(attempt,150);
        }
        attempt();
    }
    addEventListener('scroll', () => { clearTimeout(scrollTimer); scrollTimer=setTimeout(position,120); }, {passive:true});
    addEventListener('pagehide', () => {
        position(); cancelRestore();
        clearTimeout(scrollTimer); scrollTimer=null;
    });
    addEventListener('pageshow', () => { applyTheme(window.__BILI_THEME__); position(); });
    function applyTheme(config) {
        if (!config || !document.documentElement) return;
        window.__BILI_THEME__=config;
        document.documentElement.dataset.bilispeedTheme=config.dark?'dark':'light';
        document.documentElement.style.colorScheme=config.dark?'dark':'light';
        document.body?.classList.toggle('night',config.dark);
        document.getElementById('night')?.setAttribute('aria-pressed',String(config.dark));
        let style=document.getElementById('bilispeed-theme');
        if (!style && document.head) {
            style=document.createElement('style'); style.id='bilispeed-theme';
            style.textContent=`
html[data-bilispeed-theme] { --bs-bg:#fff;--bs-surface:#f5f6f8;--bs-ink:#24262e;--bs-muted:#656775;--bs-border:#e1e2e8;--bs-accent:#b52d5b;--bs-pressed:#fff0f5; }
html[data-bilispeed-theme="dark"] { --bs-bg:#191a1f;--bs-surface:#24262e;--bs-ink:#eeeef2;--bs-muted:#b2b4bf;--bs-border:#50525f;--bs-accent:#ff91b2;--bs-pressed:#3a2932; }
html[data-bilispeed-theme][data-bilispeed-touch],html[data-bilispeed-theme] .my-page {
 --bg1:var(--bs-bg);--bg2:var(--bs-surface);--bg3:var(--bs-surface);--bg1_float:var(--bs-bg);--bg2_float:var(--bs-surface);--text1:var(--bs-ink);--text2:var(--bs-muted);--text3:var(--bs-muted);--line_regular:var(--bs-border);--line_light:var(--bs-border);--graph_bg_thin:var(--bs-surface);--graph_bg_regular:var(--bs-surface);--brand_pink:var(--bs-accent);
}
html[data-bilispeed-touch][data-bilispeed-theme] body { background:var(--bs-bg)!important;color:var(--bs-ink)!important; }
html[data-bilispeed-touch][data-bilispeed-page="dynamic"][data-bilispeed-theme="dark"] #app > :is(.bg,.bgc) { display:none!important; }
html[data-bilispeed-touch][data-bilispeed-theme] :is(.bili-header__bar,.search-layout,.popular-container,.popular-video-container,.rank-list,.video-info-container,.video-toolbar-container,.video-desc-container,.video-pod,.video-pod__header,.bili-dyn-list__item,.bili-dyn-item,.space-main,.nav-bar,.relation-content,.history-list,.watchlater-list,.security-content,.security-right,.login-panel-popover,.bili-mini-mask .bili-mini-login) { background-color:var(--bs-bg)!important;color:var(--bs-ink)!important;border-color:var(--bs-border)!important; }
html[data-bilispeed-touch][data-bilispeed-theme] :is(.bili-video-card__info--tit,.video-title,.video-name,.title,.nickname,.up-name,.name,.nav-tabs__item,.video-pod__title,.security-list) { color:var(--bs-ink)!important; }
html[data-bilispeed-touch][data-bilispeed-theme] :is(.bili-video-card__info--author,.bili-video-card__info--date,.video-info-detail,.video-info-detail-list,.desc-info-text,.desc-info,.video-desc,.video-stat,.hint,.sub-title) { color:var(--bs-muted)!important; }
html[data-bilispeed-touch][data-bilispeed-theme] :is(.nav-search-input,.nav-search-content,.nav-search-btn,.nav-searchform,.search-input,.search-input-el,.search-input-wrap,.bili-dyn-publishing,.bili-tabs,.up-panel-container,.video-pod__list,.video-pod__item,.video-pod__item .simple-base-item,.cur-list,.list-box,.vui_pagenation--btn,.history-dlg) { background-color:var(--bs-surface)!important;color:var(--bs-ink)!important;border-color:var(--bs-border)!important; }
html[data-bilispeed-touch][data-bilispeed-theme] :is(#bilispeed-video-tabs,#bilispeed-episodes,.bilispeed-episode-list) {background:var(--bs-bg)!important;color:var(--bs-ink)!important;}
html[data-bilispeed-touch][data-bilispeed-theme] :is(#bilispeed-video-tabs button,#bilispeed-episodes a) {background:var(--bs-surface)!important;color:var(--bs-ink)!important;border-color:var(--bs-border)!important;}
html[data-bilispeed-touch][data-bilispeed-theme] :is(#bilispeed-video-tabs button[aria-selected=true],#bilispeed-episodes a[aria-current]) {color:var(--bs-accent)!important;}
html[data-bilispeed-touch][data-bilispeed-theme] :is(#bilispeed-video-tabs button:enabled:active,#bilispeed-episodes a:active,#bilispeed-episodes a[aria-current],#bilispeed-episodes button:enabled:active) {background:var(--bs-pressed)!important;color:var(--bs-accent)!important;}
html[data-bilispeed-touch][data-bilispeed-theme] #bilispeed-episodes .episode-group {background:var(--bs-surface)!important;}
html[data-bilispeed-touch][data-bilispeed-theme] #bilispeed-episodes :is(.episode-kind,.episode-count,.episode-index,time,p) {color:var(--bs-muted)!important;}
html[data-bilispeed-touch][data-bilispeed-theme] #bilispeed-episodes button {color:var(--bs-accent)!important;}
`;
            document.head.append(style);
        }
    }
    window.__BiliBrowserState=Object.freeze({position,restore,applyTheme,blocked,cancelRestore});
    document.addEventListener('DOMContentLoaded',()=>{applyTheme(window.__BILI_THEME__);position();},{once:true});
    applyTheme(window.__BILI_THEME__);
})();
