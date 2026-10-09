(function () {
    'use strict';
    // Only adapt the main page, never an embedded login/player document.
    if (window !== window.top || window.__BiliTouch || window.__BILI_TOUCH_ENABLED__ === false) return;
    const supported = ['www.bilibili.com', 'bilibili.com', 'search.bilibili.com', 't.bilibili.com',
        'space.bilibili.com', 'account.bilibili.com', 'passport.bilibili.com'];
    if (!supported.includes(location.hostname)) return;

    const attribute = 'data-bilispeed-touch';
    let refreshTimer = null;
    let suspended = !!window.__BILI_SPEED_SUSPENDED__;
    let previousPage = '';
    const ownElements = '#bilispeed-touch-controls, #bilispeed-seek-feedback, #bilispeed-video-tabs, #bilispeed-touch-style, #bilispeed-player-style';
    const transientPlayerElements = '.bpx-player-dm-wrap, .bpx-player-dm-container, .bpx-player-subtitle-wrap';
    const observation = { childList: true, subtree: true, attributes: true, attributeFilter: ['content'] };
    const stylesheet = `
#bilispeed-touch-controls, #bilispeed-video-tabs { display: none; }
@media (max-width: 1000px) {
    html[${attribute}], html[${attribute}] body, html[${attribute}] #app {
        min-width: 0 !important; width: 100vw !important; max-width: 100vw !important; margin: 0 !important;
    }
    html[${attribute}], html[${attribute}] body { overflow-x: hidden !important; }
    html[${attribute}] body { font-size: 14px; }
    html[${attribute}] .bili-header,
    html[${attribute}] .bili-header__bar {
        min-width: 0 !important; width: 100% !important; box-sizing: border-box;
    }
    html[${attribute}] .bili-header__bar {
        padding: 8px 12px !important; height: 56px !important;
        background: #fff !important; color: #18191c !important;
    }
    html[${attribute}] .bili-header .left-entry,
    html[${attribute}] .bili-header__banner,
    html[${attribute}] .bili-header .right-entry__main > :not(.header-avatar-wrap):not(.header-avatar-unlogin-wrap),
    html[${attribute}] .bili-header .right-entry > .upload,
    html[${attribute}] .fixed-sidenav-storage,
    html[${attribute}] .palette-button-wrap { display: none !important; }
    html[${attribute}] .bili-header .center-search-container {
        flex: 1 !important; min-width: 0 !important; max-width: none !important;
        width: auto !important; margin: 0 12px 0 0 !important;
    }
    html[${attribute}] .bili-header .right-entry,
    html[${attribute}] .bili-header .right-entry__main { flex: none !important; min-width: 0 !important; }
    html[${attribute}] .bili-header .right-entry { width: auto !important; }
    html[${attribute}] .bili-header .right-entry__loading { display: none !important; }
    html[${attribute}] .header-avatar-unlogin-wrap .login-panel-popover { display: none !important; }
    html[${attribute}] #biliMainFooter, html[${attribute}] .bili-footer { display: none !important; }
    html[${attribute}] .bili-header .header-avatar-wrap,
    html[${attribute}] .bili-header .header-avatar-unlogin-wrap {
        display: flex !important; margin: 0 !important; padding: 0 !important; min-width: 44px;
    }
    html[${attribute}] #biliMainHeader { min-height: 56px !important; }
    html[${attribute}][data-bilispeed-page="video"] #biliMainHeader { display: none !important; }
    html[${attribute}] .nav-search-input { font-size: 16px !important; }
    html[${attribute}] .bili-header__channel {
        width: 100% !important; height: auto !important; min-width: 0 !important; padding: 6px 12px 10px !important;
        box-sizing: border-box; overflow-x: auto; gap: 16px !important;
    }
    html[${attribute}] .bili-header__channel .channel-icons,
    html[${attribute}] .bili-header__channel .channel-items__right { display: none !important; }
    html[${attribute}] .bili-header__channel .right-channel-container { padding: 0 !important; }
    html[${attribute}] .bili-header__channel .channel-items__left {
        display: flex !important; flex-wrap: nowrap !important; gap: 8px !important;
    }
    html[${attribute}] .bili-header__channel .channel-link { min-width: 60px; min-height: 36px; }

    /* Keep the site's own cards, links, recommendation and lazy loading. */
    html[${attribute}] .bili-feed4-layout,
    html[${attribute}] .feed2,
    html[${attribute}] .recommended-container_floor-aside {
        width: 100% !important; min-width: 0 !important; max-width: none !important;
        margin: 0 !important; box-sizing: border-box;
    }
    html[${attribute}] .bili-feed4-layout { padding: 0 12px !important; }
    html[${attribute}] .recommended-container_floor-aside > .container {
        display: grid !important; width: 100% !important; min-width: 0 !important;
        grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
        grid-auto-rows: auto !important; gap: 18px 10px !important; padding: 0 !important;
    }
    html[${attribute}] .recommended-swipe {
        grid-column: 1 / -1 !important; grid-row: auto !important;
        min-width: 0 !important; height: auto !important; aspect-ratio: 16 / 8;
    }
    html[${attribute}] .feed-card,
    html[${attribute}] .bili-feed-card,
    html[${attribute}] .bili-video-card { min-width: 0 !important; width: 100% !important; }
    html[${attribute}] .bili-video-card__info--tit { font-size: 14px !important; line-height: 20px !important; }
    html[${attribute}] .bili-video-card__info--bottom { font-size: 12px !important; }
    html[${attribute}] .feed-roll-btn { position: static !important; margin: 18px 0 !important; }
    html[${attribute}] .feed-roll-btn button { width: 100% !important; min-height: 44px; }

    /* Stack desktop video columns and put the player before the title. */
    html[${attribute}] #mirror-vdcon {
        display: flex !important; flex-direction: column !important;
        min-width: 0 !important; max-width: none !important; width: 100% !important;
        margin: 0 !important; padding: 0 !important; gap: 16px !important;
    }
    html[${attribute}] #mirror-vdcon .left-container,
    html[${attribute}] #mirror-vdcon .right-container,
    html[${attribute}] #mirror-vdcon .right-container-inner {
        position: static !important; width: 100% !important; min-width: 0 !important;
        max-width: none !important; margin: 0 !important; padding: 0 !important;
    }
    html[${attribute}] #mirror-vdcon .left-container {
        display: flex !important; flex-direction: column !important;
    }
    html[${attribute}] #mirror-vdcon .left-container > * { order: 5; }
    html[${attribute}] #mirror-vdcon #playerWrap {
        order: 0; width: 100% !important; min-width: 0 !important;
        height: calc(var(--bilispeed-video-height, 56.25vw) + var(--bilispeed-sending-height, 46px)) !important;
        max-height: none !important;
        margin: 0 !important; flex: none !important;
    }
    html[${attribute}] #mirror-vdcon .video-info-container { order: 1; }
    html[${attribute}] #mirror-vdcon .video-toolbar-container { order: 2; }
    html[${attribute}] #mirror-vdcon #bilispeed-video-tabs { order: 3; }
    html[${attribute}] #mirror-vdcon .video-desc-container { order: 4; }
    html[${attribute}] #mirror-vdcon .video-info-container,
    html[${attribute}] #mirror-vdcon .video-toolbar-container,
    html[${attribute}] #mirror-vdcon .video-desc-container,
    html[${attribute}] #mirror-vdcon .video-tag-container,
    html[${attribute}] #mirror-vdcon .up-panel-container,
    html[${attribute}] #mirror-vdcon .video-pod-above-modules,
    html[${attribute}] #mirror-vdcon .rcmd-tab,
    html[${attribute}] #commentapp { padding: 10px 12px !important; box-sizing: border-box; }
    html[${attribute}] #mirror-vdcon .video-title {
        height: auto !important; white-space: normal !important; font-size: 17px !important; line-height: 25px !important;
    }
    html[${attribute}] #mirror-vdcon .video-info-detail-list { flex-wrap: wrap; height: auto !important; }
    html[${attribute}] #mirror-vdcon .video-toolbar-left { width: 100% !important; justify-content: space-between; }
    html[${attribute}] #mirror-vdcon .video-toolbar-left-main {
        display: flex !important; width: 100% !important; min-width: 0 !important; justify-content: space-between;
    }
    html[${attribute}] #mirror-vdcon .toolbar-left-item-wrap { flex: 1; min-width: 0; }
    html[${attribute}] #mirror-vdcon .video-toolbar-left-item { width: auto !important; min-width: 0 !important; }
    html[${attribute}] #mirror-vdcon .video-toolbar-item-text { font-size: 12px !important; }
    html[${attribute}] #mirror-vdcon .video-info-container { margin: 0 !important; min-height: 0 !important; }
    html[${attribute}] #mirror-vdcon .video-toolbar-right { display: none !important; }
    html[${attribute}] #mirror-vdcon .video-toolbar-left > * { min-height: 40px; margin-right: 8px !important; }
    html[${attribute}] #bilibili-player { width: 100% !important; height: 100% !important; }
    html[${attribute}] .bpx-player-container:not(.bpx-state-fullscreen):not(.bpx-state-web) { width: 100% !important; height: 100% !important; }
    html[${attribute}] .bpx-player-sending-bar { min-width: 0 !important; }
    html[${attribute}] .bpx-player-video-info { display: none !important; }
    html[${attribute}] #bilispeed-video-tabs { display: flex; border-bottom: 1px solid #eee; }
    html[${attribute}] #bilispeed-video-tabs button {
        flex: 1; min-height: 44px; border: 0; background: #fff; color: #61666d; font-size: 15px;
    }
    html[${attribute}] #bilispeed-video-tabs button:focus-visible { outline: 2px solid #e8557f; }
    html[${attribute}] #bilispeed-video-tabs button:disabled { color: #9499a0; }
    html[${attribute}] #bilispeed-video-tabs button:enabled:active { background: #fff0f5; color: #e8557f; }

    html[${attribute}] .search-layout,
    html[${attribute}] .search-header,
    html[${attribute}] .search-content,
    html[${attribute}] .search-page-wrapper,
    html[${attribute}] .search-page,
    html[${attribute}] .search-layout .i_wrapper {
        width: 100% !important; min-width: 0 !important; max-width: none !important; box-sizing: border-box;
    }
    html[${attribute}] .search-layout .i_wrapper { padding-left: 12px !important; padding-right: 12px !important; }
    html[${attribute}] .search-layout .video-list {
        display: grid !important; grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
        width: 100% !important; margin: 0 !important; gap: 16px 10px !important;
    }
    html[${attribute}] .search-layout .video-list-item {
        display: block !important; width: 100% !important; min-width: 0 !important;
        max-width: none !important; padding: 0 !important;
    }
    html[${attribute}] .search-layout .search-input { width: 100% !important; min-width: 0 !important; }
    html[${attribute}] .search-input-el { font-size: 16px !important; }
    html[${attribute}] .search-layout .search-tabs,
    html[${attribute}] .search-layout .search-filter { overflow-x: auto; max-width: 100%; }
    html[${attribute}] .popular-container { width: 100% !important; min-width: 0 !important; padding: 12px !important; box-sizing: border-box; }
    html[${attribute}] .popular-container .popular-video-container,
    html[${attribute}] .popular-container .flow-loader,
    html[${attribute}] .popular-container .card-list { width: 100% !important; min-width: 0 !important; display: block !important; }
    html[${attribute}] .popular-container .nav-tabs { width: 100% !important; display: flex; overflow-x: auto; }
    html[${attribute}] .popular-container .nav-tabs > * { flex: none; min-width: 100px; }
    html[${attribute}] .popular-container .video-card {
        display: flex !important; width: 100% !important; min-width: 0 !important; margin: 0 0 16px !important; gap: 10px;
    }
    html[${attribute}] .popular-container .video-card__content { flex: 0 0 44%; width: 44% !important; height: auto !important; aspect-ratio: 16 / 9; }
    html[${attribute}] .popular-container .video-card__info { flex: 1; width: auto !important; min-width: 0 !important; padding: 0 !important; }
    html[${attribute}] .popular-container .video-card__info .video-name { font-size: 14px !important; line-height: 20px !important; }
    html[${attribute}] .bili-dyn-home--member,
    html[${attribute}] .bili-dyn-home--visitor { display: block !important; width: 100% !important; min-width: 0 !important; padding: 12px !important; box-sizing: border-box; }
    html[${attribute}] .bili-dyn-home__left,
    html[${attribute}] .bili-dyn-home__right { display: none !important; }
    html[${attribute}] .bili-dyn-home__center { width: 100% !important; min-width: 0 !important; margin: 0 !important; }
    html[${attribute}] .bili-dyn-home--visitor > .left,
    html[${attribute}] .bili-dyn-home--visitor > .right,
    html[${attribute}] .bili-dyn-sidebar { display: none !important; }
    html[${attribute}] .bili-dyn-home--visitor > :not(.left):not(.right),
    html[${attribute}] .bili-dyn-list, html[${attribute}] .bili-dyn-item { width: 100% !important; min-width: 0 !important; box-sizing: border-box; }

    /* Use the official password/SMS form on a phone, without the desktop QR column. */
    html[${attribute}] .login_wp,
    html[${attribute}] .login__main,
    html[${attribute}] .login__main .main__right,
    html[${attribute}] .login-pwd,
    html[${attribute}] .login-protocol {
        width: 100% !important; min-width: 0 !important; max-width: 100% !important; box-sizing: border-box;
    }
    html[${attribute}] .login_wp { padding: 24px 16px !important; margin: 0 !important; }
    html[${attribute}] .login__main { display: block !important; }
    html[${attribute}] .login__main .main__left,
    html[${attribute}] .login__main .main__middle-line,
    html[${attribute}] #app > .top-header { display: none !important; }
    html[${attribute}] .login__main .main__right { padding: 0 !important; }
    html[${attribute}] .login-pwd .tab__form,
    html[${attribute}] .login-pwd .btn_wp,
    html[${attribute}] .login-pwd .form__item { width: 100% !important; box-sizing: border-box; }
    html[${attribute}] .login-pwd .form__item { display: flex !important; align-items: center; }
    html[${attribute}] .login-pwd .form__item input { flex: 1; width: 0 !important; min-width: 0; font-size: 16px; }
    html[${attribute}] .login-protocol { font-size: 12px; padding-top: 20px; }
}
`;

    function pageKind() {
        if (location.hostname === 'search.bilibili.com') return 'search';
        if (location.hostname === 't.bilibili.com') return 'dynamic';
        if (location.pathname.startsWith('/video/')) return 'video';
        if (location.pathname.startsWith('/v/popular')) return 'popular';
        if (location.pathname === '/' || location.pathname === '') return 'home';
        return 'other';
    }

    function videoTabs() {
        const toolbar = document.querySelector('#mirror-vdcon .video-toolbar-container');
        if (!toolbar) return;
        let tabs = document.getElementById('bilispeed-video-tabs');
        if (!tabs) {
            tabs = document.createElement('nav');
            tabs.id = 'bilispeed-video-tabs';
            tabs.setAttribute('aria-label', '视频内容');
            [['简介', '#v_desc'], ['评论', '#commentapp'], ['选集', '.video-pod-above-modules']].forEach(([label, selector]) => {
                const button = document.createElement('button');
                button.type = 'button';
                button.textContent = label;
                button.dataset.section = selector;
                button.addEventListener('click', () => {
                    const section = document.querySelector(selector);
                    if (section) section.scrollIntoView({
                        behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start'
                    });
                });
                tabs.appendChild(button);
            });
        }
        // Comments and collections can mount after the toolbar. Keep their
        // actions in sync when the official page replaces or loads a section.
        tabs.querySelectorAll('button').forEach(button => {
            const available = !!document.querySelector(button.dataset.section);
            if (button.disabled !== !available) button.disabled = !available;
            const title = available ? '查看' + button.textContent : '暂无' + button.textContent;
            if (button.title !== title) button.title = title;
        });
        if (tabs.previousElementSibling !== toolbar) toolbar.after(tabs);
    }


    function refresh() {
        if (suspended) return;
        const html = document.documentElement;
        if (!html || !document.head) return;
        let viewport = document.querySelector('meta[name="viewport"]');
        if (!viewport) {
            viewport = document.createElement('meta');
            viewport.name = 'viewport';
            document.head.appendChild(viewport);
        }
        const content = 'width=device-width, initial-scale=1';
        if (viewport.content !== content) viewport.content = content;
        let style = document.getElementById('bilispeed-touch-style');
        if (!style) {
            style = document.createElement('style');
            style.id = 'bilispeed-touch-style';
            style.textContent = stylesheet;
            document.head.appendChild(style);
        }
        if (!html.hasAttribute(attribute)) html.setAttribute(attribute, '');
        const page = pageKind();
        if (page !== previousPage) {
            previousPage = page;
            html.setAttribute('data-bilispeed-page', page);
            // Give the official player a chance to resize its video/danmaku canvas.
            setTimeout(() => window.dispatchEvent(new Event('resize')), 50);
        }
        if (page === 'video') videoTabs();
        if (window.__BiliTouchPlayer) window.__BiliTouchPlayer.refresh();
    }

    function schedule() {
        if (suspended || refreshTimer !== null) return;
        refreshTimer = setTimeout(() => { refreshTimer = null; refresh(); }, 100);
    }

    function requiresRefresh(record) {
        const target = record.target.nodeType === 1 ? record.target : record.target.parentElement;
        // Our progress labels and the site's animated overlays don't change the
        // page layout. Observing them would rescan the player on every update.
        if (target && target.closest(ownElements + ', ' + transientPlayerElements)) return false;
        if (record.type === 'attributes') return target && target.matches('meta[name="viewport"]');
        return [...record.addedNodes, ...record.removedNodes].some(node =>
            node.nodeType === 1 && !node.matches(ownElements + ', ' + transientPlayerElements));
    }

    function setSuspended(value) {
        const next = !!value;
        if (next === suspended) return;
        suspended = next;
        clearTimeout(refreshTimer);
        refreshTimer = null;
        observer.disconnect();
        if (window.__BiliTouchPlayer) window.__BiliTouchPlayer.setSuspended(suspended);
        if (!suspended) { observer.observe(document, observation); refresh(); }
    }

    Object.defineProperty(window, '__BiliTouch', { value: Object.freeze({ refresh, setSuspended }), configurable: false });
    const observer = new MutationObserver(records => { if (records.some(requiresRefresh)) schedule(); });
    if (!suspended) observer.observe(document, observation);
    document.addEventListener('DOMContentLoaded', refresh, { once: true });
    window.addEventListener('popstate', schedule);
    window.addEventListener('hashchange', schedule);
    window.addEventListener('pageshow', schedule);
    refresh();
})();
