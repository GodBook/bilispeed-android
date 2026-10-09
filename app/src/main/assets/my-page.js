(function () {
    'use strict';
    const login = 'https://account.bilibili.com/account/home';
    const element = id => document.getElementById(id);
    const text = (id, value) => { element(id).textContent = value == null ? '—' : String(value); };
    let loggedIn = false;
    let generation = 0;
    let pending = null;

    async function api(path, signal) {
        const response = await fetch('https://api.bilibili.com' + path, { credentials: 'include', signal, cache: 'no-store' });
        if (!response.ok) throw new Error('HTTP ' + response.status);
        return response.json();
    }

    function resetProfile() {
        loggedIn = false;
        text('username', '登录 / 注册'); text('member', '登录后查看账号');
        ['bcoin', 'coins', 'dynamics', 'following', 'followers'].forEach(id => text(id, null));
        element('level').hidden = true; element('avatar').hidden = true;
        element('avatar').removeAttribute('src'); element('avatar-placeholder').hidden = false;
        ['avatar-link', 'username', 'space', 'dynamic-link', 'following-link', 'follower-link', 'favorites'].forEach(id => { element(id).href = login; });
    }

    function renderProfile(data) {
        const mid = String(data.mid || '');
        if (!/^\d+$/.test(mid) || mid === '0') throw new Error('Invalid account');
        loggedIn = true;
        const space = 'https://space.bilibili.com/' + mid;
        text('username', data.uname); text('coins', data.money);
        text('bcoin', data.wallet && data.wallet.bcoin_balance != null ? Number(data.wallet.bcoin_balance).toFixed(1) : null);
        const level = data.level_info && data.level_info.current_level;
        element('level').hidden = level == null;
        text('level', 'LV' + (level == null ? '' : level));
        text('member', data.isSeniorMember ? '硬核会员' : data.email_verified || Number(level) > 0 ? '正式会员' : '注册会员');
        ['avatar-link', 'space'].forEach(id => { element(id).href = space; });
        element('dynamic-link').href = space + '/dynamic';
        element('following-link').href = space + '/fans/follow';
        element('follower-link').href = space + '/fans/fans';
        element('favorites').href = space + '/favlist';
        try {
            const face = new URL(data.face);
            if (['http:', 'https:'].includes(face.protocol) && /(^|\.)hdslb\.com$/.test(face.hostname)) {
                face.protocol = 'https:'; element('avatar').src = face.href;
            }
        } catch (_) { }
    }

    async function load() {
        const current = ++generation;
        if (pending) pending.abort();
        const controller = new AbortController(); pending = controller;
        const timeout = setTimeout(() => controller.abort(), 12000);
        text('status', '正在加载账号信息…'); element('retry').hidden = true;
        resetProfile();
        try {
            const nav = await api('/x/web-interface/nav', controller.signal);
            if (current !== generation) return;
            if (nav.code === -101 || nav.code === 0 && nav.data && nav.data.isLogin === false) {
                text('status', '登录后同步历史记录、收藏与账号信息'); return;
            }
            if (nav.code !== 0 || !nav.data || !nav.data.isLogin) throw new Error('Account unavailable');
            renderProfile(nav.data);
            const stats = await api('/x/web-interface/nav/stat', controller.signal);
            if (current !== generation) return;
            if (stats.code === -101) { resetProfile(); text('status', '登录已失效，请重新登录'); return; }
            if (stats.code !== 0 || !stats.data) throw new Error('Statistics unavailable');
            text('dynamics', stats.data.dynamic_count); text('following', stats.data.following); text('followers', stats.data.follower);
            text('status', '');
        } catch (_) {
            if (current !== generation) return;
            text('status', loggedIn ? '关注与动态数据暂时加载失败' : '账号信息暂时加载失败，请检查网络');
            element('retry').hidden = false;
        } finally { clearTimeout(timeout); if (pending === controller) pending = null; }
    }

    element('avatar').addEventListener('load', () => {
        if (!loggedIn || !element('avatar').getAttribute('src') || !element('avatar').naturalWidth) return;
        element('avatar').hidden = false; element('avatar-placeholder').hidden = true;
    });
    element('avatar').addEventListener('error', () => { element('avatar').hidden = true; element('avatar-placeholder').hidden = false; });
    element('retry').addEventListener('click', load);
    document.querySelectorAll('[data-account-link]').forEach(link => link.addEventListener('click', event => {
        if (!loggedIn) { event.preventDefault(); location.href = login; }
    }));
    function night(value) {
        document.body.classList.toggle('night', value);
        element('night').setAttribute('aria-pressed', String(value));
    }
    try { night(localStorage.getItem('bilispeed-profile-night') === 'true'); } catch (_) { }
    element('night').addEventListener('click', () => {
        const value = !document.body.classList.contains('night'); night(value);
        try { localStorage.setItem('bilispeed-profile-night', String(value)); } catch (_) { }
    });
    element('offline').addEventListener('click', () => element('offline-dialog').showModal());
    element('open-link').addEventListener('click', () => { text('link-error', ''); element('link-dialog').showModal(); });
    document.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click', () => button.closest('dialog').close()));
    element('link-form').addEventListener('submit', event => {
        event.preventDefault();
        const value = element('video-link').value.trim();
        const match = value.match(/https?:\/\/[^\s<>]+/i);
        const bv = value.match(/\bBV[0-9A-Za-z]{10}\b/);
        try {
            const url = new URL(match ? match[0] : bv ? 'https://www.bilibili.com/video/' + bv[0] : value);
            if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.port
                    || !/(^|\.)bilibili\.com$/.test(url.hostname) && url.hostname !== 'b23.tv') throw new Error('Invalid link');
            url.protocol = 'https:'; location.href = url.href;
        } catch (_) { text('link-error', '请输入有效的 B站链接或 BV 号'); }
    });
    window.addEventListener('pagehide', () => { ++generation; if (pending) pending.abort(); });
    window.addEventListener('pageshow', event => { if (event.persisted) load(); });
    load();
})();
