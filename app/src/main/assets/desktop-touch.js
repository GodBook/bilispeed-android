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
    const managedDanmaku = new WeakSet();
    const ownElements = '#bilispeed-touch-controls, #bilispeed-seek-feedback, #bilispeed-video-tabs, #bilispeed-touch-style, #bilispeed-player-style, #bilispeed-episodes-style, #bilispeed-episodes, [data-bilispeed-image], [data-bilispeed-danmaku-header]';
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
}
`;

    function pageKind() {
        if (location.hostname === 'account.bilibili.com') return 'account';
        if (location.hostname === 'space.bilibili.com') return 'space';
        if (location.hostname === 'passport.bilibili.com') return 'login';
        if (location.hostname === 'search.bilibili.com') return 'search';
        if (location.hostname === 't.bilibili.com') return 'dynamic';
        if (location.pathname.startsWith('/video/')) return 'video';
        if (location.pathname.startsWith('/v/popular')) return 'popular';
        if (location.pathname === '/' || location.pathname === '') return 'home';
        return 'other';
    }

    function isPageReady() {
        if (document.readyState !== 'loading' && !document.querySelector('#app[data-server-rendered]')) return true;
        if (!document.querySelector('#mirror-vdcon .video-toolbar-container')) return false;
        if (!hydrationStarted) {
            hydrationStarted = Date.now();
            // If the official bootstrap itself fails, still expose usable
            // controls and metadata instead of leaving skeletons forever.
            setTimeout(schedule, 3500);
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
            videoTabs(); danmakuSettings();
        }
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
        observer.disconnect();
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
    document.addEventListener('click', event => {
        if (suspended || !(event.target instanceof Element) || event.target.closest('.bpx-player-dm-setting')) return;
        document.querySelectorAll('[data-bilispeed-danmaku-open]').forEach(closeDanmaku);
    }, true);
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape') document.querySelectorAll('[data-bilispeed-danmaku-open]').forEach(closeDanmaku);
    });
    refresh();
})();
