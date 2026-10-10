(function () {
    'use strict';
    if (window.__BiliSpeed) return;

    const initial = Number(window.__BILI_SPEED_INITIAL__);
    let wanted = valid(initial) ? initial : 1;
    let lastReport = '';
    let lastSent = 0;
    let scheduled = false;
    let suspended = !!window.__BILI_SPEED_SUSPENDED__;
    let heartbeatTimer = null;
    const videos = new Set();
    const pendingRoots = new Set();
    const managedFrames = new Set();
    const observers = new Map();
    const bound = new WeakSet();
    const roots = new WeakSet();
    const frames = new WeakSet();
    const animatedOverlays = '#bilispeed-touch-controls, #bilispeed-seek-feedback, '
        + '.bpx-player-dm-wrap, .bpx-player-dm-container, .bpx-player-subtitle-wrap';
    const prototype = HTMLMediaElement.prototype;
    const rateDescriptor = Object.getOwnPropertyDescriptor(prototype, 'playbackRate');
    const defaultDescriptor = Object.getOwnPropertyDescriptor(prototype, 'defaultPlaybackRate');

    function showMobileWebPlayer() {
        if (!document.documentElement) return;
        const mobileVideo = location.hostname === 'm.bilibili.com' && location.pathname.startsWith('/video/');
        const attribute = 'data-bilispeed-webplayer';
        if (!mobileVideo) {
            document.documentElement.removeAttribute(attribute);
            return;
        }
        // The mobile site's search layout hides its already mounted HTML5 player
        // and displays an app-launch poster. Use the site's existing web-player layout.
        if (!document.querySelector('.video-share .m-video-player')) return;
        const changed = !document.documentElement.hasAttribute(attribute);
        if (changed) document.documentElement.setAttribute(attribute, '');
        if (!document.getElementById('bilispeed-mobile-player-style') && document.head) {
            const style = document.createElement('style');
            style.id = 'bilispeed-mobile-player-style';
            style.textContent = 'html[' + attribute + '] .video-share{display:block!important;}' +
                'html[' + attribute + '] .video-natural-search{display:none!important;}';
            document.head.appendChild(style);
        }
        if (changed) setTimeout(() => window.dispatchEvent(new Event('resize')), 50);
        document.querySelectorAll('.video-share .m-video-player').forEach(player => {
            const video = player.querySelector('video');
            if (!video) return;
            let button = player.querySelector('[data-bilispeed-play]');
            if (!button) {
                button = document.createElement('button');
                button.setAttribute('data-bilispeed-play', '');
                button.setAttribute('aria-label', '播放视频');
                button.type = 'button';
                button.textContent = '▶';
                button.style.cssText = 'position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);' +
                    'width:58px;height:58px;border:0;border-radius:50%;background:#e8557f;color:white;' +
                    'font-size:24px;padding:0 0 0 4px;z-index:2147483000;cursor:pointer;';
                button.addEventListener('click', event => {
                    event.preventDefault();
                    event.stopPropagation();
                    const current = player.querySelector('video');
                    if (current) {
                        apply(current);
                        current.play().then(() => report(true)).catch(() => report(true));
                    }
                });
                player.appendChild(button);
            }
            const display = video.paused || video.ended ? 'block' : 'none';
            if (button.style.display !== display) button.style.display = display;
        });
    }

    function valid(rate) {
        return Number.isFinite(rate) && rate >= 0.25 && rate <= 5;
    }

    function isLive(video) {
        return video.duration === Infinity;
    }

    // Use the original setters to avoid a ratechange/reapply loop. Only video is locked.
    function apply(video) {
        const rate = isLive(video) ? 1 : wanted;
        try {
            if (Math.abs(video.playbackRate - rate) > 0.001) {
                rateDescriptor.set.call(video, rate);
            }
            if (defaultDescriptor && Math.abs(video.defaultPlaybackRate - rate) > 0.001) {
                defaultDescriptor.set.call(video, rate);
            }
            video.preservesPitch = true;
            if ('webkitPreservesPitch' in video) video.webkitPreservesPitch = true;
            if (suspended && !video.paused) video.pause();
        } catch (_) {
            // The snapshot reports the actual rate if a particular player rejects it.
        }
    }

    function guard(property, descriptor) {
        if (!descriptor || !descriptor.configurable || !descriptor.set) return;
        try {
            Object.defineProperty(prototype, property, {
                configurable: true,
                enumerable: descriptor.enumerable,
                get: descriptor.get,
                set: function (value) {
                    return descriptor.set.call(this,
                        this.tagName === 'VIDEO' ? (isLive(this) ? 1 : wanted) : value);
                }
            });
        } catch (_) {
            // Event handlers and the low-frequency heartbeat cover non-configurable engines.
        }
    }

    guard('playbackRate', rateDescriptor);
    guard('defaultPlaybackRate', defaultDescriptor);

    const play = prototype.play;
    prototype.play = function () {
        if (this.tagName === 'VIDEO' && suspended) {
            return Promise.reject(new DOMException('Playback is paused while the app is in the background', 'AbortError'));
        }
        return play.apply(this, arguments);
    };

    function snapshot() {
        const present = Array.from(videos).filter(video => video.isConnected);
        const playable = present.filter(video => video.readyState >= 1 && !video.error);
        const active = playable.find(video => !video.paused && !video.ended) || playable[0] || present[0];
        return {
            type: 'state',
            videos: present.length,
            ready: playable.length > 0,
            playing: playable.some(video => !video.paused && !video.ended),
            live: !!active && isLive(active),
            rate: active ? active.playbackRate : wanted,
            selected: wanted,
            suspended,
            version: 2
        };
    }

    function report(force) {
        const now = Date.now();
        const message = JSON.stringify(snapshot());
        if (!force && message === lastReport && now - lastSent < 2000) return;
        if (!force && now - lastSent < 150) return;
        lastReport = message;
        lastSent = now;
        try {
            if (window.BiliSpeedBridge) window.BiliSpeedBridge.postMessage(message);
        } catch (_) {}
    }

    function bind(video) {
        videos.add(video);
        if (!bound.has(video)) {
            bound.add(video);
            ['loadedmetadata', 'loadeddata', 'durationchange', 'play', 'playing',
                'ratechange', 'emptied', 'pause', 'ended', 'error'].forEach(name => {
                video.addEventListener(name, function () {
                    apply(video);
                    report(true);
                    scheduleReport();
                }, { passive: true });
            });
        }
        apply(video);
    }

    function trustedOrigin(origin) {
        try {
            const url = new URL(origin);
            return url.protocol === 'https:' &&
                (url.hostname === 'bilibili.com' || url.hostname.endsWith('.bilibili.com'));
        } catch (_) { return false; }
    }

    function sendToFrames(message) {
        managedFrames.forEach(frame => {
            if (!frame.isConnected) { managedFrames.delete(frame); return; }
            try {
                // Older WebViews can still control a same-origin child without a bridge.
                if (frame.contentWindow.__BiliSpeed) {
                    if (message.type === 'config') frame.contentWindow.__BiliSpeed.configure(message);
                    if (message.type === 'pause') frame.contentWindow.__BiliSpeed.pause();
                    return;
                }
            } catch (_) { }
            try {
                const origin = new URL(frame.src || location.href, location.href).origin;
                if (trustedOrigin(origin)) frame.contentWindow.postMessage(message, origin);
            } catch (_) { }
        });
    }

    function frameConfig() { return { type: 'config', rate: wanted, suspended }; }

    function bindFrame(frame) {
        managedFrames.add(frame);
        if (frames.has(frame)) return;
        frames.add(frame);
        frame.addEventListener('load', () => sendToFrames(frameConfig()));
    }

    function discover(root) {
        if (!root || ![1, 9, 11].includes(root.nodeType)) return;
        if (root.tagName === 'VIDEO') bind(root);
        if (root.tagName === 'IFRAME') bindFrame(root);
        if (root.querySelectorAll) {
            root.querySelectorAll('*').forEach(element => {
                if (element.tagName === 'VIDEO') bind(element);
                if (element.tagName === 'IFRAME') bindFrame(element);
                if (element.shadowRoot) watch(element.shadowRoot);
            });
        }
        if (root.shadowRoot) watch(root.shadowRoot);
    }

    function watch(root) {
        discover(root);
        if (roots.has(root)) return;
        roots.add(root);
        // Native autoplay does not call the JavaScript play() method.
        root.addEventListener('play', blockBackgroundPlay, true);
        const observer = new MutationObserver(records => {
            let changed = false;
            records.forEach(record => {
                // Progress labels, subtitles and flying danmaku can change many
                // times per second; they do not mount media players.
                const target = record.target.nodeType === 1 ? record.target : record.target.parentElement;
                if (target && target.closest(animatedOverlays)) return;
                record.addedNodes.forEach(node => {
                    if ((node.nodeType === 1 || node.nodeType === 11)
                            && !(node.matches && node.matches(animatedOverlays))) {
                        pendingRoots.add(node);
                        changed = true;
                    }
                });
                if (record.removedNodes.length && (videos.size || managedFrames.size || observers.size > 1)) changed = true;
            });
            if (changed) scheduleReport();
        });
        observer.observe(root, { childList: true, subtree: true });
        observers.set(root, observer);
    }

    function blockBackgroundPlay(event) {
        if (suspended && event.target.tagName === 'VIDEO') event.target.pause();
    }

    function prune() {
        videos.forEach(video => { if (!video.isConnected) videos.delete(video); });
        managedFrames.forEach(frame => { if (!frame.isConnected) managedFrames.delete(frame); });
        observers.forEach((observer, root) => {
            if (root.host && !root.host.isConnected) {
                observer.disconnect();
                observers.delete(root);
                roots.delete(root);
            }
        });
    }

    function scheduleReport() {
        if (scheduled) return;
        scheduled = true;
        setTimeout(() => {
            scheduled = false;
            pendingRoots.forEach(root => {
                if (!root.isConnected) return;
                // An ancestor already covers this subtree; scan it only once per batch.
                for (let parent = root.parentNode; parent; parent = parent.parentNode) {
                    if (pendingRoots.has(parent)) return;
                }
                discover(root);
            });
            pendingRoots.clear();
            showMobileWebPlayer();
            prune();
            report(false);
        }, 100);
    }

    // Catch open shadow roots even when attached to an already connected element.
    const attachShadow = Element.prototype.attachShadow;
    if (attachShadow) {
        Element.prototype.attachShadow = function (options) {
            const root = attachShadow.call(this, options);
            if (options.mode === 'open') watch(root);
            return root;
        };
    }

    function setRate(rate) {
        const number = Number(rate);
        if (!valid(number)) return false;
        const changed = Math.abs(wanted - number) > .001;
        wanted = Math.round(number * 100) / 100;
        videos.forEach(apply);
        sendToFrames(frameConfig());
        report(changed);
        return true;
    }

    function configure(message) {
        if (typeof message.suspended === 'boolean') suspended = message.suspended;
        if (window.__BiliTouch) window.__BiliTouch.setSuspended(suspended);
        if (message.buttons && window.__BiliTouchPlayer) window.__BiliTouchPlayer.setAppearance(message.buttons);
        if (suspended) stopHeartbeat(); else scheduleHeartbeat();
        return setRate(message.rate);
    }

    function pause() {
        suspended = true;
        if (window.__BiliTouch) window.__BiliTouch.setSuspended(true);
        stopHeartbeat();
        videos.forEach(video => { try { video.pause(); } catch (_) {} });
        sendToFrames({ type: 'pause' });
        report(true);
    }

    function resume() {
        configure({ rate: wanted, suspended: false });
    }

    function stopHeartbeat() {
        if (heartbeatTimer !== null) clearTimeout(heartbeatTimer);
        heartbeatTimer = null;
    }

    function scheduleHeartbeat() {
        if (heartbeatTimer !== null || suspended) return;
        heartbeatTimer = setTimeout(() => {
            heartbeatTimer = null;
            if (suspended) return;
            showMobileWebPlayer();
            prune();
            videos.forEach(apply);
            report(false);
            scheduleHeartbeat();
        }, videos.size ? 1500 : 3000);
    }

    const api = Object.freeze({ setRate, configure, snapshot, pause, resume });
    Object.defineProperty(window, '__BiliSpeed', { value: api, configurable: false });

    if (window.BiliSpeedBridge) {
        window.BiliSpeedBridge.onmessage = event => {
            try {
                const message = JSON.parse(event.data);
                if (message.type === 'config') configure(message);
                if (message.type === 'pause') pause();
            } catch (_) {}
        };
    }

    window.addEventListener('message', event => {
        if (event.source !== window.parent || window.parent === window || !trustedOrigin(event.origin)) return;
        if (event.data && event.data.type === 'config') configure(event.data);
        if (event.data && event.data.type === 'pause') pause();
    });

    watch(document);
    document.addEventListener('DOMContentLoaded', scheduleReport, { once: true });
    document.addEventListener('visibilitychange', () => {
        // WebView can be hidden while its custom fullscreen video is still visible.
        // Only the native activity's suspended state stops the heartbeat.
        scheduleHeartbeat();
        if (!document.hidden) report(true);
    });
    window.addEventListener('pageshow', () => { videos.forEach(apply); scheduleHeartbeat(); report(true); });
    scheduleReport();
    scheduleHeartbeat();
    report(true);
})();
