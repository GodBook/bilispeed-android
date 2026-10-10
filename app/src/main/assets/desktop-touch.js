(function () {
    'use strict';
    // Only adapt the main page, never an embedded login/player document.
    if (window !== window.top || window.__BiliTouch || window.__BILI_TOUCH_ENABLED__ === false) return;
    if (location.hostname === 'www.bilibili.com' && location.pathname === '/__bilispeed__/me') return;
    const supported = ['www.bilibili.com', 'bilibili.com', 'search.bilibili.com', 't.bilibili.com',
        'space.bilibili.com', 'account.bilibili.com', 'passport.bilibili.com'];
    if (!supported.includes(location.hostname)) return;

    const attribute = 'data-bilispeed-touch';
    let refreshTimer = null;
    let suspended = !!window.__BILI_SPEED_SUSPENDED__;
    let previousPage = '';
    let hydrationStarted = 0;
    let hydrationTimer = null;
    let bootstrapFailed = false;
    let previousSpaceRoute = '';
    const managedDanmaku = new WeakSet();
    const commentRoots = new Map();
    const waitingCommentTags = new Set();
    let commentTimer = null;
    const ownElements = '#bilispeed-touch-controls, #bilispeed-seek-feedback, #bilispeed-video-tabs, #bilispeed-touch-style, #bilispeed-player-style, #bilispeed-episodes-style, #bilispeed-episodes, #bilispeed-folder-toggle, [data-bilispeed-image], [data-bilispeed-danmaku-header]';
    const transientPlayerElements = '.bpx-player-dm-wrap, .bpx-player-dm-container, .bpx-player-subtitle-wrap';
    const observation = { childList: true, subtree: true, attributes: true, attributeFilter: ['content', 'data-server-rendered'] };
    const stylesheet = `
#bilispeed-touch-controls, #bilispeed-video-tabs { display: none; }
html[${attribute}] .bpx-player-dm-setting-wrap[data-bilispeed-danmaku-open] { display: block !important; }
html[${attribute}] .bpx-player-dm-setting-wrap[data-bilispeed-danmaku-closed] { display: none !important; }
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
    html[${attribute}] .bili-video-card__stats { gap: 8px; font-size: 11px !important; }
    html[${attribute}] .bili-video-card__stats--left { min-width: 0; overflow: hidden; }
    html[${attribute}] .bili-video-card__stats--item { min-width: 0; white-space: nowrap; }
    html[${attribute}] .bili-video-card__stats--item:nth-child(n + 2) { display: none !important; }
    html[${attribute}] .bili-video-card__stats--icon { width: 14px !important; height: 14px !important; }
    html[${attribute}] .bili-video-card__stats__duration {
        flex: none; max-width: 48%; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
    }
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
    /* The desktop player switches data-screen to mini when comments scroll into
       view. 100% height on its fixed box means viewport height and obscures the
       comments. Keep it in the reserved document slot outside real fullscreen. */
    html[${attribute}]:not([data-bilispeed-fullscreen]) .bpx-player-container:not([data-screen="full"]):not([data-screen="web"]):not(.bpx-state-fullscreen):not(.bpx-state-web) {
        position: relative !important; inset: auto !important; transform: none !important;
        width: 100% !important; height: 100% !important; min-width: 0 !important; max-width: 100% !important;
    }
    html[${attribute}]:not([data-bilispeed-fullscreen]) .bpx-player-container[data-screen="mini"] :is(.bpx-player-mini-warp, .bpx-player-mini-wrap, .bpx-player-mini-close) { display: none !important; }
    html[${attribute}] .bpx-player-sending-bar { min-width: 0 !important; }
    html[${attribute}] .bpx-player-sending-bar { padding: 0 8px !important; box-sizing: border-box; }
    html[${attribute}] .bpx-player-sending-bar .bpx-player-video-info { display: none !important; }
    html[${attribute}] .bpx-player-dm-input { flex: 1; min-width: 0 !important; width: auto !important; }
    html[${attribute}] .bpx-player-dm-input input { min-width: 0 !important; width: 100% !important; }
    /* The official popup is anchored outside its narrow desktop trigger. Keep
       it in the viewport and preserve the site's own sliders and checkboxes. */
    html[${attribute}] .bpx-player-dm-setting-wrap {
        position: fixed !important; left: 8px !important; right: 8px !important; top: 8px !important; bottom: auto !important;
        width: auto !important; height: auto !important; min-width: 0 !important; max-width: 420px !important; margin: 0 auto !important;
        max-height: calc(100dvh - 24px) !important; overflow: auto !important; overscroll-behavior: contain;
        transform: none !important; box-sizing: border-box !important; z-index: 100010 !important;
    }
    html[${attribute}] .bpx-player-dm-setting-wrap * { box-sizing: border-box; max-width: 100%; }
    html[${attribute}] .bpx-player-dm-setting-wrap .bpx-player-dm-setting-box {
        position: relative !important; width: 100% !important; height: auto !important; right: auto !important; bottom: auto !important;
    }
    html[${attribute}] .bpx-player-dm-setting-wrap :is(.bui-panel-wrap, .bui-panel-move) {
        display: block !important; width: 100% !important; height: auto !important;
        transform: none !important; margin-left: 0 !important; left: auto !important;
    }
    html[${attribute}] .bpx-player-dm-setting-wrap .bui-panel-item {
        display: none !important; width: 100% !important; height: auto !important; float: none !important; position: relative !important;
    }
    html[${attribute}] .bpx-player-dm-setting-wrap .bui-panel-item-active { display: block !important; }
    html[${attribute}] [data-bilispeed-danmaku-header] {
        display: flex; align-items: center; justify-content: space-between; position: sticky; top: 0; z-index: 2;
        padding: 4px 12px; color: #fff; background: #202124; border-bottom: 1px solid #555;
        font: 15px sans-serif; box-sizing: border-box;
    }
    html[${attribute}] [data-bilispeed-danmaku-header] button {
        border: 0; border-radius: 6px; min-width: 52px; min-height: 44px; font: 14px sans-serif;
        background: transparent; color: #ff91b2; touch-action: manipulation;
    }
    html[${attribute}] .bpx-player-dm-setting-wrap :is(.bpx-player-dm-setting-left, .bpx-player-dm-setting-right) {
        width: 100% !important; height: auto !important; min-width: 0 !important; padding: 16px !important; white-space: normal !important;
    }
    html[${attribute}] .bpx-player-dm-setting-wrap .bpx-player-dm-setting-left-radio { flex-wrap: wrap; gap: 12px; }
    html[${attribute}] .bpx-player-dm-setting-wrap .bpx-player-dm-setting-left-block-content {
        flex-wrap: wrap; height: auto !important; overflow: visible !important; gap: 10px;
    }
    html[${attribute}] .bpx-player-dm-setting-wrap .bpx-player-block-filter-type { min-height: 44px; margin: 0 !important; }
    html[${attribute}] .bpx-player-dm-setting-wrap .bpx-player-dm-setting-left-block-word { gap: 8px; }
    html[${attribute}] .bpx-player-dm-setting-wrap :is(.bpx-player-dm-setting-left-block-add, .bpx-player-dm-setting-left-block-sync) { flex: 1; min-height: 36px; line-height: 36px; }
    html[${attribute}] .bpx-player-dm-setting-wrap :is(.bpx-player-dm-setting-left-area, .bpx-player-dm-setting-left-fontsize, .bpx-player-dm-setting-left-opacity, .bpx-player-dm-setting-left-speedplus) {
        min-width: 0 !important; min-height: 40px; height: auto !important; margin-bottom: 8px !important;
    }
    html[${attribute}] .bpx-player-dm-setting-wrap :is(.bpx-player-dm-setting-left-area-content, .bpx-player-dm-setting-left-fontsize-content, .bpx-player-dm-setting-left-opacity-content, .bpx-player-dm-setting-left-speedplus-content) {
        flex: 1; width: 0 !important; min-width: 0 !important;
    }
    html[${attribute}] .bpx-player-dm-setting-wrap .bui-slider { width: 100% !important; min-width: 0 !important; }
    html[${attribute}] .bpx-player-dm-setting-wrap input[type="range"] { min-width: 0 !important; max-width: 100%; }
    html[${attribute}] .bpx-player-dm-setting-wrap .bpx-player-dm-setting-left-more {
        top: 0 !important; padding-top: 0 !important; min-height: 44px; line-height: 44px;
    }
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
    /* Current search uses Bootstrap-like percentage columns without the old
       video-list-item class. Reset the grid children, not just the card inside. */
    html[${attribute}] .search-layout .video-list > *,
    html[${attribute}] .search-layout .video-list-item {
        display: block !important; width: 100% !important; min-width: 0 !important;
        max-width: 100% !important; padding: 0 !important; margin: 0 !important;
        flex: none !important; box-sizing: border-box;
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
    html[${attribute}][data-bilispeed-page="popular"] .popular-container .video-card {
        display: flex !important; width: 100% !important; min-width: 0 !important; max-width: 100% !important;
        height: auto !important; min-height: 96px; margin: 0 0 16px !important; gap: 10px; box-sizing: border-box;
    }
    html[${attribute}] .popular-container .video-card__content {
        flex: 0 0 44%; width: 44% !important; height: auto !important; aspect-ratio: 16 / 9;
        align-self: flex-start; margin: 0 !important;
    }
    html[${attribute}] .popular-container .video-card__info { flex: 1; width: auto !important; min-width: 0 !important; padding: 0 !important; }
    html[${attribute}] .popular-container .video-card__info .video-name { font-size: 14px !important; line-height: 20px !important; }
    html[${attribute}] .popular-container .video-stat { flex-wrap: wrap; gap: 4px 8px; }
    html[${attribute}] .popular-container .nav-tabs { height: 68px !important; }
    html[${attribute}] .popular-container .nav-tabs > .nav-tabs__item {
        flex: 1 1 0; min-width: 0; margin: 0 !important; padding: 6px 2px !important; justify-content: center;
    }
    html[${attribute}] .popular-container .nav-tabs__item > div { flex-direction: column; gap: 4px; }
    html[${attribute}] .popular-container .nav-tabs__item > div > img { width: 24px; height: 24px; margin: 0 !important; }
    html[${attribute}] .popular-container .nav-tabs__item > div > span { font-size: clamp(11px, 3vw, 13px); white-space: nowrap; }
    html[${attribute}] .popular-container .rank-list { display: block !important; }
    html[${attribute}][data-bilispeed-page="popular"] .popular-container .rank-item { width: 100% !important; }
    html[${attribute}] .popular-container .rank-item .content .img {
        width: 44% !important; height: auto !important; aspect-ratio: 16 / 9; align-self: flex-start;
    }
    html[${attribute}] .popular-container .rank-item .content .info {
        flex: 1; min-width: 0 !important; height: auto !important; min-height: 96px; padding: 0 !important;
    }
    html[${attribute}] .popular-container .rank-list.pgc-list .rank-item .content .img { width: 30% !important; aspect-ratio: 3 / 4; }
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

    /* The signed-in feed uses plain aside/main children, unlike the visitor
       feed. Adapt the actual content column as well as the outer shell. */
    html[${attribute}] .bili-dyn-home--member > :is(.left, .right) { display: none !important; }
    html[${attribute}] :is(.bili-dyn-home--member, .bili-dyn-home--visitor) > main,
    html[${attribute}] :is(.bili-dyn-home--member, .bili-dyn-home--visitor) > main > section,
    html[${attribute}] :is(.bili-dyn-detail, .bili-dyn-details, .bili-dyn-publishing, .bili-dyn-up-list, .bili-dyn-list-tabs) {
        width: 100% !important; min-width: 0 !important; max-width: 100% !important; margin-left: 0 !important;
        margin-right: 0 !important; box-sizing: border-box;
    }
    html[${attribute}] .bili-dyn-up-list { overflow-x: auto; }
    html[${attribute}] .bili-dyn-list-tabs { gap: 0; overflow-x: auto; }
    html[${attribute}] .bili-dyn-list-tabs__list { flex: none; min-height: 44px; }
    html[${attribute}][data-bilispeed-page="dynamic"] #app > .content { width: 100% !important; min-width: 0 !important; padding: 12px !important; margin: 0 !important; box-sizing: border-box; }
    html[${attribute}][data-bilispeed-page="dynamic"] #app > .content > :is(.card, .bili-tabs) { width: 100% !important; min-width: 0 !important; max-width: 100%; }
    html[${attribute}] .bili-dyn-publishing__title__input { width: 0 !important; min-width: 0; flex: 1; }
    html[${attribute}] .bili-dyn-publishing__action { flex-wrap: wrap; height: auto !important; gap: 8px; }
    html[${attribute}] .bili-dyn-item__main { padding: 0 !important; min-width: 0; }
    html[${attribute}] .bili-dyn-item__avatar { top: 12px !important; left: 12px !important; width: 40px !important; height: 40px !important; padding: 0 !important; }
    html[${attribute}] .bili-dyn-item__avatar :is(.bili-dyn-avatar, .b-avatar) { width: 40px !important; height: 40px !important; }
    html[${attribute}] .bili-dyn-item__header { width: auto !important; min-height: 64px; height: auto !important; margin: 0 !important; padding: 12px 44px 8px 64px !important; box-sizing: border-box; }
    html[${attribute}] .bili-dyn-item__body { width: auto !important; min-width: 0; margin: 0 !important; padding: 0 12px !important; }
    html[${attribute}] .bili-dyn-item__footer { display: flex !important; width: auto !important; margin: 0 !important; padding: 0 12px !important; }
    html[${attribute}] .bili-dyn-item__action { flex: 1; min-width: 0; width: auto !important; }
    html[${attribute}] .bili-dyn-item__action > * { width: 100% !important; min-height: 44px; }
    html[${attribute}] :is(.bili-dyn-content, .bili-dyn-content__orig, .bili-dyn-content__orig__major, .bili-dyn-content__orig__desc, .bili-dyn-content__orig__topic, .bili-dyn-content__repost) {
        min-width: 0 !important; max-width: 100% !important; box-sizing: border-box; overflow-wrap: anywhere;
    }
    html[${attribute}] .bili-dyn-card-video { display: flex !important; width: 100% !important; min-width: 0 !important; height: auto !important; min-height: 100px; }
    html[${attribute}] .bili-dyn-card-video__header { flex: 0 0 42%; width: 42% !important; height: auto !important; min-height: 100px; }
    html[${attribute}] .bili-dyn-card-video__cover { width: 100% !important; height: 100% !important; }
    html[${attribute}] .bili-dyn-card-video__body { flex: 1; min-width: 0; width: auto !important; height: auto !important; padding: 10px !important; }
    html[${attribute}] .bili-dyn-card-video__title { font-size: 14px !important; line-height: 20px !important; -webkit-line-clamp: 2 !important; height: auto !important; }
    html[${attribute}] .bili-dyn-card-video__desc { display: none !important; }
    html[${attribute}] .bili-dyn-card-video__stat { position: static !important; flex-wrap: wrap; gap: 4px; margin-top: 8px; }
    html[${attribute}] .bili-dyn-card-video__stat__item { width: auto !important; margin-right: 8px !important; }
    html[${attribute}] :is(.bili-dyn-card-opus, .bili-dyn-card-article, .bili-dyn-card-common, .bili-dyn-card-reserve, .bili-dyn-card-vote, .bili-album) { max-width: 100% !important; min-width: 0 !important; box-sizing: border-box; }
    html[${attribute}] .bili-album__preview { max-width: 100% !important; }
    html[${attribute}] .bili-album__preview.grid3 { width: 100% !important; grid-template-columns: repeat(3, minmax(0, 1fr)) !important; }
    html[${attribute}] .bili-album__preview.grid2 { width: min(100%, 268px) !important; grid-template-columns: repeat(2, minmax(0, 1fr)) !important; }
    html[${attribute}] :is(.bili-album__preview.grid3, .bili-album__preview.grid2) .bili-album__preview__picture { width: 100% !important; height: auto !important; aspect-ratio: 1; }
    html[${attribute}] .bili-album__preview__picture { max-width: 100% !important; }
    html[${attribute}] .bili-album__watch__control { flex-wrap: wrap; height: auto !important; }
    html[${attribute}] .bili-album__watch__control__option { min-height: 36px; padding: 0 10px !important; }

    /* History and watch-later are separate desktop applications. Their current
       routes redirect to /history and /watchlater/list, and use scoped styles. */
    html[${attribute}] :is(.history-record, .watchlater-list) {
        width: 100% !important; min-width: 0 !important; max-width: 100% !important;
        margin: 0 !important; padding: 16px 12px 24px !important; box-sizing: border-box;
    }
    html[${attribute}] :is(.history-record, .watchlater-list) :is(.main-head, .watchlater-list-title) {
        display: flex; flex-wrap: wrap; gap: 8px 12px; width: 100% !important; min-width: 0 !important; max-width: 100% !important; height: auto !important; margin: 0 0 12px !important; padding: 0 !important;
    }
    html[${attribute}] .history-record .main-title { margin: 0 !important; font-size: 22px !important; min-height: 40px; }
    html[${attribute}] .history-record .main-actions { gap: 12px; margin-left: auto; min-height: 40px; }
    html[${attribute}] .watchlater-list-title-left { font-size: 22px !important; }
    html[${attribute}] .watchlater-list-title-right { margin-left: auto; }
    html[${attribute}] :is(.history-record, .watchlater-list) :is(.main-breadcrumbs, .breadcrumbs, .breadcrumbs__top, .breadcrumbs__bottom, .main-content,
        .history-list, .history-timeline, .history-timeline-item, .history-section, .watchlater-list-nav, .watchlater-list-slim, .breadcrums-nav, .list-header, .list-header-main, .list-header-extra, .watchlater-list-container) {
        width: 100% !important; min-width: 0 !important; max-width: 100% !important; height: auto !important;
        margin-left: 0 !important; margin-right: 0 !important; padding-left: 0 !important; padding-right: 0 !important; box-sizing: border-box;
    }
    html[${attribute}] :is(.history-record, .watchlater-list) :is(.main-breadcrumbs, .watchlater-list-nav) { position: static !important; }
    html[${attribute}] :is(.history-record, .watchlater-list) .fixed-nav-shim { display: none !important; }
    html[${attribute}] .history-record :is(.breadcrumbs__top, .breadcrumbs__top > .left, .tabs, .breadcrumbs__top > .right, .filters, .filter-item, .radio-filter),
    html[${attribute}] .watchlater-list :is(.list-header-main, .list-header-filter, .list-header-options, .list-header-extra) {
        flex-wrap: wrap; min-width: 0 !important; max-width: 100% !important; height: auto !important; gap: 8px;
    }
    html[${attribute}] .history-record .breadcrumbs__top > :is(.left, .right),
    html[${attribute}] .watchlater-list :is(.list-header-filter, .list-header-options) { width: 100% !important; margin: 0 !important; }
    html[${attribute}] .history-record .radio-tabs { gap: 0 !important; }
    html[${attribute}] .history-record .radio-tabs__item { margin: 0 !important; padding: 0 10px !important; min-height: 44px; }
    html[${attribute}] :is(.history-record, .watchlater-list) :is(.vui_button, .radio-filter__item, .lists-view-mode__action, .list-header-filter__btn, .list-header-filter__more, .watchlater-list-title-sort) {
        min-height: 40px; min-width: 40px; box-sizing: border-box; touch-action: manipulation;
    }
    html[${attribute}] :is(.history-record, .watchlater-list) .search-bar {
        flex: 1 1 100%; width: 100% !important; height: 40px !important; --search-bar-side-padding: 12px;
        --search-bar-bg: var(--graph_bg_regular, #f1f2f3); box-sizing: border-box;
    }
    html[${attribute}] :is(.history-record, .watchlater-list) .search-bar .input-wrap { flex: 1; min-width: 0; max-width: none !important; }
    html[${attribute}] :is(.history-record, .watchlater-list) .search-bar-input { width: 100% !important; min-width: 0; font-size: 16px; }
    html[${attribute}] .history-record .history-timeline-item { display: block !important; }
    html[${attribute}] .history-record .history-timeline-label { position: static !important; width: 100% !important; height: auto !important; margin: 20px 0 12px !important; }
    html[${attribute}] .history-record :is(.section-label, .section-title) { position: static !important; width: auto !important; height: auto !important; margin: 0 !important; }
    html[${attribute}] .history-record :is(.history-timeline-anchor, .section-anchor-icon, .history-start, .history-end, .history-floating-nav) { display: none !important; }
    html[${attribute}] .history-record .section-cards { display: grid !important; grid-template-columns: repeat(2, minmax(0, 1fr)) !important; gap: 20px 10px !important; padding-bottom: 12px !important; }
    html[${attribute}] .history-record .section-cards.list-mode { grid-template-columns: minmax(0, 1fr) !important; }
    html[${attribute}] .history-record :is(.history-card, .history-skeleton-card, .history-card__left, .history-skeleton-card__left, .history-card__main, .history-skeleton-card__main) { width: 100% !important; min-width: 0 !important; max-width: 100% !important; box-sizing: border-box; }
    html[${attribute}] .history-record .history-card.list-mode { flex-wrap: wrap; }
    html[${attribute}] .history-record .history-card__right { flex: 1; min-width: 0; max-width: 100%; }
    html[${attribute}] .watchlater-list-grid { display: grid !important; grid-template-columns: repeat(2, minmax(0, 1fr)) !important; gap: 20px 10px !important; padding: 0 !important; }
    html[${attribute}] .watchlater-list-grid > * { width: 100% !important; min-width: 0 !important; max-width: 100% !important; margin: 0 !important; }
    html[${attribute}] .watchlater-list--vertical .watchlater-list-grid { grid-template-columns: minmax(0, 1fr) !important; }
    html[${attribute}] .watchlater-list :is(.video-card__wrap, .video-card__left, .video-card__right) { min-width: 0 !important; max-width: 100%; box-sizing: border-box; }
    html[${attribute}] .watchlater-list .video-card--grid .video-card__wrap { display: block !important; }
    html[${attribute}] .watchlater-list .video-card--grid :is(.video-card__left, .video-card__right) { width: 100% !important; margin-left: 0 !important; }
    html[${attribute}] .watchlater-list .video-card--list .video-card__left { flex: 0 0 42%; width: 42% !important; }
    html[${attribute}] .watchlater-list .video-card--list .video-card__right { flex: 1; width: auto !important; padding-left: 10px !important; }
    html[${attribute}] .watchlater-list-empty { width: 100% !important; min-width: 0 !important; margin: 24px 0 !important; padding: 24px 12px !important; box-sizing: border-box; text-align: center; }
    html[${attribute}] .watchlater-list-empty__img { margin-left: auto !important; margin-right: auto !important; }
    html[${attribute}] :is(.history-record, .watchlater-list) .fixed-side-menu { display: none !important; }

    /* Space is shared by favorites, uploads, dynamics, following and fans. */
    html[${attribute}][data-bilispeed-page="space"] :is(.space-main, .upinfo, .nav-bar__main) {
        width: 100% !important; min-width: 0 !important; max-width: 100% !important; margin: 0 !important; box-sizing: border-box;
    }
    html[${attribute}][data-bilispeed-page="space"] .space-main { padding: 16px 12px !important; }
    html[${attribute}][data-bilispeed-page="space"] :is(.space-home, .space-follow, .space-fans, .space-dynamic) { display: flex !important; flex-direction: column; width: 100% !important; min-width: 0 !important; gap: 16px; }
    html[${attribute}][data-bilispeed-page="space"] :is(.space-home, .space-dynamic) > :is(.content, .aside),
    html[${attribute}][data-bilispeed-page="space"] :is(.space-follow, .space-fans) > :is(.follow-aside, .relation-aside, .follow-main, .fans-main) { width: 100% !important; min-width: 0 !important; max-width: 100% !important; margin: 0 !important; padding: 0 !important; box-sizing: border-box; }
    html[${attribute}][data-bilispeed-page="space"] :is(.space-dynamic__content, .space-dynamic__aside, .space-dynamic-inner, .space-dynamic-inner > .content, .space-dynamic__top) { width: 100% !important; min-width: 0 !important; margin-left: 0 !important; margin-right: 0 !important; box-sizing: border-box; }
    html[${attribute}][data-bilispeed-page="space"] :is(.follow-aside, .relation-aside) { max-height: 160px; overflow-y: auto; }
    html[${attribute}][data-bilispeed-page="space"] :is(.follow-aside, .relation-aside) > .vui_collapse { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
    html[${attribute}][data-bilispeed-page="space"] :is(.follow-main-title, .relation-content, .relation-content-header, .relation-content-header__left, .relation-content-header__right) { width: 100% !important; min-width: 0 !important; height: auto !important; margin: 0 !important; padding-left: 0 !important; padding-right: 0 !important; box-sizing: border-box; }
    html[${attribute}][data-bilispeed-page="space"] .relation-content-header { flex-wrap: wrap; gap: 12px; }
    html[${attribute}][data-bilispeed-page="space"] .relation-content .items { display: grid !important; grid-template-columns: minmax(0, 1fr) !important; gap: 20px !important; }
    html[${attribute}][data-bilispeed-page="space"] .relation-card { width: 100% !important; min-width: 0 !important; height: auto !important; gap: 16px; }
    html[${attribute}][data-bilispeed-page="space"] .relation-card-info { flex: 1; min-width: 0 !important; width: auto !important; margin: 0 !important; }
    html[${attribute}][data-bilispeed-page="space"] .upinfo { padding: 12px !important; height: auto !important; gap: 12px; flex-wrap: wrap; }
    html[${attribute}][data-bilispeed-page="space"] :is(.upinfo__main, .upinfo-detail) { flex: 1; min-width: 0 !important; width: auto !important; }
    html[${attribute}][data-bilispeed-page="space"] .upinfo__main { gap: 12px !important; }
    html[${attribute}][data-bilispeed-page="space"] .upinfo-detail__top { flex-wrap: wrap; height: auto !important; gap: 4px 8px; }
    html[${attribute}][data-bilispeed-page="space"] .nickname { min-width: 0; max-width: 100%; overflow: hidden; text-overflow: ellipsis; }
    html[${attribute}][data-bilispeed-page="space"] .upinfo .operations { flex: 1 1 100%; height: auto !important; margin: 0 !important; flex-wrap: wrap; }
    html[${attribute}][data-bilispeed-page="space"] :is(.nav-bar, .nav-bar__main) { height: auto !important; position: static !important; }
    html[${attribute}][data-bilispeed-page="space"] .nav-bar__main { display: flex; flex-direction: column; padding: 0 12px !important; align-items: stretch; }
    html[${attribute}][data-bilispeed-page="space"] .nav-bar__main-left { display: flex; flex-direction: column; align-items: stretch; width: 100% !important; min-width: 0 !important; }
    html[${attribute}][data-bilispeed-page="space"] .nav-tab { flex: none; width: 100% !important; gap: 20px !important; overflow-x: auto; overscroll-behavior-x: contain; }
    html[${attribute}][data-bilispeed-page="space"] .nav-tab__item { flex: none; margin: 0 !important; min-height: 48px; }
    html[${attribute}][data-bilispeed-page="space"] :is(.nav-bar-search, .nav-search-input) { width: 100% !important; min-width: 0 !important; margin: 0 !important; box-sizing: border-box; }
    html[${attribute}][data-bilispeed-page="space"] .nav-bar-search { flex: none; margin-bottom: 8px !important; }
    html[${attribute}][data-bilispeed-page="space"] .nav-bar__main-right { width: 100% !important; padding: 8px 0 !important; }
    html[${attribute}][data-bilispeed-page="space"] .nav-statistics { width: 100% !important; justify-content: flex-start; gap: 20px; }
    html[${attribute}] .space-favlist { display: flex !important; flex-direction: column; width: 100% !important; min-width: 0 !important; margin: 0 !important; gap: 16px; }
    html[${attribute}] .space-favlist :is(.favlist-aside, .favlist-main) { width: 100% !important; min-width: 0 !important; max-width: 100% !important; margin: 0 !important; padding: 0 !important; box-sizing: border-box; }
    html[${attribute}] .space-favlist .favlist-aside { max-height: 240px; height: auto !important; overflow-y: auto; overscroll-behavior: contain; border-bottom: 1px solid var(--line_regular, #e3e5e7); }
    html[${attribute}] .space-favlist[data-bilispeed-folders]:not([data-bilispeed-folders-open]) .favlist-aside { display: none !important; }
    html[${attribute}] #bilispeed-folder-toggle {
        display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; min-height: 48px;
        padding: 10px 12px; border: 1px solid var(--line_regular, #e3e5e7); border-radius: 8px;
        background: var(--bg1, #fff); color: var(--text1, #18191c); font: 15px sans-serif; text-align: left; touch-action: manipulation;
    }
    html[${attribute}] #bilispeed-folder-toggle::after { content: '展开'; flex: none; color: var(--text2, #61666d); font-size: 13px; }
    html[${attribute}] #bilispeed-folder-toggle[aria-expanded="true"]::after { content: '收起'; }
    html[${attribute}] #bilispeed-folder-toggle:active { background: var(--graph_bg_regular, #f1f2f3); }
    html[${attribute}] .favlist-aside .vui_collapse { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); width: 100% !important; }
    html[${attribute}] .favlist-aside :is(.vui_sidebar, .fav-collapse, .vui_collapse_item, .fav-collapse-wrap, .fav-sortable-list) { width: 100% !important; min-width: 0; }
    html[${attribute}] .favlist-aside .vui_sidebar-item-title { flex: 1; min-width: 0; }
    html[${attribute}] .favlist-info { display: flex; gap: 12px; min-width: 0; height: auto !important; }
    html[${attribute}] .favlist-info-file-card { flex: 0 0 32%; width: 32% !important; height: auto !important; }
    html[${attribute}] .favlist-info :is(.folder-cover-card, .folder-cover-card__cover) { width: 100% !important; height: auto !important; aspect-ratio: 16 / 9; }
    html[${attribute}] .favlist-info-detail { flex: 1; min-width: 0 !important; width: auto !important; height: auto !important; padding: 0 !important; }
    html[${attribute}] :is(.favlist-info-detail__title, .favlist-info-detail__title-row, .favlist-info-detail__actions) { height: auto !important; flex-wrap: wrap; gap: 8px; }
    html[${attribute}] .favlist-info-detail__data { overflow-wrap: anywhere; }
    html[${attribute}] .favlist-info-detail__actions > .vui_button { flex: 1 1 0; min-width: 0 !important; width: auto !important; padding-left: 8px !important; padding-right: 8px !important; white-space: nowrap; }
    html[${attribute}] :is(.fav-list-main, .fav-list-header, .fav-list-header-filter, .fav-list-header-filter__left, .fav-list-header-filter__right) { width: 100% !important; min-width: 0 !important; max-width: 100% !important; height: auto !important; box-sizing: border-box; }
    html[${attribute}] .fav-list-header-filter { flex-wrap: wrap; gap: 12px; }
    html[${attribute}] .fav-list-header-filter__search { width: 100% !important; min-width: 0 !important; }
    html[${attribute}] .fav-list-header-filter__search .vui_input-wrapper { flex: 1; min-width: 0; }
    html[${attribute}] .fav-list-header .radio-filter { flex-wrap: wrap; gap: 8px; }
    html[${attribute}] .fav-list-main .items { display: grid !important; grid-template-columns: repeat(2, minmax(0, 1fr)) !important; gap: 20px 10px !important; width: 100% !important; }
    html[${attribute}] .fav-list-main .items__item { width: 100% !important; min-width: 0 !important; margin: 0 !important; }
    html[${attribute}][data-bilispeed-page="space"] :is(.space-upload, .space-subscribe) { display: flex !important; flex-direction: column; gap: 16px; width: 100% !important; min-width: 0 !important; }
    html[${attribute}][data-bilispeed-page="space"] :is(.upload-sidenav, .upload-content, .subscribe-sidebar, .subscribe-content) { width: 100% !important; min-width: 0 !important; margin: 0 !important; padding: 0 !important; box-sizing: border-box; }
    html[${attribute}][data-bilispeed-page="space"] :is(.upload-sidenav, .subscribe-sidebar) { height: auto !important; }
    html[${attribute}][data-bilispeed-page="space"] .side-nav { display: flex; width: 100% !important; overflow-x: auto; gap: 8px; }
    html[${attribute}][data-bilispeed-page="space"] .side-nav__item { flex: none; width: auto !important; min-width: 88px; margin: 0 !important; }
    html[${attribute}][data-bilispeed-page="space"] :is(.video-header__top, .video-header__bottom, .video-header .breadcrumb, .video-order-filter, .video-list__header) { flex-wrap: wrap; min-width: 0 !important; height: auto !important; gap: 8px; }
    html[${attribute}][data-bilispeed-page="space"] :is(.video-body .video-list.grid-mode, .video-list__content) { display: grid !important; grid-template-columns: repeat(2, minmax(0, 1fr)) !important; gap: 20px 10px !important; }
    html[${attribute}][data-bilispeed-page="space"] .upload-video-card { min-width: 0 !important; max-width: 100%; }
    html[${attribute}][data-bilispeed-page="space"] .upload-video-card.list-mode { flex-wrap: wrap; }
    html[${attribute}][data-bilispeed-page="space"] .upload-video-card__left { min-width: 0 !important; max-width: 100%; }
    html[${attribute}][data-bilispeed-page="space"] .upload-video-card.list-mode .upload-video-card__left { flex: 0 0 42%; width: 42% !important; }
    html[${attribute}][data-bilispeed-page="space"] .upload-video-card__right { min-width: 0 !important; flex: 1; }
    html[${attribute}][data-bilispeed-page="space"] .vui_pagenation { display: flex; flex-wrap: wrap; gap: 8px; width: 100% !important; min-width: 0 !important; max-width: 100%; }
    html[${attribute}][data-bilispeed-page="space"] .vui_pagenation--btns { display: flex; flex-wrap: wrap; gap: 6px; width: 100% !important; min-width: 0 !important; }
    html[${attribute}][data-bilispeed-page="space"] .vui_pagenation--btn { width: auto !important; min-width: 36px !important; margin: 0 !important; }
    html[${attribute}][data-bilispeed-page="space"] .space-settings :is(.section, .title-wrap, .column, .tags-wrap > .content, .switches, .switch-item, .switch-item > .wrap) { width: 100% !important; min-width: 0 !important; max-width: 100% !important; box-sizing: border-box; }
    html[${attribute}][data-bilispeed-page="space"] .space-settings .privacy { grid-template-columns: minmax(0, 1fr) !important; }
    html[${attribute}][data-bilispeed-page="space"] .space-settings .column { overflow-x: auto; overscroll-behavior-x: contain; }
    html[${attribute}][data-bilispeed-page="space"] .space-settings .tags-wrap > .content { flex-wrap: wrap; height: auto !important; gap: 8px; }
    @media (max-width: 350px) {
        html[${attribute}] .favlist-info-detail__actions { flex-direction: column; align-items: stretch; }
    }
    html[${attribute}] :is(.history-record, .watchlater-list, .space-main) :is(.bili-video-card__title, .bili-video-card__info--tit) { font-size: 14px !important; line-height: 20px !important; height: auto !important; }
    html[${attribute}] :is(.history-record, .watchlater-list, .space-main) :is(button, input) { font-size: 14px; }
    html[${attribute}] :is(.history-record, .watchlater-list, .space-main) input { font-size: 16px; }
    html[${attribute}] :is(.history-record, .watchlater-list, .space-main) :is(.vui_button, .radio-filter__item) { min-height: 40px; }
    /* Official dialogs are teleported to body, outside the page's main shell. */
    html[${attribute}]:is([data-bilispeed-page="history"], [data-bilispeed-page="watchlater"], [data-bilispeed-page="space"]) .vui_dialog--content {
        min-width: 0 !important; max-width: calc(100vw - 24px) !important; max-height: calc(100dvh - 24px) !important;
        overflow: auto; padding: 40px 16px 20px !important; box-sizing: border-box;
    }
    html[${attribute}]:is([data-bilispeed-page="history"], [data-bilispeed-page="watchlater"], [data-bilispeed-page="space"]) .vui_dialog--footer {
        display: flex; flex-wrap: wrap; height: auto !important; gap: 8px;
    }
    html[${attribute}]:is([data-bilispeed-page="history"], [data-bilispeed-page="watchlater"], [data-bilispeed-page="space"]) .vui_dialog--btn {
        flex: 1; min-width: 0 !important; width: auto !important; margin: 0 !important; min-height: 44px;
    }
    html[${attribute}]:is([data-bilispeed-page="history"], [data-bilispeed-page="watchlater"], [data-bilispeed-page="space"]) .vui_dialog--body { min-width: 0; overflow-wrap: anywhere; }
    html[${attribute}] :is(.history-record, .watchlater-list, .space-main) :is(.vui_popover-content, .dp__menu) { max-width: calc(100vw - 24px) !important; box-sizing: border-box; }
    html[${attribute}] :is(.history-record, .watchlater-list, .space-main) :is(button, a, input):focus-visible { outline: 2px solid #e8557f; outline-offset: 2px; }

    /* Account pages use a 980px shell and a 150px sidebar on desktop. */
    html[${attribute}][data-bilispeed-page="account"] #account-app,
    html[${attribute}][data-bilispeed-page="account"] .security_content {
        width: 100% !important; min-width: 0 !important; max-width: 100% !important; box-sizing: border-box;
    }
    html[${attribute}][data-bilispeed-page="account"] .top-img {
        width: 100% !important; height: 72px !important; background-size: cover !important; background-position: center;
    }
    html[${attribute}][data-bilispeed-page="account"] .security_content {
        display: flex !important; flex-direction: column !important; margin: 0 0 24px !important;
        overflow: visible !important; border: 0 !important; box-shadow: none !important;
    }
    html[${attribute}][data-bilispeed-page="account"] .security-left {
        width: 100% !important; height: auto !important; overflow-x: auto !important;
        border-bottom: 1px solid #e5e9ef; overscroll-behavior-x: contain;
    }
    html[${attribute}][data-bilispeed-page="account"] .security-title { display: none !important; }
    html[${attribute}][data-bilispeed-page="account"] .security-left ul { display: flex !important; width: max-content; }
    html[${attribute}][data-bilispeed-page="account"] .security-list,
    html[${attribute}][data-bilispeed-page="account"] .security-list-jump {
        width: auto !important; flex: none !important; height: 48px !important; margin: 0 !important;
        border: 0 !important; padding: 0 16px !important; line-height: 48px !important;
    }
    html[${attribute}][data-bilispeed-page="account"] .security-icon,
    html[${attribute}][data-bilispeed-page="account"] .security-list-jump-icon { display: none !important; }
    html[${attribute}][data-bilispeed-page="account"] .security-nav-name,
    html[${attribute}][data-bilispeed-page="account"] .security-list-link-jump {
        margin: 0 !important; white-space: nowrap; letter-spacing: normal !important; font-size: 14px !important; line-height: 48px !important;
    }
    html[${attribute}][data-bilispeed-page="account"] .security-right {
        width: 100% !important; min-width: 0 !important; min-height: 0 !important; border: 0 !important; box-sizing: border-box;
    }
    html[${attribute}][data-bilispeed-page="account"] .security-right :is(div, section, form, ul, table) {
        min-width: 0 !important; max-width: 100% !important; box-sizing: border-box;
    }
    html[${attribute}][data-bilispeed-page="account"] .secuity-right-home,
    html[${attribute}][data-bilispeed-page="account"] .security-right > div {
        width: 100% !important; padding: 20px 16px !important;
    }
    html[${attribute}][data-bilispeed-page="account"] .index-info { display: flex; gap: 12px; padding-bottom: 20px !important; }
    html[${attribute}][data-bilispeed-page="account"] .home-head { flex: none; }
    html[${attribute}][data-bilispeed-page="account"] .home-right { flex: 1; width: 0 !important; margin: 0 !important; }
    html[${attribute}][data-bilispeed-page="account"] .home-top-msg-name { overflow-wrap: anywhere; }
    html[${attribute}][data-bilispeed-page="account"] .home-top-level-all { width: 100% !important; }
    html[${attribute}][data-bilispeed-page="account"] .home-top-progress-wrap { max-width: 100%; }
    html[${attribute}][data-bilispeed-page="account"] .home-top-level-up { width: min(140px, 28vw) !important; }
    html[${attribute}][data-bilispeed-page="account"] .home-top-level-number { display: block; margin: 4px 0 !important; }
    html[${attribute}][data-bilispeed-page="account"] .home-to-update,
    html[${attribute}][data-bilispeed-page="account"] .home-to-space {
        position: relative !important; top: auto !important; right: auto !important; min-height: 40px; line-height: 40px !important;
        margin: 8px 6px 0 0 !important;
    }
    html[${attribute}][data-bilispeed-page="account"] .index-invition-box {
        width: 100% !important; height: auto !important; min-height: 120px; padding: 28px 12px !important;
        background-size: cover !important; background-position: center;
    }
    html[${attribute}][data-bilispeed-page="account"] .invition-box-index { display: flex; flex-wrap: wrap; gap: 8px; height: auto !important; }
    html[${attribute}][data-bilispeed-page="account"] .index-invition-box :is(.btn-ok, .btn-disable) {
        position: static !important; float: none !important; flex: none; min-height: 40px; line-height: 40px;
    }
    html[${attribute}][data-bilispeed-page="account"] :is(.home-daily-task-warp, .home-mp, .home-safe) { padding: 24px 0 !important; }
    html[${attribute}][data-bilispeed-page="account"] .home-dialy-task-tips { position: static !important; margin: 8px 0; }
    html[${attribute}][data-bilispeed-page="account"] .home-dialy-exp-item { width: 49% !important; vertical-align: top; }
    html[${attribute}][data-bilispeed-page="account"] .user-setting-warp { padding: 16px !important; }
    html[${attribute}][data-bilispeed-page="account"] .el-form-item__label { float: none !important; display: block; width: auto !important; text-align: left; }
    html[${attribute}][data-bilispeed-page="account"] .el-form-item__content { margin-left: 0 !important; }
    html[${attribute}][data-bilispeed-page="account"] :is(.el-input, .el-textarea, .el-dialog, .popup-box) { width: 100% !important; min-width: 0 !important; max-width: calc(100vw - 32px) !important; }
    html[${attribute}][data-bilispeed-page="account"] input { max-width: 100%; font-size: 16px; }

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
    /* The inline login dialog is a different component from passport's page. */
    html[${attribute}] .bili-mini-mask .bili-mini-content-wp {
        display: block !important;
        width: calc(100vw - 24px) !important; max-width: 420px !important; min-width: 0 !important;
        height: auto !important; min-height: 0 !important; max-height: calc(100dvh - 24px); overflow-y: auto; overflow-x: hidden;
        padding: 48px 16px 20px !important; box-sizing: border-box; background-image: none !important;
    }
    html[${attribute}] .bili-mini-mask :is(.login-scan-wp, .bili-mini-line) { display: none !important; }
    html[${attribute}] .bili-mini-mask :is(.bili-mini-login-right-wp, .login-pwd-wp, .tab__form, .login-tab-wp, .login-sns-wp, .login-agreement-wp) {
        width: 100% !important; min-width: 0 !important; margin: 0 !important; box-sizing: border-box;
    }
    html[${attribute}] .bili-mini-mask .bili-mini-close-icon { top: 8px !important; right: 8px !important; }
    html[${attribute}] .bili-mini-mask .bili-mini-customer-title {
        position: absolute !important; top: 14px !important; left: 16px !important; right: 52px !important;
        width: auto !important; height: auto !important; font-size: 16px !important; line-height: 24px !important;
        text-align: left !important; white-space: normal;
    }
    html[${attribute}] .bili-mini-mask .login-tab-wp { display: flex !important; justify-content: center; white-space: nowrap; }
    html[${attribute}] .bili-mini-mask .form__item { display: flex; width: 100% !important; box-sizing: border-box; }
    html[${attribute}] .bili-mini-mask .form__item input { flex: 1; width: 0 !important; min-width: 0 !important; font-size: 16px; }
    html[${attribute}] .bili-mini-mask .btn_wp { display: flex; gap: 8px; width: 100% !important; }
    html[${attribute}] .bili-mini-mask .btn_wp > * { flex: 1; width: 0 !important; margin: 0 !important; }
    html[${attribute}] .bili-mini-mask .login-agreement-wp {
        position: static !important; inset: auto !important; transform: none !important;
        padding-top: 16px !important; font-size: 12px;
    }
}
`;

    // The current official comments use nested Shadow DOM; document CSS cannot
    // reach their desktop spacing or action row. Only style known components.
    const commentStyles = {
        'bili-comment-renderer': `
            & #body { padding-left: 56px; }
            & #user-avatar { left: 4px; width: 40px; height: 40px; }
            & #user-avatar bili-avatar { --avatar-width: 40px !important; --avatar-height: 40px !important; }
            & #main { min-width: 0; }
            & #ornament { display: none; }`,
        'bili-comment-replies-renderer': `& #expander { padding-left: 56px; }`,
        'bili-comment-reply-renderer': `& #footer { padding-right: 0; }`,
        'bili-comment-action-buttons-renderer': `
            & { flex-wrap: wrap; gap: 2px 12px; }
            & > :not(:first-child) { margin-left: 0; }
            & #pubdate { flex-basis: 100%; white-space: nowrap; }
            & :is(#like, #dislike, #reply) { flex: none; }
            & button { white-space: nowrap; min-height: 36px; height: auto; }
            & #more { margin-left: auto; margin-right: 0; height: 36px; }`,
        'bili-comment-box': `
            & #user-avatar { width: 48px; }
            & #comment-area { flex: 1; width: calc(100% - 48px); min-width: 0; }
            & #footer { flex-wrap: wrap; gap: 6px; }
            & #optional { order: 5; flex-basis: 100%; }`
    };

    function scheduleComments() {
        if (suspended || commentTimer !== null) return;
        commentTimer = setTimeout(() => { commentTimer = null; commentLayout(); }, 100);
    }

    function commentLayout() {
        if (suspended) return;
        for (const [root, watcher] of commentRoots) {
            if (!root.host.isConnected) { watcher.disconnect(); commentRoots.delete(root); }
        }
        function visit(element) {
            const root = element.shadowRoot;
            if (!root) {
                if (!customElements.get(element.localName) && !waitingCommentTags.has(element.localName)) {
                    waitingCommentTags.add(element.localName);
                    customElements.whenDefined(element.localName).then(scheduleComments);
                }
                return;
            }
            const css = commentStyles[element.localName];
            if (css && !root.getElementById('bilispeed-comment-style')) {
                const style = document.createElement('style');
                style.id = 'bilispeed-comment-style';
                style.textContent = '@media (max-width: 1000px) {'
                    + css.replaceAll('&', ':host-context(html[data-bilispeed-touch])') + '}';
                root.appendChild(style);
            }
            if (!commentRoots.has(root)) {
                const watcher = new MutationObserver(records => {
                    if (records.some(record => [...record.addedNodes, ...record.removedNodes].some(node =>
                        node.nodeType === 1 && (node.id !== 'bilispeed-comment-style' || !node.isConnected)))) scheduleComments();
                });
                watcher.observe(root, { childList: true, subtree: true });
                commentRoots.set(root, watcher);
            }
            root.querySelectorAll('bili-comments-header-renderer, bili-comment-thread-renderer, bili-comment-renderer, '
                + 'bili-comment-replies-renderer, bili-comment-reply-renderer, bili-comment-action-buttons-renderer, bili-comment-box').forEach(visit);
        }
        document.querySelectorAll('bili-comments').forEach(visit);
    }

    function pageKind() {
        if (location.hostname === 'account.bilibili.com') return 'account';
        if (location.hostname === 'space.bilibili.com') return 'space';
        if (location.hostname === 'passport.bilibili.com') return 'login';
        if (location.hostname === 'search.bilibili.com') return 'search';
        if (location.hostname === 't.bilibili.com') return 'dynamic';
        if (location.pathname.startsWith('/video/')) return 'video';
        if (location.pathname.startsWith('/v/popular')) return 'popular';
        if (/^\/(?:account\/)?history(?:\/|$)/.test(location.pathname)) return 'history';
        if (location.pathname.startsWith('/watchlater')) return 'watchlater';
        if (location.pathname === '/' || location.pathname === '') return 'home';
        return 'other';
    }

    function isPageReady() {
        const app = document.getElementById('app');
        const bootstrap = document.querySelector('script[src*="/jinkela/video/video."]');
        // Vue removes data-server-rendered at the START of hydration. Its root
        // mount flag is the boundary after which adding our children is safe.
        const mounted = app && (app.__vue__ && app.__vue__._isMounted
            || app.__vue_app__ && app.__vue_app__._instance && app.__vue_app__._instance.isMounted);
        if (mounted || document.readyState !== 'loading' && !bootstrap && !(app && app.hasAttribute('data-server-rendered'))) return true;
        if (!document.querySelector('#mirror-vdcon .video-toolbar-container')) return false;
        if (!hydrationStarted) {
            hydrationStarted = Date.now();
            // If the official bootstrap itself fails, still expose usable
            // controls and metadata instead of leaving skeletons forever.
            setTimeout(schedule, 3500);
        }
        if (bootstrap && !bootstrapFailed) {
            if (!suspended && hydrationTimer === null) hydrationTimer = setTimeout(() => {
                hydrationTimer = null; schedule();
            }, 500);
            return false;
        }
        return Date.now() - hydrationStarted >= 3500;
    }

    function videoTabs() {
        const toolbar = document.querySelector('#mirror-vdcon .video-toolbar-container');
        if (!toolbar) return;
        let tabs = document.getElementById('bilispeed-video-tabs');
        if (!tabs) {
            tabs = document.createElement('nav');
            tabs.id = 'bilispeed-video-tabs';
            tabs.setAttribute('aria-label', '视频内容');
            [['简介', '#v_desc, .video-desc-container'], ['评论', '#commentapp, bili-comments'],
                ['选集', '#bilispeed-episodes[data-ready], .video-pod-above-modules, #multi_page, .video-sections']].forEach(([label, selector]) => {
                const button = document.createElement('button');
                button.type = 'button';
                button.textContent = label;
                button.dataset.tab = label;
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

    function closeDanmaku(wrap) {
        wrap.removeAttribute('data-bilispeed-danmaku-open');
        wrap.setAttribute('data-bilispeed-danmaku-closed', '');
        const trigger = wrap.closest('.bpx-player-dm-setting');
        if (trigger) (trigger.querySelector('[data-bilispeed-danmaku-trigger]') || trigger).setAttribute('aria-expanded', 'false');
    }

    function libraryNavigation() {
        const active = document.querySelector('.nav-tab__item.active');
        if (active && previousSpaceRoute !== location.pathname) {
            const nav = active.closest('.nav-tab');
            if (nav) {
                nav.scrollLeft += active.getBoundingClientRect().left - nav.getBoundingClientRect().left
                    - (nav.clientWidth - active.getBoundingClientRect().width) / 2;
                previousSpaceRoute = location.pathname;
            }
        }
        const library = document.querySelector('.space-favlist');
        const folders = library && library.querySelector('.favlist-aside');
        if (!folders) return;
        let toggle = library.querySelector('#bilispeed-folder-toggle');
        if (!toggle) {
            toggle = document.createElement('button');
            toggle.id = 'bilispeed-folder-toggle'; toggle.type = 'button';
            if (!folders.id) folders.id = 'bilispeed-folder-list';
            toggle.setAttribute('aria-controls', folders.id);
            toggle.setAttribute('aria-expanded', 'false');
            toggle.addEventListener('click', () => {
                const open = library.toggleAttribute('data-bilispeed-folders-open');
                toggle.setAttribute('aria-expanded', String(open));
            });
            folders.addEventListener('click', event => {
                if (!(event.target instanceof Element) || !event.target.closest('.vui_sidebar-item')) return;
                library.removeAttribute('data-bilispeed-folders-open'); toggle.setAttribute('aria-expanded', 'false');
                schedule();
            });
            folders.before(toggle); library.setAttribute('data-bilispeed-folders', '');
        }
        const selected = folders.querySelector('.vui_sidebar-item--active .vui_ellipsis');
        const label = '收藏夹' + (selected && selected.textContent.trim() ? ' · ' + selected.textContent.trim() : '与收藏分类');
        if (toggle.textContent !== label) toggle.textContent = label;
    }

    function danmakuSettings() {
        document.querySelectorAll('#bilibili-player .bpx-player-dm-setting').forEach(trigger => {
            const wrap = trigger.querySelector('.bpx-player-dm-setting-wrap');
            if (!wrap) return;
            if (!wrap.querySelector('[data-bilispeed-danmaku-header]')) {
                const header = document.createElement('header');
                header.setAttribute('data-bilispeed-danmaku-header', '');
                const title = document.createElement('span');
                title.textContent = '弹幕设置';
                const done = document.createElement('button');
                done.type = 'button'; done.textContent = '完成';
                done.setAttribute('aria-label', '关闭弹幕设置');
                done.dataset.bilispeedControl = 'danmaku-close';
                done.addEventListener('click', event => {
                    event.preventDefault(); event.stopPropagation(); closeDanmaku(wrap);
                });
                header.append(title, done); wrap.prepend(header);
            }
            // Label the icon rather than making the popup's checkbox/slider
            // descendants presentational children of one ARIA button.
            const action = Array.from(trigger.children).find(child => child !== wrap) || trigger;
            action.setAttribute('data-bilispeed-danmaku-trigger', '');
            if (action !== trigger) action.setAttribute('role', 'button');
            action.tabIndex = 0;
            action.setAttribute('aria-label', '弹幕设置');
            action.setAttribute('aria-expanded', String(wrap.hasAttribute('data-bilispeed-danmaku-open')));
            if (managedDanmaku.has(trigger)) return;
            managedDanmaku.add(trigger);
            trigger.addEventListener('click', event => {
                if (!(event.target instanceof Element)) return;
                if (event.target.closest('.bpx-player-dm-setting-wrap')) return;
                const current = trigger.querySelector('.bpx-player-dm-setting-wrap');
                if (!current) return;
                event.preventDefault(); event.stopImmediatePropagation();
                if (current.hasAttribute('data-bilispeed-danmaku-open')) closeDanmaku(current);
                else {
                    current.removeAttribute('data-bilispeed-danmaku-closed');
                    current.setAttribute('data-bilispeed-danmaku-open', '');
                    (trigger.querySelector('[data-bilispeed-danmaku-trigger]') || trigger).setAttribute('aria-expanded', 'true');
                }
            }, true);
            trigger.addEventListener('keydown', event => {
                if (!(event.target instanceof Element) || !event.target.matches('[data-bilispeed-danmaku-trigger]')
                        || !['Enter', ' '].includes(event.key)) return;
                event.preventDefault(); trigger.click();
            });
        });
    }


    function refresh() {
        if (suspended) return;
        const html = document.documentElement;
        if (!html || !document.head) return;
        // Official metadata still contains HTTP CDN images. Upgrade requests
        // instead of weakening the WebView's mixed-content protection.
        if (!document.getElementById('bilispeed-https-resources')) {
            const secure = document.createElement('meta');
            secure.id = 'bilispeed-https-resources';
            secure.httpEquiv = 'Content-Security-Policy';
            secure.content = 'upgrade-insecure-requests';
            document.head.appendChild(secure);
        }
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
        // Avoid inserting extra children into server-rendered Vue markup
        // while the official page is still parsing and hydrating it.
        if (page === 'video' && isPageReady()) {
            if (window.__BiliTouchVideo) window.__BiliTouchVideo.refresh();
            videoTabs(); danmakuSettings(); commentLayout();
        }
        if (page === 'space' && isPageReady()) libraryNavigation();
        if (page === 'dynamic' || page === 'space') commentLayout();
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
        if (record.type === 'attributes') return target && (target.matches('meta[name="viewport"]')
                || record.attributeName === 'data-server-rendered' && target.id === 'app');
        return [...record.addedNodes, ...record.removedNodes].some(node =>
            node.nodeType === 1 && !node.matches(ownElements + ', ' + transientPlayerElements));
    }

    function setSuspended(value) {
        const next = !!value;
        if (next === suspended) return;
        suspended = next;
        clearTimeout(refreshTimer);
        refreshTimer = null;
        clearTimeout(hydrationTimer); hydrationTimer = null;
        observer.disconnect();
        clearTimeout(commentTimer); commentTimer = null;
        commentRoots.forEach(watcher => watcher.disconnect()); commentRoots.clear();
        if (window.__BiliTouchVideo) window.__BiliTouchVideo.setSuspended(suspended);
        if (window.__BiliTouchPlayer) window.__BiliTouchPlayer.setSuspended(suspended);
        if (!suspended) { observer.observe(document, observation); refresh(); }
    }

    Object.defineProperty(window, '__BiliTouch', { value: Object.freeze({ refresh, setSuspended, isPageReady }), configurable: false });
    const observer = new MutationObserver(records => { if (records.some(requiresRefresh)) schedule(); });
    if (!suspended) observer.observe(document, observation);
    document.addEventListener('DOMContentLoaded', refresh, { once: true });
    window.addEventListener('popstate', schedule);
    window.addEventListener('hashchange', schedule);
    window.addEventListener('pageshow', schedule);
    window.addEventListener('error', event => {
        const source = event.target instanceof HTMLScriptElement ? event.target.src : event.filename;
        if (typeof source === 'string' && source.includes('/jinkela/video/video.')) {
            bootstrapFailed = true; schedule();
        }
    }, true);
    document.addEventListener('click', event => {
        if (suspended || !(event.target instanceof Element) || event.target.closest('.bpx-player-dm-setting')) return;
        document.querySelectorAll('[data-bilispeed-danmaku-open]').forEach(closeDanmaku);
    }, true);
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape') document.querySelectorAll('[data-bilispeed-danmaku-open]').forEach(closeDanmaku);
    });
    refresh();
})();
