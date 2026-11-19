(function () {
    'use strict';
    if (window !== window.top || window.__BiliFullscreenGestures) return;

    const excluded = 'button, input, select, textarea, a, [role="button"], [role="slider"], '
        + '#bilispeed-touch-controls, .bpx-player-control-wrap, .bpx-player-ending-wrap, '
        + '.bpx-player-dm-setting, .bpx-player-dm-setting-wrap, .bpx-player-contextmenu';
    const feedbackId = 'bilispeed-fullscreen-level';
    let fullscreen = false;
    let suspended = !!window.__BILI_SPEED_SUSPENDED__;
    let contact = null;
    let sequence = 0;
    let brightnessReply = 0;
    let suppressClickUntil = 0;
    let feedback = null;
    let feedbackTimer = null;

    function installStyles() {
        if (!document.head || document.getElementById('bilispeed-fullscreen-gesture-style')) return;
        const style = document.createElement('style');
        style.id = 'bilispeed-fullscreen-gesture-style';
        style.textContent = `:fullscreen { touch-action: none; }
            #${feedbackId} { position: fixed; top: max(16%, env(safe-area-inset-top)); left: 50%;
                transform: translateX(-50%); z-index: 2147483647; padding: 8px 16px; border-radius: 8px;
                background: rgba(24, 25, 28, .94); color: #fff; font: 500 15px/1.5 system-ui, sans-serif;
                font-variant-numeric: tabular-nums; white-space: nowrap; pointer-events: none; }
            #${feedbackId}[hidden] { display: none !important; }`;
        document.head.appendChild(style);
    }

    function surface() {
        return document.fullscreenElement || document.webkitFullscreenElement || document.getElementById('bilibili-player');
    }

    function showLevel(label, percent) {
        const player = surface();
        if (!player || player instanceof HTMLVideoElement) return;
        if (!feedback) {
            feedback = document.createElement('div');
            feedback.id = feedbackId;
            feedback.setAttribute('role', 'status');
        }
        if (feedback.parentNode !== player) player.appendChild(feedback);
        const text = label + ' ' + percent + '%';
        if (feedback.textContent !== text) feedback.textContent = text;
        feedback.hidden = false;
        clearTimeout(feedbackTimer);
        feedbackTimer = setTimeout(() => { feedback.hidden = true; }, 950);
    }

    function reset() {
        contact = null;
        brightnessReply = 0;
        clearTimeout(feedbackTimer);
        if (feedback) feedback.hidden = true;
    }

    function start(event) {
        if (!event.isTrusted || !fullscreen || suspended) return;
        reset();
        if (event.touches.length !== 1) return;
        suppressClickUntil = 0;
        if (!(event.target instanceof Element) || event.target.closest(excluded)
                || document.querySelector('.bpx-player-contextmenu.bpx-player-active')) return;
        const player = surface();
        if (!player || !player.contains(event.target)) return;
        const videos = player instanceof HTMLVideoElement ? [player] : Array.from(player.querySelectorAll('video'));
        const ready = videos.filter(video => video.readyState >= 1 && video.getBoundingClientRect().width > 0);
        const video = ready.find(item => !item.paused && !item.ended) || ready[0];
        if (!video) return;
        const touch = event.touches[0];
        const viewport = window.visualViewport;
        const midpoint = viewport ? viewport.offsetLeft + viewport.width / 2 : window.innerWidth / 2;
        contact = { id: touch.identifier, x: touch.clientX, y: touch.clientY,
            height: Math.max(1, viewport ? viewport.height : window.innerHeight),
            brightness: touch.clientX < midpoint, video, src: video.currentSrc,
            volume: video.muted ? 0 : video.volume, lastDelta: null, adjusting: false, sequence: ++sequence };
    }

    function applyLevel(current, y) {
        const delta = Math.max(-100, Math.min(100, Math.round((current.y - y) * 100 / (current.height * .75))));
        if (delta === current.lastDelta) return;
        const first = current.lastDelta === null;
        current.lastDelta = delta;
        if (current.brightness) {
            brightnessReply = current.sequence;
            window.BiliSpeedBridge.postMessage(JSON.stringify({ type: 'fullscreen-brightness',
                id: current.sequence, start: first, delta }));
        } else {
            const volume = Math.max(0, Math.min(1, current.volume + delta / 100));
            if (current.video.volume !== volume) current.video.volume = volume;
            if (current.video.muted !== (volume === 0)) current.video.muted = volume === 0;
            showLevel('音量', Math.round(volume * 100));
        }
    }

    function consume(event) {
        event.preventDefault();
        event.stopImmediatePropagation();
        suppressClickUntil = Date.now() + 800;
    }

    function move(event) {
        if (!contact || !event.isTrusted) return;
        const current = contact;
        if (event.touches.length !== 1 || !current.video.isConnected || current.video.currentSrc !== current.src) {
            reset();
            return;
        }
        const touch = event.touches[0];
        if (touch.identifier !== current.id) return;
        if (!current.adjusting) {
            const dx = Math.abs(touch.clientX - current.x), dy = Math.abs(touch.clientY - current.y);
            if (dx >= 10 && dx >= dy * 1.2) { contact = null; return; }
            if (dy < 10 || dy <= dx * 1.2) return;
            if (current.brightness && !window.BiliSpeedBridge) { contact = null; return; }
            current.adjusting = true;
            window.__BiliTouchPlayer?.cancelGesture();
        }
        consume(event);
        applyLevel(current, touch.clientY);
    }

    function end(event) {
        if (!contact || !event.isTrusted) return;
        const current = contact;
        contact = null;
        if (!current.adjusting) return;
        consume(event);
        const touch = event.changedTouches[0];
        if (touch && touch.identifier === current.id && current.video.isConnected && current.video.currentSrc === current.src) {
            applyLevel(current, touch.clientY);
        }
    }

    function suppressSwipeClick(event) {
        if (event.isTrusted && event.detail > 0 && Date.now() < suppressClickUntil) consume(event);
    }

    function setFullscreen(value) {
        if (fullscreen === value) return;
        reset();
        suppressClickUntil = 0;
        fullscreen = value;
        if (fullscreen) {
            installStyles();
            document.addEventListener('touchstart', start, { capture: true, passive: true });
            document.addEventListener('touchmove', move, { capture: true, passive: false });
            document.addEventListener('touchend', end, { capture: true, passive: false });
            document.addEventListener('touchcancel', reset, true);
            document.addEventListener('click', suppressSwipeClick, true);
        } else {
            document.removeEventListener('touchstart', start, true);
            document.removeEventListener('touchmove', move, true);
            document.removeEventListener('touchend', end, true);
            document.removeEventListener('touchcancel', reset, true);
            document.removeEventListener('click', suppressSwipeClick, true);
        }
    }

    Object.defineProperty(window, '__BiliFullscreenGestures', { value: Object.freeze({ setFullscreen,
        setSuspended(value) { suspended = value; if (value) reset(); },
        onBrightness(message) {
            if (fullscreen && !suspended && brightnessReply && message.id === brightnessReply) showLevel('亮度', message.percent);
        }
    }) });
    document.addEventListener('fullscreenchange', () => { reset(); if (fullscreen) installStyles(); });
    document.addEventListener('DOMContentLoaded', () => { if (fullscreen) installStyles(); }, { once: true });
    window.addEventListener('pagehide', reset);
    setFullscreen(!!window.__BILI_TOUCH_FULLSCREEN__);
})();
