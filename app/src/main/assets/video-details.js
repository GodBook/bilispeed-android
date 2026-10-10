(function () {
    'use strict';
    if (!window.__BiliTouch || window.__BiliTouchVideo) return;
    let key = '';
    let data = null;
    let pending = null;
    let failed = false;
    let suspended = !!window.__BILI_SPEED_SUSPENDED__;
    let rendered = '';
    let panel = null;
    let navigation = { previous: null, next: null };
    const css = `
#bilispeed-episodes { display: none; }
html[data-bilispeed-touch] #bilispeed-episodes {
    display: block; order: 4 !important; margin: 8px 12px; padding: 0;
    color: #18191c; font: 14px/1.5 sans-serif;
    min-width: 0; box-sizing: border-box;
}
html[data-bilispeed-touch] #bilispeed-episodes[hidden] { display: none; }
#bilispeed-episodes .episode-group { padding: 12px; border-radius: 10px; background: #f6f7f8; }
#bilispeed-episodes .episode-group + .episode-group { margin-top: 12px; }
#bilispeed-episodes header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 8px; }
#bilispeed-episodes h2 { font-size: 16px; line-height: 24px; margin: 0; font-weight: 600; overflow-wrap: anywhere; }
#bilispeed-episodes .episode-kind { display: block; color: #61666d; font-size: 12px; font-weight: 400; }
#bilispeed-episodes .episode-count { color: #9499a0; white-space: nowrap; font-size: 13px; }
#bilispeed-episodes .episode-list { position: relative; }
#bilispeed-episodes .episode-list[data-expanded] { max-height: min(480px, 60vh); overflow-y: auto; overscroll-behavior: contain; }
#bilispeed-episodes a { display: flex; align-items: center; gap: 10px; min-height: 48px; color: inherit;
    text-decoration: none; touch-action: manipulation; border-radius: 5px; padding: 8px; box-sizing: border-box; }
#bilispeed-episodes a[hidden] { display: none; }
#bilispeed-episodes a[aria-current] { color: #00a1d6; background: #eaf6fb; }
#bilispeed-episodes a:active { background: #eaf6fb; }
#bilispeed-episodes a:focus-visible, #bilispeed-episodes button:focus-visible { outline: 2px solid #00a1d6; outline-offset: -2px; }
#bilispeed-episodes .episode-index { flex: none; min-width: 20px; color: #9499a0; font-size: 12px; font-variant-numeric: tabular-nums; }
#bilispeed-episodes .episode-title { flex: 1; min-width: 0; display: -webkit-box; -webkit-box-orient: vertical;
    -webkit-line-clamp: 2; overflow: hidden; overflow-wrap: anywhere; }
#bilispeed-episodes time { color: #9499a0; font-size: 12px; font-variant-numeric: tabular-nums; flex: none; }
#bilispeed-episodes button { border: 0; color: #00a1d6; background: transparent; min-height: 44px; padding: 0 10px; font: inherit; }
#bilispeed-episodes .episode-actions { display: flex; justify-content: space-between; gap: 8px; border-top: 1px solid #e3e5e7; margin-top: 8px; }
#bilispeed-episodes p { margin: 0; color: #9499a0; }
html[data-bilispeed-touch][data-bilispeed-episodes-ready] #mirror-vdcon :is(.video-pod-above-modules, #multi_page, .video-sections) { display: none !important; }
html[data-bilispeed-touch] #mirror-vdcon :is(.up-panel-container, .video-pod, .video-sections, .video-desc-container) { max-width: 100%; box-sizing: border-box; }
`;

    function route() {
        if (!['www.bilibili.com', 'bilibili.com'].includes(location.hostname)) return '';
        const match = location.pathname.match(/^\/video\/(BV[0-9A-Za-z]{10}|av\d+)(?:\/|$)/);
        return match ? match[1] : '';
    }

    function matches(video, identity) {
        return video && (video.bvid === identity || 'av' + video.aid === identity);
    }

    function restoreImages() {
        if (!data) return;
        const avatar = document.querySelector('#mirror-vdcon .up-avatar');
        if (avatar && !avatar.querySelector('img:not([data-bilispeed-image])') && data.owner && data.owner.face) {
            try {
                const url = new URL(data.owner.face, location.href);
                if (['http:', 'https:'].includes(url.protocol) && /(^|\.)hdslb\.com$/.test(url.hostname)) {
                    url.protocol = 'https:';
                    const image = avatar.querySelector('[data-bilispeed-image]') || node('img');
                    image.setAttribute('data-bilispeed-image', '');
                    if (image.src !== url.href) image.src = url.href;
                    image.alt = data.owner.name || 'UP主头像'; image.referrerPolicy = 'no-referrer';
                    image.style.cssText = 'width:100%;height:100%;object-fit:cover;border-radius:50%';
                    if (!image.parentElement) avatar.appendChild(image);
                }
            } catch (_) { }
        }
        // Lazy image components can remain placeholders after the columns are
        // stacked. Restore their declared CDN source, without changing links.
        document.querySelectorAll('#mirror-vdcon img').forEach(image => {
            const source = image.getAttribute('data-src') || image.getAttribute('data-original') || image.getAttribute('src');
            if (!source) return;
            try {
                const url = new URL(source, location.href);
                if (!['http:', 'https:'].includes(url.protocol) || !/(^|\.)hdslb\.com$/.test(url.hostname)) return;
                url.protocol = 'https:';
                if (image.src !== url.href) image.src = url.href;
            } catch (_) { }
        });
    }

    function node(tag, className, value) {
        const result = document.createElement(tag);
        if (className) result.className = className;
        if (value != null) result.textContent = String(value);
        return result;
    }

    function ensurePanel() {
        const toolbar = document.querySelector('#mirror-vdcon .video-toolbar-container');
        if (!toolbar) return false;
        if (!document.getElementById('bilispeed-episodes-style')) {
            const style = node('style'); style.id = 'bilispeed-episodes-style'; style.textContent = css; document.head.appendChild(style);
        }
        const current = document.getElementById('bilispeed-episodes');
        if (current !== panel || !panel) {
            panel = current || node('section'); panel.id = 'bilispeed-episodes';
            panel.setAttribute('aria-label', '视频选集'); rendered = '';
        }
        if (panel.parentElement !== toolbar.parentElement) toolbar.after(panel);
        return true;
    }

    function renderStatus(message, retry) {
        if (!panel) return;
        panel.hidden = false; panel.removeAttribute('data-ready');
        panel.replaceChildren(node('p', '', message));
        if (retry) {
            const button = node('button', '', '重新加载选集'); button.type = 'button';
            button.addEventListener('click', () => { failed = false; refresh(); }); panel.appendChild(button);
        }
    }

    function duration(seconds) {
        const value = Math.max(0, Math.floor(Number(seconds) || 0));
        const minutes = Math.floor(value / 60);
        return (value >= 3600 ? Math.floor(value / 3600) + ':' + String(minutes % 60).padStart(2, '0') : minutes)
            + ':' + String(value % 60).padStart(2, '0');
    }

    function groups(video) {
        const result = [];
        const pages = Array.isArray(video.pages) ? video.pages : [];
        if (pages.length > 1) {
            result.push({ title: '分集', kind: 'parts', items: pages.map((page, index) => ({
                bvid: video.bvid, aid: video.aid, page: Number(page.page) || index + 1,
                title: page.part || '第 ' + (index + 1) + ' 集', duration: page.duration
            })) });
        }
        const season = video.ugc_season;
        if (season && Array.isArray(season.sections)) {
            season.sections.forEach(section => {
                if (!Array.isArray(section.episodes)) return;
                const items = section.episodes.map(episode => ({
                    bvid: episode.bvid || episode.arc && episode.arc.bvid,
                    aid: episode.aid || episode.arc && episode.arc.aid, page: 1,
                    title: episode.title || episode.arc && episode.arc.title || '视频',
                    duration: episode.arc && episode.arc.duration || episode.page && episode.page.duration
                })).filter(item => /^BV[0-9A-Za-z]{10}$/.test(item.bvid || '') || Number(item.aid) > 0);
                if (items.length) result.push({ kind: 'collection', title: (season.title || '视频合集')
                    + (season.sections.length > 1 && section.title ? ' · ' + section.title : ''), items });
            });
        }
        return result;
    }

    function episodeUrl(item) {
        const id = /^BV[0-9A-Za-z]{10}$/.test(item.bvid || '') ? item.bvid : 'av' + Number(item.aid);
        const url = new URL('https://www.bilibili.com/video/' + id + '/');
        if (item.page > 1) url.searchParams.set('p', item.page);
        return url.href;
    }

    function adjacent(sections, page) {
        const parts = sections.find(section => section.kind === 'parts');
        const part = parts ? parts.items.findIndex(item => item.page === page) : -1;
        const collection = sections.filter(section => section.kind === 'collection').flatMap(section => section.items);
        const episode = collection.findIndex(item => matches(item, key));
        const target = item => item ? { url: episodeUrl(item), title: item.title } : null;
        return {
            previous: target(part > 0 ? parts.items[part - 1] : (page === 1 && episode > 0 ? collection[episode - 1] : null)),
            next: target(part >= 0 && part < parts.items.length - 1 ? parts.items[part + 1]
                : ((!parts || part === parts.items.length - 1) && episode >= 0 ? collection[episode + 1] : null))
        };
    }

    function render() {
        if (!data || !panel) return;
        const page = Math.max(1, Number(new URL(location.href).searchParams.get('p')) || 1);
        const signature = key + ':' + page;
        if (rendered === signature) return;
        const sections = groups(data);
        navigation = adjacent(sections, page);
        panel.replaceChildren();
        panel.hidden = sections.length === 0;
        panel.toggleAttribute('data-ready', sections.length > 0);
        document.documentElement.toggleAttribute('data-bilispeed-episodes-ready', sections.length > 0);
        sections.forEach((section, groupIndex) => {
            const group = node('section', 'episode-group');
            group.dataset.episodeKind = section.kind;
            const header = node('header');
            const active = section.items.findIndex(item => matches(item, key) && (section.kind === 'collection' || item.page === page));
            const title = node('h2', '', section.title);
            const heading = node('div');
            if (section.kind === 'collection') heading.appendChild(node('span', 'episode-kind', '合集'));
            heading.appendChild(title);
            header.append(heading, node('span', 'episode-count',
                active < 0 ? section.items.length + ' 集' : (active + 1) + ' / ' + section.items.length));
            const list = node('div', 'episode-list');
            list.id = 'bilispeed-episode-list-' + groupIndex;
            list.setAttribute('aria-label', section.title);
            const start = Math.max(0, Math.min(active - 2, section.items.length - 5));
            section.items.forEach((item, index) => {
                // Real anchors deliberately reload the official player. Some
                // desktop collection rows only have a mouse/Vue click handler.
                const link = node('a'); link.href = episodeUrl(item); link.title = item.title;
                link.hidden = index < start || index >= start + 5;
                if (index === active) link.setAttribute('aria-current', 'true');
                link.append(node('span', 'episode-index', index + 1), node('span', 'episode-title', item.title), node('time', '', duration(item.duration)));
                list.appendChild(link);
            });
            group.append(header, list);
            if (section.items.length > 5) {
                const actions = node('div', 'episode-actions');
                const toggle = node('button', '', '展开全部 ' + section.items.length + ' 集'); toggle.type = 'button';
                toggle.setAttribute('aria-controls', list.id); toggle.setAttribute('aria-expanded', 'false');
                toggle.setAttribute('data-episode-toggle', '');
                const locate = node('button', '', '定位当前集'); locate.type = 'button'; locate.hidden = true;
                const positionCurrent = () => {
                    const selected = list.querySelector('[aria-current]');
                    if (selected) list.scrollTop = Math.max(0, selected.offsetTop - (list.clientHeight - selected.offsetHeight) / 2);
                };
                toggle.addEventListener('click', () => {
                    const expanded = list.toggleAttribute('data-expanded');
                    Array.from(list.children).forEach((link, index) => { link.hidden = !expanded && (index < start || index >= start + 5); });
                    toggle.setAttribute('aria-expanded', String(expanded));
                    toggle.textContent = expanded ? '收起选集' : '展开全部 ' + section.items.length + ' 集';
                    locate.hidden = !expanded || active < 0;
                    if (expanded) positionCurrent(); else list.scrollTop = 0;
                });
                locate.addEventListener('click', positionCurrent);
                actions.append(toggle, locate); group.appendChild(actions);
            }
            panel.appendChild(group);
        });
        rendered = signature;
        if (window.__BiliTouch) window.__BiliTouch.refresh();
    }

    async function request(identity) {
        const controller = new AbortController(); pending = controller;
        const timeout = setTimeout(() => controller.abort(), 12000);
        try {
            const parameter = identity.startsWith('BV') ? 'bvid=' + identity : 'aid=' + identity.slice(2);
            const response = await fetch('https://api.bilibili.com/x/web-interface/view?' + parameter,
                { credentials: 'include', signal: controller.signal });
            if (!response.ok) throw new Error('HTTP ' + response.status);
            const value = await response.json();
            if (pending !== controller || identity !== route() || suspended) return;
            if (value.code !== 0 || !matches(value.data, identity)) throw new Error('Video unavailable');
            data = value.data; restoreImages(); render();
        } catch (_) {
            if (pending !== controller || identity !== route() || suspended) return;
            failed = true; renderStatus('选集暂时加载失败，请重试', true);
        } finally { clearTimeout(timeout); if (pending === controller) pending = null; }
    }

    function refresh() {
        if (suspended || !window.__BiliTouch.isPageReady()) return;
        const identity = route();
        if (identity !== key) {
            if (pending) pending.abort(); pending = null;
            key = identity; data = null; failed = false; rendered = '';
            navigation = { previous: null, next: null };
            document.documentElement.removeAttribute('data-bilispeed-episodes-ready');
            if (panel) { panel.hidden = true; panel.removeAttribute('data-ready'); }
        }
        if (!identity || !ensurePanel()) return;
        if (data) { restoreImages(); render(); return; }
        const initial = window.__INITIAL_STATE__ && window.__INITIAL_STATE__.videoData;
        if (matches(initial, identity) && Array.isArray(initial.pages)
                && (!initial.season_id || initial.ugc_season && Array.isArray(initial.ugc_season.sections))) {
            data = initial; restoreImages(); render(); return;
        }
        if (pending || failed) return;
        renderStatus('正在加载选集…', false); request(identity);
    }

    function setSuspended(value) {
        suspended = !!value;
        if (suspended && pending) { const old = pending; pending = null; old.abort(); }
        if (!suspended) refresh();
    }
    Object.defineProperty(window, '__BiliTouchVideo', { value: Object.freeze({ refresh, setSuspended,
        getNavigation() {
            const page = Math.max(1, Number(new URL(location.href).searchParams.get('p')) || 1);
            return !suspended && key === route() && rendered === key + ':' + page ? navigation : { previous: null, next: null };
        }
    }) });
    document.addEventListener('DOMContentLoaded', refresh, { once: true });
    window.addEventListener('popstate', refresh);
    window.addEventListener('hashchange', refresh);
    refresh();
})();
