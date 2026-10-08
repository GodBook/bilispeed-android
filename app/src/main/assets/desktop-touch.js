(function () {
    'use strict';
    // Only adapt the main page, never an embedded login/player document.
    if (window !== window.top || window.__BiliTouch || window.__BILI_TOUCH_ENABLED__ === false) return;
    const supported = ['www.bilibili.com', 'bilibili.com', 'search.bilibili.com', 't.bilibili.com',
        'space.bilibili.com', 'account.bilibili.com', 'passport.bilibili.com'];
    if (!supported.includes(location.hostname)) return;

    const attribute = 'data-bilispeed-touch';
    let scheduled = false;
    let previousPage = '';
    const controlledVideos = new WeakSet();
    let editingProgress = false;
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
        height: calc(56.25vw + 46px) !important; max-height: none !important;
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
    html[${attribute}] #bilibili-player .bpx-player-control-wrap { display: none !important; }
    html[${attribute}] #bilispeed-touch-controls {
        display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between;
        position: absolute; bottom: 0; left: 0; right: 0; z-index: 100;
        padding: 4px 8px; box-sizing: border-box; color: #fff;
        background: linear-gradient(transparent, rgba(0,0,0,.72)); font: 13px sans-serif;
    }
    html[${attribute}] #bilispeed-touch-controls input {
        flex: 0 0 100%; width: 100%; min-width: 0; height: 24px; margin: 0; accent-color: #e8557f;
    }
    html[${attribute}] #bilispeed-touch-controls button {
        min-width: 44px; min-height: 40px; padding: 0 8px; border: 0; border-radius: 6px;
        background: transparent; color: #fff; font: 15px sans-serif; touch-action: manipulation;
    }
    html[${attribute}] #bilispeed-touch-controls button:focus-visible { outline: 2px solid #e8557f; }
    html[${attribute}] #bilispeed-touch-controls [data-bilispeed-control="play"] { font-size: 20px; }
    html[${attribute}] #bilispeed-touch-controls [data-bilispeed-control="time"] { flex: 1; padding-left: 8px; }
    html[${attribute}] .bpx-player-sending-bar { min-width: 0 !important; }
    html[${attribute}] .bpx-player-video-info { display: none !important; }
    html[${attribute}] #bilispeed-video-tabs { display: flex; border-bottom: 1px solid #eee; }
    html[${attribute}] #bilispeed-video-tabs button {
        flex: 1; min-height: 44px; border: 0; background: #fff; color: #61666d; font-size: 15px;
    }
    html[${attribute}] #bilispeed-video-tabs button:focus-visible { outline: 2px solid #e8557f; }

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
        if (!toolbar || document.getElementById('bilispeed-video-tabs')) return;
        const tabs = document.createElement('nav');
        tabs.id = 'bilispeed-video-tabs';
        tabs.setAttribute('aria-label', '视频内容');
        [['简介', '#v_desc'], ['评论', '#commentapp'], ['选集', '.video-pod-above-modules']].forEach(([label, selector]) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.textContent = label;
            button.addEventListener('click', () => {
                const section = document.querySelector(selector);
                if (section) section.scrollIntoView({ behavior: 'smooth', block: 'start' });
            });
            if (label === '选集' && !document.querySelector(selector)) return;
            tabs.appendChild(button);
        });
        toolbar.after(tabs);
    }

    function currentVideo() {
        const player = document.getElementById('bilibili-player');
        if (!player) return null;
        const videos = Array.from(player.querySelectorAll('video')).filter(video => video.isConnected);
        return videos.find(video => !video.paused && !video.ended) || videos.find(video => video.readyState >= 1) || videos[0];
    }

    function timeLabel(seconds) {
        if (!Number.isFinite(seconds) || seconds < 0) return '00:00';
        const total = Math.floor(seconds);
        const hours = Math.floor(total / 3600);
        const minutes = Math.floor(total / 60) % 60;
        const secondsText = String(total % 60).padStart(2, '0');
        return (hours ? hours + ':' + String(minutes).padStart(2, '0') : String(minutes).padStart(2, '0')) + ':' + secondsText;
    }

    function updateControls() {
        const controls = document.getElementById('bilispeed-touch-controls');
        const video = currentVideo();
        if (!controls || !video) return;
        const play = controls.querySelector('[data-bilispeed-control="play"]');
        const seek = controls.querySelector('input');
        const playing = !video.paused && !video.ended;
        const seekable = Number.isFinite(video.duration) && video.duration > 0;
        const label = playing ? '暂停视频' : '播放视频';
        if (play.getAttribute('aria-label') !== label) {
            play.setAttribute('aria-label', label);
            play.textContent = playing ? 'Ⅱ' : '▶';
        }
        seek.disabled = !seekable;
        if (!editingProgress) seek.value = seekable ? Math.round(video.currentTime / video.duration * 1000) : 0;
        const position = editingProgress && seekable ? Number(seek.value) / 1000 * video.duration : video.currentTime;
        const time = controls.querySelector('[data-bilispeed-control="time"]');
        const text = video.duration === Infinity ? '直播' : timeLabel(position) + ' / ' + timeLabel(video.duration);
        if (time.textContent !== text) time.textContent = text;
        const fullscreen = controls.querySelector('[data-bilispeed-control="fullscreen"]');
        const fullText = document.fullscreenElement ? '退出全屏' : '全屏';
        if (fullscreen.textContent !== fullText) fullscreen.textContent = fullText;
    }

    function touchControls() {
        const video = currentVideo();
        if (!video) return;
        const host = video.closest('.bpx-player-video-area') || document.getElementById('bilibili-player');
        let controls = document.getElementById('bilispeed-touch-controls');
        if (!controls) {
            controls = document.createElement('div');
            controls.id = 'bilispeed-touch-controls';
            controls.setAttribute('role', 'group');
            controls.setAttribute('aria-label', '视频播放控制');
            ['click', 'dblclick', 'pointerdown', 'pointerup', 'mousedown', 'mouseup', 'touchstart', 'touchend'].forEach(name => {
                controls.addEventListener(name, event => event.stopPropagation());
            });
            const seek = document.createElement('input');
            seek.type = 'range'; seek.min = '0'; seek.max = '1000'; seek.step = '1';
            seek.setAttribute('data-bilispeed-control', 'seek');
            seek.setAttribute('aria-label', '播放进度');
            seek.addEventListener('input', () => { editingProgress = true; updateControls(); });
            seek.addEventListener('change', () => {
                const active = currentVideo();
                if (active && Number.isFinite(active.duration) && active.duration > 0) {
                    try { active.currentTime = Number(seek.value) / 1000 * active.duration; } catch (_) {}
                }
                editingProgress = false;
                updateControls();
            });
            seek.addEventListener('pointercancel', () => { editingProgress = false; updateControls(); });
            controls.appendChild(seek);
            const play = document.createElement('button');
            play.type = 'button';
            play.setAttribute('data-bilispeed-control', 'play');
            play.addEventListener('click', event => {
                event.preventDefault();
                const active = currentVideo();
                if (!active) return;
                if (active.paused || active.ended) active.play().catch(() => updateControls());
                else active.pause();
            });
            controls.appendChild(play);
            const time = document.createElement('span');
            time.setAttribute('data-bilispeed-control', 'time');
            controls.appendChild(time);
            const fullscreen = document.createElement('button');
            fullscreen.type = 'button';
            fullscreen.setAttribute('data-bilispeed-control', 'fullscreen');
            fullscreen.addEventListener('click', event => {
                event.preventDefault();
                const active = currentVideo();
                if (!active) return;
                if (document.fullscreenElement) document.exitFullscreen().catch(() => updateControls());
                else {
                    const target = active.closest('.bpx-player-container') || active;
                    if (target.requestFullscreen) target.requestFullscreen().catch(() => updateControls());
                }
            });
            controls.appendChild(fullscreen);
            host.appendChild(controls);
        } else if (controls.parentElement !== host) host.appendChild(controls);
        if (!controlledVideos.has(video)) {
            controlledVideos.add(video);
            ['timeupdate', 'play', 'pause', 'ended', 'loadedmetadata', 'durationchange', 'seeking', 'seeked'].forEach(name => {
                video.addEventListener(name, updateControls, { passive: true });
            });
        }
        updateControls();
    }

    function refresh() {
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
        if (page === 'video') { videoTabs(); touchControls(); }
    }

    function schedule() {
        if (scheduled) return;
        scheduled = true;
        setTimeout(() => { scheduled = false; refresh(); }, 100);
    }

    Object.defineProperty(window, '__BiliTouch', { value: Object.freeze({ refresh }), configurable: false });
    const observer = new MutationObserver(schedule);
    observer.observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ['content'] });
    document.addEventListener('DOMContentLoaded', refresh, { once: true });
    window.addEventListener('popstate', schedule);
    window.addEventListener('hashchange', schedule);
    window.addEventListener('pageshow', schedule);
    document.addEventListener('fullscreenchange', updateControls);
    refresh();
})();
