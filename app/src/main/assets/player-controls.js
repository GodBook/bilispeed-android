(function () {
    'use strict';
    if (!window.__BiliTouch || window.__BiliTouchPlayer) return;

    const managedVideos = new WeakSet();
    const managedHosts = new WeakSet();
    const audibleVolumes = new WeakMap();
    let video = null;
    let host = null;
    let controls = null;
    let feedback = null;
    let parts = {};
    let gesture = null;
    let progressVideo = null;
    let progressSource = '';
    let preview = null;
    let panel = '';
    let interacting = false;
    let keyboardInteraction = false;
    let nativeFullscreen = !!window.__BILI_TOUCH_FULLSCREEN__;
    let fullscreen = false;
    let hideTimer = null;
    let feedbackTimer = null;
    let suppressClickUntil = 0;
    let pendingTap = null;
    let appearance = window.__BILI_BUTTON_APPEARANCE__ || { size: 100, opacity: 100 };
    let subtitleRoot = null;
    let suspended = !!window.__BILI_SPEED_SUSPENDED__;
    const subtitleObserver = new MutationObserver(() => {
        if (suspended) return;
        update();
        if (panel === 'subtitles') renderSubtitles();
    });
    const stylesheet = `
html[data-bilispeed-touch] [data-bilispeed-player] { position: relative; min-width: 0; touch-action: pan-y pinch-zoom; }
html[data-bilispeed-touch][data-bilispeed-fullscreen] [data-bilispeed-player] { touch-action: none; }
html[data-bilispeed-touch] [data-bilispeed-player] video {
    width: 100% !important; height: 100% !important; max-width: 100% !important; max-height: 100% !important;
    display: block; object-fit: contain !important; object-position: center; background: #000;
}
html[data-bilispeed-touch] [data-bilispeed-player] .bpx-player-control-wrap { display: none !important; }
html[data-bilispeed-touch] #bilispeed-touch-controls {
    display: grid; grid-template-columns: 44px minmax(0, 1fr) auto auto auto;
    align-items: center; gap: 0 2px; position: absolute; bottom: 0; left: 0; right: 0; z-index: 100;
    box-sizing: border-box; padding: 12px 8px 4px;
    color: #fff; background: linear-gradient(transparent, rgba(0, 0, 0, .8));
    font: 12px sans-serif; opacity: 1; visibility: visible; transition: opacity .18s;
}
html[data-bilispeed-touch] #bilispeed-touch-controls[data-hidden="true"] {
    opacity: 0; visibility: hidden; pointer-events: none;
}
html[data-bilispeed-touch] #bilispeed-touch-controls button {
    min-width: 44px; min-height: 44px; padding: 0 6px; border: 0; border-radius: 6px;
    color: #fff; background: transparent; font: 13px sans-serif; touch-action: manipulation;
}
html[data-bilispeed-touch] #bilispeed-touch-controls button[aria-pressed="true"] { color: #ff91b2; }
html[data-bilispeed-touch] #bilispeed-touch-controls :focus-visible { outline: 2px solid #ff91b2; outline-offset: -2px; }
html[data-bilispeed-touch] #bilispeed-touch-controls :disabled { opacity: .45; }
html[data-bilispeed-touch] #bilispeed-touch-controls [data-bilispeed-control="subtitles"] {
    min-width: max(44px, calc(44px * var(--bilispeed-subtitle-size, 1)));
    min-height: max(44px, calc(44px * var(--bilispeed-subtitle-size, 1)));
    font-size: calc(13px * var(--bilispeed-subtitle-size, 1));
    opacity: var(--bilispeed-subtitle-opacity, 1);
}
html[data-bilispeed-touch] #bilispeed-touch-controls input[type="range"] {
    min-width: 0; height: 28px; margin: 0; accent-color: #e8557f; touch-action: none;
}
html[data-bilispeed-touch] #bilispeed-touch-controls [data-bilispeed-control="seek"] { grid-column: 1 / -1; width: 100%; }
html[data-bilispeed-touch] #bilispeed-touch-controls [data-bilispeed-control="play"] { font-size: 20px; }
html[data-bilispeed-touch] #bilispeed-touch-controls [data-bilispeed-control="time"] {
    white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-variant-numeric: tabular-nums;
}
html[data-bilispeed-touch] #bilispeed-player-panel {
    position: absolute; right: 8px; bottom: 4px; width: min(280px, calc(100% - 16px));
    max-height: min(260px, calc(var(--bilispeed-player-height, 100vh) - 16px)); overflow: auto; padding: 12px; box-sizing: border-box;
    background: rgba(24, 25, 28, .96); border: 1px solid #505158; border-radius: 10px;
    font: 14px sans-serif; overscroll-behavior: contain;
}
html[data-bilispeed-touch] #bilispeed-player-panel[hidden],
html[data-bilispeed-touch] #bilispeed-player-panel > [hidden],
html[data-bilispeed-touch] #bilispeed-seek-feedback[hidden] { display: none !important; }
html[data-bilispeed-touch] #bilispeed-player-panel header { display: flex; align-items: center; justify-content: space-between; }
html[data-bilispeed-touch] #bilispeed-player-panel [data-bilispeed-control="volume-slider"] { width: 100%; height: 44px; }
html[data-bilispeed-touch] #bilispeed-player-panel [data-subtitle-option] { display: block; width: 100%; text-align: left; }
html[data-bilispeed-touch] #bilispeed-player-panel p { line-height: 1.6; margin: 8px 0; }
html[data-bilispeed-touch] #bilispeed-seek-feedback {
    position: absolute; left: 50%; top: 38%; transform: translate(-50%, -50%); z-index: 101;
    max-width: calc(100% - 32px); padding: 10px 14px; border-radius: 8px; box-sizing: border-box;
    color: #fff; background: rgba(0, 0, 0, .76); font: 14px sans-serif; text-align: center; pointer-events: none;
}
html[data-bilispeed-touch] [data-bilispeed-player][data-controls-visible] .bpx-player-subtitle-wrap {
    transform: translateY(calc(-1 * var(--bilispeed-controls-height, 72px)));
}
html[data-bilispeed-touch][data-bilispeed-fullscreen] .bpx-player-sending-area,
html[data-bilispeed-touch][data-bilispeed-fullscreen] .bpx-player-sending-bar { display: none !important; }
html[data-bilispeed-touch][data-bilispeed-fullscreen] #bilispeed-player-panel {
    bottom: calc(100% + 4px); max-height: min(260px, calc(var(--bilispeed-player-height, 100vh) - var(--bilispeed-controls-height, 84px) - 16px));
}
html[data-bilispeed-touch][data-bilispeed-fullscreen] #bilispeed-touch-controls {
    padding: 12px max(8px, env(safe-area-inset-right)) max(4px, env(safe-area-inset-bottom)) max(8px, env(safe-area-inset-left));
}
html[data-bilispeed-touch][data-bilispeed-fullscreen] #bilibili-player,
html[data-bilispeed-touch] .bpx-player-container:fullscreen,
html[data-bilispeed-touch] .bpx-player-container[data-screen="full"],
html[data-bilispeed-touch] .bpx-player-container[data-screen="web"] {
    width: 100% !important; height: 100% !important; min-width: 0 !important; margin: 0 !important;
}
@media (max-width: 360px) {
    html[data-bilispeed-touch] #bilispeed-touch-controls { grid-template-columns: 40px minmax(0, 1fr) auto auto auto; padding-left: 4px; padding-right: 4px; }
    html[data-bilispeed-touch] #bilispeed-touch-controls button { min-width: 40px; padding: 0 4px; font-size: 12px; }
    html[data-bilispeed-touch] #bilispeed-touch-controls [data-bilispeed-control="time"] { font-size: 11px; }
}
@media (prefers-reduced-motion: reduce) {
    html[data-bilispeed-touch] #bilispeed-touch-controls { transition: none; }
}
`;

    function currentVideo() {
        const player = document.getElementById('bilibili-player');
        if (!player) return null;
        const videos = Array.from(player.querySelectorAll('video')).filter(item => item.isConnected);
        const visible = videos.filter(item => item.getBoundingClientRect().width > 0 && getComputedStyle(item).visibility !== 'hidden');
        const candidates = visible.length ? visible : videos;
        return candidates.find(item => !item.paused && !item.ended) || candidates.find(item => item.readyState >= 1) || candidates[0] || null;
    }

    function timeLabel(seconds) {
        const total = Math.floor(Number.isFinite(seconds) ? Math.max(0, seconds) : 0);
        const hours = Math.floor(total / 3600);
        return (hours ? hours + ':' : '') + String(Math.floor(total / 60) % 60).padStart(2, '0')
            + ':' + String(total % 60).padStart(2, '0');
    }

    function setText(element, text) {
        if (element.textContent !== text) element.textContent = text;
    }

    function setAttribute(element, name, value) {
        if (element.getAttribute(name) !== value) element.setAttribute(name, value);
    }

    function seekable(item) {
        return item && item.readyState >= 1 && Number.isFinite(item.duration) && item.duration > 0;
    }

    function isFullscreen() {
        return nativeFullscreen || !!(document.fullscreenElement || document.webkitFullscreenElement)
            || !!document.querySelector('#bilibili-player .bpx-player-container[data-screen="full"], #bilibili-player .bpx-player-container[data-screen="web"]');
    }

    function setVisible(visible) {
        if (!controls) return;
        controls.dataset.hidden = String(!visible);
        controls.setAttribute('aria-hidden', String(!visible));
        controls.inert = !visible;
        if (host) host.toggleAttribute('data-controls-visible', visible);
    }

    function scheduleHide() {
        clearTimeout(hideTimer);
        hideTimer = null;
        if (suspended || !fullscreen || !controls || controls.dataset.hidden === 'true') return;
        hideTimer = setTimeout(() => {
            hideTimer = null;
            // Never remove a slider or a settings panel while it is being used.
            if (panel || interacting || progressVideo || gesture) return;
            if (keyboardInteraction && controls.contains(document.activeElement)) return;
            setVisible(false);
        }, 3000);
    }

    function showControls() {
        setVisible(true);
        update();
        scheduleHide();
    }

    function closePanel() {
        panel = '';
        parts.panel.hidden = true;
        parts.volume.setAttribute('aria-expanded', 'false');
        parts.subtitles.setAttribute('aria-expanded', 'false');
        scheduleHide();
    }

    function showFeedback(text, timeout) {
        clearTimeout(feedbackTimer);
        setText(feedback, text);
        feedback.hidden = false;
        if (timeout) feedbackTimer = setTimeout(() => { feedback.hidden = true; }, timeout);
    }

    function cancelPreview() {
        if (gesture || pendingTap) suppressClickUntil = Date.now() + 800;
        cancelTap();
        gesture = null;
        progressVideo = null;
        preview = null;
        if (feedback) feedback.hidden = true;
    }

    function seek(item, position) {
        if (item !== currentVideo() || !seekable(item)) return false;
        try { item.currentTime = Math.max(0, Math.min(item.duration, position)); return true; }
        catch (_) { showFeedback('暂时无法跳转，请稍后重试', 1500); return false; }
    }

    function setAppearance(value) {
        appearance = value || { size: 100, opacity: 100 };
        if (!document.documentElement) return;
        const size = Number(appearance.size), opacity = Number(appearance.opacity);
        const styles = document.documentElement.style;
        let changed = false;
        [['--bilispeed-subtitle-size', String(Number.isFinite(size) ? Math.max(.7, Math.min(1.5, size / 100)) : 1)],
            ['--bilispeed-subtitle-opacity', String(Number.isFinite(opacity) ? Math.max(.2, Math.min(1, opacity / 100)) : 1)]].forEach(([key, text]) => {
            if (styles.getPropertyValue(key) !== text) { styles.setProperty(key, text); changed = true; }
        });
        if (changed && controls) updateLayout();
    }

    function cancelTap() {
        if (pendingTap) clearTimeout(pendingTap.timer);
        pendingTap = null;
    }

    function inputTime(event) {
        const time = Number(event.timeStamp);
        return Number.isFinite(time) && time > 0 ? time : performance.now();
    }

    function togglePlayback(withFeedback) {
        const active = currentVideo();
        if (!active || suspended) return;
        if (active.paused || active.ended) {
            active.play().then(() => {
                if (withFeedback && active === video && !suspended) showFeedback('继续播放', 900);
            }).catch(() => { update(); if (withFeedback && !suspended) showFeedback('暂时无法播放，请重试', 1500); });
        } else {
            active.pause();
            if (withFeedback) showFeedback('已暂停', 900);
        }
        if (withFeedback) showControls();
        else scheduleHide();
    }

    function singleTap() {
        if (!controls || suspended) return;
        if (panel) { closePanel(); showControls(); }
        else if (!fullscreen || controls.dataset.hidden === 'true') showControls();
        else setVisible(false);
    }

    function surfaceTap(x, y, at) {
        const active = currentVideo();
        if (!active || suspended) return;
        const previous = pendingTap;
        if (previous && previous.video === active && previous.source === active.currentSrc
                && at >= previous.at && at - previous.at <= 340 && Math.hypot(x - previous.x, y - previous.y) <= 32) {
            cancelTap();
            togglePlayback(true);
            return;
        }
        if (previous) { cancelTap(); if (!previous.handled) singleTap(); }
        const tap = { video: active, source: active.currentSrc, host, at, x, y, handled: false, timer: null };
        tap.timer = setTimeout(() => {
            if (pendingTap !== tap) return;
            // Keep one timestamp after the single-tap action so a queued second
            // contact can still match while video decoding delays JS dispatch.
            tap.handled = true;
            if (host === tap.host && currentVideo() === tap.video && tap.video.currentSrc === tap.source) singleTap();
        }, 340);
        pendingTap = tap;
    }

    function button(name, label, action) {
        const element = document.createElement('button');
        element.type = 'button';
        element.dataset.bilispeedControl = name;
        element.textContent = label;
        element.addEventListener('click', event => { event.preventDefault(); cancelTap(); action(); });
        return element;
    }

    function subtitleState() {
        const tracks = video ? Array.from(video.textTracks).filter(track => track.kind === 'subtitles' || track.kind === 'captions') : [];
        const languages = subtitleRoot ? Array.from(subtitleRoot.querySelectorAll('.bpx-player-ctrl-subtitle-major-content .bpx-player-ctrl-subtitle-language-item[data-lan]')) : [];
        const close = subtitleRoot && subtitleRoot.querySelector('.bpx-player-ctrl-subtitle-close-switch');
        const activeLanguage = languages.find(item => item.classList.contains('bpx-state-active'));
        return { tracks, languages, close, enabled: tracks.some(track => track.mode === 'showing') || !!(activeLanguage && !(close && close.classList.contains('bpx-state-active'))) };
    }

    function renderSubtitles() {
        if (suspended || !controls || panel !== 'subtitles') return;
        const state = subtitleState();
        const options = [];
        const off = button('subtitle-off', '关闭字幕', () => {
            state.tracks.forEach(track => { track.mode = 'disabled'; });
            if (state.close && !state.close.classList.contains('bpx-state-active')) state.close.click();
            update(); closePanel();
        });
        off.setAttribute('data-subtitle-option', '');
        off.setAttribute('aria-pressed', String(!state.enabled));
        options.push(off);
        state.tracks.forEach((track, index) => {
            const option = button('subtitle-track', track.label || track.language || '字幕 ' + (index + 1), () => {
                state.tracks.forEach(item => { item.mode = item === track ? 'showing' : 'disabled'; });
                update(); closePanel();
            });
            option.setAttribute('data-subtitle-option', '');
            option.setAttribute('aria-pressed', String(track.mode === 'showing'));
            options.push(option);
        });
        if (!state.tracks.length) state.languages.forEach(language => {
            const option = button('subtitle-language', language.textContent.trim(), () => {
                // Delegate to the site's real subtitle control, preserving its login/permission checks.
                if (language.isConnected) language.click();
                update(); closePanel();
            });
            option.setAttribute('data-subtitle-option', '');
            option.setAttribute('aria-pressed', String(state.enabled && language.classList.contains('bpx-state-active')));
            options.push(option);
        });
        if (!state.tracks.length && !state.languages.length) {
            const message = document.createElement('p');
            const login = subtitleRoot && subtitleRoot.querySelector('.bpx-player-ctrl-subtitle-language-unlogin');
            message.textContent = login && getComputedStyle(login).display !== 'none'
                ? '登录后可使用字幕，请从「我的」进入官方登录页面。' : '该视频暂无可用字幕。';
            options.push(message);
        }
        parts.subtitleOptions.replaceChildren(...options);
    }

    function togglePanel(name) {
        if (panel === name) { closePanel(); return; }
        panel = name;
        parts.panel.hidden = false;
        parts.volumePanel.hidden = name !== 'volume';
        parts.subtitlePanel.hidden = name !== 'subtitles';
        parts.volume.setAttribute('aria-expanded', String(name === 'volume'));
        parts.subtitles.setAttribute('aria-expanded', String(name === 'subtitles'));
        setText(parts.panelTitle, name === 'volume' ? '音量' : '字幕');
        showControls(); update();
        if (name === 'subtitles') renderSubtitles();
    }

    function createControls() {
        controls = document.createElement('div');
        controls.id = 'bilispeed-touch-controls';
        controls.setAttribute('role', 'group');
        controls.setAttribute('aria-label', '视频播放控制');
        ['click', 'dblclick', 'pointerdown', 'pointerup', 'mousedown', 'mouseup', 'touchstart', 'touchmove', 'touchend'].forEach(name => {
            controls.addEventListener(name, event => event.stopPropagation());
        });
        controls.addEventListener('pointerdown', () => { cancelTap(); keyboardInteraction = false; interacting = true; showControls(); });
        controls.addEventListener('touchstart', cancelTap, { passive: true });
        controls.addEventListener('keydown', event => {
            keyboardInteraction = true;
            showControls();
            if (event.key === 'Escape' && panel) { event.preventDefault(); event.stopPropagation(); closePanel(); }
        });
        controls.addEventListener('focusout', () => { keyboardInteraction = false; scheduleHide(); });
        const range = document.createElement('input');
        range.type = 'range'; range.min = '0'; range.max = '1000'; range.step = '1';
        range.dataset.bilispeedControl = 'seek';
        range.setAttribute('aria-label', '播放进度');
        range.addEventListener('pointerdown', () => { progressVideo = currentVideo(); progressSource = progressVideo ? progressVideo.currentSrc : ''; });
        range.addEventListener('input', () => {
            if (!progressVideo) { progressVideo = currentVideo(); progressSource = progressVideo ? progressVideo.currentSrc : ''; }
            if (seekable(progressVideo)) preview = Number(range.value) / 1000 * progressVideo.duration;
            update();
        });
        range.addEventListener('change', () => {
            if (progressVideo && progressVideo.currentSrc === progressSource && preview !== null) seek(progressVideo, preview);
            progressVideo = null; preview = null; update(); scheduleHide();
        });
        range.addEventListener('pointercancel', () => { cancelPreview(); update(); });
        const play = button('play', '▶', () => togglePlayback(false));
        const time = document.createElement('span');
        time.dataset.bilispeedControl = 'time';
        const volume = button('volume', '音量', () => togglePanel('volume'));
        const subtitles = button('subtitles', '字幕', () => togglePanel('subtitles'));
        const full = button('fullscreen', '全屏', () => {
            if (document.fullscreenElement) document.exitFullscreen().catch(() => update());
            else if (document.webkitFullscreenElement && document.webkitExitFullscreen) document.webkitExitFullscreen();
            else {
                const active = currentVideo();
                const target = active && (active.closest('.bpx-player-container') || document.getElementById('bilibili-player'));
                if (target && target.requestFullscreen) target.requestFullscreen().catch(() => showFeedback('暂时无法进入全屏', 1500));
            }
        });
        const settings = document.createElement('section');
        settings.id = 'bilispeed-player-panel'; settings.hidden = true;
        settings.setAttribute('aria-label', '播放设置');
        const header = document.createElement('header');
        const title = document.createElement('span');
        const close = button('close-panel', '完成', closePanel);
        header.append(title, close);
        const volumePanel = document.createElement('div');
        const volumeSlider = document.createElement('input');
        volumeSlider.type = 'range'; volumeSlider.min = '0'; volumeSlider.max = '100'; volumeSlider.step = '1';
        volumeSlider.dataset.bilispeedControl = 'volume-slider';
        volumeSlider.setAttribute('aria-label', '音量百分比');
        volumeSlider.addEventListener('input', () => {
            const active = currentVideo();
            if (!active) return;
            if (active.volume > 0) audibleVolumes.set(active, active.volume);
            active.volume = Math.max(0, Math.min(1, Number(volumeSlider.value) / 100));
            active.muted = active.volume === 0;
            update();
        });
        const volumeValue = document.createElement('output');
        const mute = button('mute', '静音', () => {
            const active = currentVideo();
            if (!active) return;
            if (active.muted || active.volume === 0) {
                if (active.volume === 0) active.volume = audibleVolumes.get(active) || 0.5;
                active.muted = false;
            } else { audibleVolumes.set(active, active.volume); active.muted = true; }
            update();
        });
        volumePanel.append(volumeSlider, volumeValue, mute);
        const subtitlePanel = document.createElement('div');
        subtitlePanel.hidden = true;
        settings.append(header, volumePanel, subtitlePanel);
        [volume, subtitles].forEach(item => { item.setAttribute('aria-controls', settings.id); item.setAttribute('aria-expanded', 'false'); });
        controls.append(range, play, time, volume, subtitles, full, settings);
        feedback = document.createElement('div');
        feedback.id = 'bilispeed-seek-feedback'; feedback.hidden = true;
        parts = { range, play, time, volume, subtitles, full, panel: settings, panelTitle: title,
            volumePanel, volumeSlider, volumeValue, mute, subtitlePanel, subtitleOptions: subtitlePanel };
    }

    function ignoreTarget(target) {
        return target instanceof Element && !!target.closest('#bilispeed-touch-controls, button, input, select, textarea, a, [role="button"], .bpx-player-control-wrap, .bpx-player-ending-wrap, .bpx-player-dm-setting, .bpx-player-dm-setting-wrap');
    }

    function bindHost(target) {
        if (managedHosts.has(target)) return;
        managedHosts.add(target);
        target.addEventListener('touchstart', event => {
            if (target !== host || suspended) return;
            if (event.touches.length !== 1) { cancelPreview(); update(); return; }
            if (ignoreTarget(event.target)) { cancelTap(); return; }
            // A new finger contact is an intentional tap, not the trailing click of a swipe.
            suppressClickUntil = 0;
            if (!video) return;
            const touch = event.touches[0];
            gesture = { id: touch.identifier, x: touch.clientX, y: touch.clientY, width: Math.max(1, host.getBoundingClientRect().width),
                video, source: video.currentSrc, time: video.currentTime, duration: video.duration,
                started: inputTime(event), moved: false, seeking: false, vertical: false };
        }, { capture: true, passive: true });
        target.addEventListener('touchmove', event => {
            if (target !== host || !gesture) return;
            if (event.touches.length !== 1) { cancelPreview(); update(); return; }
            const touch = Array.from(event.touches).find(item => item.identifier === gesture.id);
            if (!touch || gesture.vertical) return;
            const dx = touch.clientX - gesture.x, dy = touch.clientY - gesture.y;
            if (Math.hypot(dx, dy) >= 10) { gesture.moved = true; cancelTap(); }
            if (!seekable(gesture.video)) return;
            if (!gesture.seeking) {
                if (Math.abs(dx) < 10 && Math.abs(dy) < 10) return;
                if (Math.abs(dy) > Math.abs(dx) * 1.2) { gesture.vertical = true; return; }
                if (Math.abs(dx) < 10 || Math.abs(dx) < Math.abs(dy) * 1.2) return;
                gesture.seeking = true;
                closePanel();
            }
            event.preventDefault(); event.stopImmediatePropagation();
            preview = Math.max(0, Math.min(gesture.duration, gesture.time + dx / gesture.width * Math.min(120, gesture.duration)));
            const delta = Math.round(preview - gesture.time);
            showFeedback((delta >= 0 ? '快进 ' : '快退 ') + timeLabel(preview) + ' / ' + timeLabel(gesture.duration)
                + '（' + (delta >= 0 ? '+' : '') + delta + '秒）');
            update();
        }, { capture: true, passive: false });
        target.addEventListener('touchend', event => {
            if (target !== host || !gesture) return;
            const completed = gesture;
            if (event.touches.length) { cancelPreview(); update(); return; }
            if (completed.seeking) {
                event.preventDefault(); event.stopImmediatePropagation();
                suppressClickUntil = Date.now() + 800;
                if (completed.video.currentSrc === completed.source && preview !== null) seek(completed.video, preview);
                clearTimeout(feedbackTimer);
                feedbackTimer = setTimeout(() => { feedback.hidden = true; }, 600);
            } else if (!completed.moved && !completed.vertical && inputTime(event) - completed.started <= 250
                    && currentVideo() === completed.video && completed.video.currentSrc === completed.source) {
                // Consume both the touch and its compatibility click so the
                // official player cannot toggle again or enter fullscreen.
                event.preventDefault(); event.stopImmediatePropagation();
                suppressClickUntil = Date.now() + 800;
                surfaceTap(completed.x, completed.y, inputTime(event));
            } else {
                cancelTap();
                suppressClickUntil = Date.now() + 800;
            }
            gesture = null; preview = null; update(); scheduleHide();
        }, { capture: true, passive: false });
        target.addEventListener('touchcancel', () => { if (target === host) { cancelPreview(); update(); scheduleHide(); } }, { passive: true });
        target.addEventListener('click', event => {
            if (target !== host || ignoreTarget(event.target)) return;
            if (Date.now() < suppressClickUntil) { event.preventDefault(); event.stopImmediatePropagation(); return; }
            event.preventDefault(); event.stopImmediatePropagation();
            surfaceTap(event.clientX, event.clientY, inputTime(event));
        }, true);
        target.addEventListener('dblclick', event => {
            if (target === host && !ignoreTarget(event.target)) { event.preventDefault(); event.stopImmediatePropagation(); }
        }, true);
    }

    function updateLayout() {
        if (suspended || !video || !document.documentElement) return;
        const wrap = document.getElementById('playerWrap');
        if (wrap && !fullscreen) {
            const width = wrap.clientWidth || innerWidth;
            const viewportHeight = window.visualViewport ? window.visualViewport.height : innerHeight;
            const ratio = video.videoWidth && video.videoHeight ? video.videoWidth / video.videoHeight : 16 / 9;
            const height = Math.round(Math.max(120, Math.min(width / ratio, Math.max(160, viewportHeight * .68))));
            const sending = document.querySelector('#bilibili-player .bpx-player-sending-bar');
            const styles = document.documentElement.style;
            [['--bilispeed-video-height', height + 'px'], ['--bilispeed-sending-height', (sending ? sending.getBoundingClientRect().height : 0) + 'px']].forEach(([key, value]) => {
                if (styles.getPropertyValue(key) !== value) styles.setProperty(key, value);
            });
        }
        if (host) {
            const value = host.clientHeight + 'px';
            if (host.style.getPropertyValue('--bilispeed-player-height') !== value) host.style.setProperty('--bilispeed-player-height', value);
            if (controls) {
                const height = controls.offsetHeight + 'px';
                if (host.style.getPropertyValue('--bilispeed-controls-height') !== height) host.style.setProperty('--bilispeed-controls-height', height);
            }
        }
    }

    function update() {
        if (suspended || !controls || !video) return;
        const full = isFullscreen();
        if (fullscreen !== full) {
            fullscreen = full; cancelPreview(); closePanel(); clearTimeout(hideTimer);
            document.documentElement.toggleAttribute('data-bilispeed-fullscreen', full);
            setVisible(!full);
            updateLayout();
        }
        // Fullscreen controls are usually hidden. Catch up when shown instead
        // of changing their range, labels and subtitle queries on timeupdate.
        if (controls.dataset.hidden === 'true' && !panel && preview === null && !gesture) return;
        const playing = !video.paused && !video.ended;
        setText(parts.play, playing ? 'Ⅱ' : '▶');
        setAttribute(parts.play, 'aria-label', playing ? '暂停视频' : '播放视频');
        const canSeek = seekable(video);
        if (parts.range.disabled === canSeek) parts.range.disabled = !canSeek;
        const position = preview !== null ? preview : video.currentTime;
        const progress = String(canSeek ? Math.round(position / video.duration * 1000) : 0);
        if (parts.range.value !== progress) parts.range.value = progress;
        setAttribute(parts.range, 'aria-valuetext', timeLabel(position) + ' / ' + timeLabel(video.duration));
        setText(parts.time, video.duration === Infinity ? '直播' : timeLabel(position) + ' / ' + timeLabel(video.duration));
        if (parts.time.title !== parts.time.textContent) parts.time.title = parts.time.textContent;
        setText(parts.full, full ? '退出' : '全屏');
        setAttribute(parts.full, 'aria-label', full ? '退出全屏' : '进入全屏');
        const volume = Math.round((video.muted ? 0 : video.volume) * 100);
        if (parts.volumeSlider.value !== String(volume)) parts.volumeSlider.value = volume;
        setAttribute(parts.volumeSlider, 'aria-valuetext', volume + '%');
        setText(parts.volumeValue, volume + '%');
        setText(parts.volume, volume === 0 ? '静音' : '音量');
        setAttribute(parts.volume, 'aria-label', '调节音量，当前 ' + volume + '%');
        setAttribute(parts.mute, 'aria-pressed', String(volume === 0));
        setText(parts.mute, volume === 0 ? '取消静音' : '静音');
        const subtitles = subtitleState();
        setAttribute(parts.subtitles, 'aria-pressed', String(subtitles.enabled));
        setAttribute(parts.subtitles, 'aria-label', subtitles.enabled ? '字幕已开启，点击选择' : '开启或选择字幕');
    }

    function refresh() {
        if (suspended || !document.head) return;
        // The player can appear before the surrounding Vue page has hydrated.
        // Its controls are extra DOM children too, so share the same mount gate.
        if (document.querySelector('#mirror-vdcon') && !window.__BiliTouch.isPageReady()) return;
        setAppearance(appearance);
        if (!document.getElementById('bilispeed-player-style')) {
            const style = document.createElement('style');
            style.id = 'bilispeed-player-style'; style.textContent = stylesheet; document.head.appendChild(style);
        }
        const active = currentVideo();
        if (!active) {
            if (host) { host.removeAttribute('data-bilispeed-player'); host.removeAttribute('data-controls-visible'); }
            if (controls) controls.remove();
            if (feedback) feedback.remove();
            cancelPreview(); clearTimeout(hideTimer); clearTimeout(feedbackTimer); subtitleObserver.disconnect(); subtitleRoot = null;
            video = null; host = null; panel = '';
            return;
        }
        const target = active.closest('.bpx-player-video-area') || document.getElementById('bilibili-player');
        const changed = video !== active || host !== target;
        if (changed) {
            cancelPreview(); interacting = false;
            if (host && host !== target) { host.removeAttribute('data-bilispeed-player'); host.removeAttribute('data-controls-visible'); }
            video = active; host = target;
        }
        host.setAttribute('data-bilispeed-player', '');
        if (!controls) createControls();
        if (controls.parentElement !== host) { host.append(controls, feedback); setVisible(!isFullscreen()); }
        if (changed) closePanel();
        bindHost(host);
        if (!managedVideos.has(active)) {
            managedVideos.add(active);
            ['timeupdate', 'play', 'pause', 'ended', 'loadedmetadata', 'durationchange', 'volumechange', 'seeking', 'seeked'].forEach(name => {
                active.addEventListener(name, () => { if (active === video) { update(); if (name === 'loadedmetadata') updateLayout(); } }, { passive: true });
            });
            ['loadstart', 'emptied'].forEach(name => active.addEventListener(name, () => { if (active === video) { cancelPreview(); closePanel(); update(); } }, { passive: true }));
            ['addtrack', 'removetrack', 'change'].forEach(name => active.textTracks.addEventListener(name, () => {
                if (active === video) { update(); if (panel === 'subtitles') renderSubtitles(); }
            }));
        }
        const official = document.querySelector('#bilibili-player .bpx-player-ctrl-subtitle');
        if (official !== subtitleRoot) {
            subtitleObserver.disconnect(); subtitleRoot = official;
            if (official) subtitleObserver.observe(official, { subtree: true, childList: true, attributes: true, attributeFilter: ['class', 'hidden', 'data-lan'] });
            if (panel === 'subtitles') renderSubtitles();
        }
        update(); updateLayout();
    }

    function finishInteraction() { interacting = false; scheduleHide(); }
    function resized() { cancelPreview(); interacting = false; refresh(); scheduleHide(); }
    function setSuspended(value) {
        const next = !!value;
        if (next === suspended) return;
        suspended = next;
        if (suspended) {
            cancelPreview(); interacting = false; keyboardInteraction = false;
            clearTimeout(hideTimer); clearTimeout(feedbackTimer);
            subtitleObserver.disconnect(); subtitleRoot = null;
        } else { refresh(); scheduleHide(); }
    }
    Object.defineProperty(window, '__BiliTouchPlayer', { value: Object.freeze({ refresh,
        setSuspended, setAppearance,
        setFullscreen(value) { nativeFullscreen = !!value; update(); }
    }), configurable: false });
    window.addEventListener('pointerup', finishInteraction, true);
    window.addEventListener('pointercancel', () => { cancelPreview(); finishInteraction(); update(); }, true);
    window.addEventListener('resize', resized);
    window.addEventListener('orientationchange', resized);
    window.addEventListener('pagehide', () => { cancelPreview(); clearTimeout(hideTimer); clearTimeout(feedbackTimer); });
    window.addEventListener('pageshow', refresh);
    if (window.visualViewport) window.visualViewport.addEventListener('resize', resized);
    document.addEventListener('fullscreenchange', () => { update(); updateLayout(); });
    document.addEventListener('webkitfullscreenchange', () => { update(); updateLayout(); });
    document.addEventListener('DOMContentLoaded', refresh, { once: true });
    setAppearance(appearance);
    refresh();
})();
