package app.bilispeed.browser;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewGroup;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONTokener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real WebView geometry and Android touch regression for official account markup.
 * Does not modify account data, Cookies or preferences. layoutWidth=320 also runs on a phone. */
public class AccountPagesInstrumentationTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;

    @Before public void launch() {
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        String width = InstrumentationRegistry.getArguments().getString("layoutWidth");
        if (width != null) instrumentation.runOnMainSync(() -> {
            ViewGroup.LayoutParams params = activity.browserForTesting().getLayoutParams();
            params.width = Math.round(Integer.parseInt(width) * activity.getResources().getDisplayMetrics().density);
            activity.browserForTesting().setLayoutParams(params);
        });
    }

    @After public void finish() {
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }

    private Object js(String expression) throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> activity.browserForTesting().evaluateJavascript(expression, value -> {
            result.set(value); ready.countDown();
        }));
        assertTrue("WebView response", ready.await(8, TimeUnit.SECONDS));
        return new JSONTokener(result.get()).nextValue();
    }

    private void await(String expression) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 12000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (Boolean.TRUE.equals(js("Boolean(" + expression + ")"))) return;
            SystemClock.sleep(100);
        }
        fail("Account layout did not settle: " + expression);
    }

    private void fixture(String origin, String css, String body) throws Exception {
        CountDownLatch committed = new CountDownLatch(1);
        Object bridge = new Object() { @android.webkit.JavascriptInterface public void ready() { committed.countDown(); } };
        String html = "<!doctype html><meta name='viewport' content='width=1100'><style>body{margin:0;min-width:1100px}"
                + "*{box-sizing:border-box}button{min-height:34px}input{min-width:0}" + css + "</style><div id='app'>" + body
                + "</div><script>addEventListener('load',()=>AccountFixtureLoaded.ready())</script>";
        instrumentation.runOnMainSync(() -> {
            activity.browserForTesting().addJavascriptInterface(bridge, "AccountFixtureLoaded");
            activity.browserForTesting().stopLoading();
            activity.browserForTesting().loadDataWithBaseURL(origin, html, "text/html", "UTF-8", null);
        });
        try { assertTrue("Fixture commit", committed.await(15, TimeUnit.SECONDS)); }
        finally { instrumentation.runOnMainSync(() -> activity.browserForTesting().removeJavascriptInterface("AccountFixtureLoaded")); }
        await("window.__BiliTouch && document.documentElement.hasAttribute('data-bilispeed-touch')");
        String requestedWidth = InstrumentationRegistry.getArguments().getString("layoutWidth");
        int actualWidth = ((Number) js("innerWidth")).intValue();
        if (requestedWidth != null) assertEquals("Actual narrow WebView viewport", Integer.parseInt(requestedWidth), actualWidth, 1);
        System.out.println("BILISPEED_ACCOUNT_VIEWPORT=" + actualWidth);
    }

    private void inside(String selector) throws Exception {
        String quoted = org.json.JSONObject.quote(selector);
        assertEquals("Clipped or missing " + selector, true, js("(()=>{let a=Array.from(document.querySelectorAll(" + quoted
                + "));return a.length>0&&a.every(e=>{let r=e.getBoundingClientRect();return r.width>0&&r.left>=-1&&r.right<=innerWidth+2})})()"));
    }

    private void tap(String selector) throws Exception {
        String quoted = org.json.JSONObject.quote(selector);
        js("document.querySelector(" + quoted + ").scrollIntoView({block:'center',behavior:'instant'});true");
        SystemClock.sleep(150);
        JSONArray point = (JSONArray) js("(()=>{let e=document.querySelector(" + quoted + "),r=e.getBoundingClientRect();"
                + "return [r.x+r.width/2,r.y+r.height/2]})()");
        int[] offset = new int[2];
        instrumentation.runOnMainSync(() -> activity.browserForTesting().getLocationOnScreen(offset));
        float density = activity.getResources().getDisplayMetrics().density;
        float x = offset[0] + (float) point.getDouble(0) * density;
        float y = offset[1] + (float) point.getDouble(1) * density;
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 70, MotionEvent.ACTION_UP, x, y, 0);
        try { instrumentation.sendPointerSync(down); instrumentation.sendPointerSync(up); }
        finally { down.recycle(); up.recycle(); }
        instrumentation.waitForIdleSync();
    }

    @Test public void historyToolbarDatesAndCardsFitAndFiltersRemainTouchable() throws Exception {
        String card = "<div class='history-card grid-mode'><div class='history-card__left'><div class='history-card__main'>"
                + "<a class='bili-video-card' href='#video'>实际历史视频</a></div></div></div>";
        fixture("https://www.bilibili.com/history", ".history-record,.main-head{width:1012px;min-width:1012px}"
                + ".main-head,.breadcrumbs__top,.left,.right,.tabs,.radio-tabs{display:flex}.breadcrumbs__top{width:800px;margin-left:162px}"
                + ".history-list{width:872px;margin-left:90px}.history-timeline-item{display:flex}.history-section{width:800px;margin-left:72px}"
                + ".history-timeline-label{position:absolute;width:0}.section-title{position:absolute;left:-32px}.section-cards{display:grid;grid-template-columns:repeat(3,1fr)}"
                + ".history-card{width:256px;min-height:100px}.history-card__left,.history-card__main{width:256px}"
                + ".filters{display:none}.search-bar{display:flex;width:34px}.input-wrap{max-width:0}.radio-tabs__item{padding:12px}",
                "<main class='history-record'><div class='main-head'><h1 class='main-title'>历史记录</h1><div class='main-actions'>记录开关</div></div>"
                + "<div class='main-breadcrumbs'><div class='breadcrumbs'><div class='breadcrumbs__top'><div class='left'><div class='tabs'>"
                + "<div class='radio-tabs'><div class='radio-tabs__item'>综合</div><div class='radio-tabs__item'>视频</div><div class='radio-tabs__item'>直播</div><div class='radio-tabs__item'>专栏</div></div>"
                + "<button id='filter' class='filter-switcher vui_button' onclick=\"document.querySelector('.filters').style.display='flex'\">更多筛选</button></div></div>"
                + "<div class='right'><label class='search-bar folded'><div class='input-wrap'><input class='search-bar-input' aria-label='搜索历史'></div><button>搜索</button></label>"
                + "<button class='vui_button'>清空历史</button><button class='vui_button'>批量管理</button></div></div>"
                + "<div class='breadcrumbs__bottom'><div class='filters'><div class='radio-filter'><button>全部时长</button><button>超过60分钟</button></div></div></div></div></div>"
                + "<div class='history-list'><div class='history-timeline'><div class='history-timeline-item'><div class='history-timeline-label'><div class='section-label'><div class='section-title'>今天</div></div></div>"
                + "<div class='history-timeline-anchor'></div><div class='history-section'><div class='section-cards grid-mode'>" + card + card + card + "</div></div></div></div></div></main>");
        assertEquals("history", js("document.documentElement.dataset.bilispeedPage"));
        inside(".main-head, .breadcrumbs__top, .history-card, .section-title, .right button, .search-bar-input");
        assertEquals(true, js("(()=>{let a=document.querySelectorAll('.history-card');return Math.abs(a[0].offsetTop-a[1].offsetTop)<2&&a[2].offsetTop>a[0].offsetTop})()"));
        await("document.querySelector('#filter').getBoundingClientRect().width>0");
        tap("#filter");
        await("getComputedStyle(document.querySelector('.filters')).display==='flex'");
        inside(".filters, .filters button");
        js("document.querySelector('.section-cards').className='section-cards list-mode';true");
        assertEquals(true, js("document.querySelector('.history-card').getBoundingClientRect().width>innerWidth*.8"));
    }

    @Test public void legacyHistoryRouteAndWatchlaterEmptyAndBothCardModesFit() throws Exception {
        fixture("https://www.bilibili.com/account/history", "", "<main class='history-record'>历史记录</main>");
        assertEquals("history", js("document.documentElement.dataset.bilispeedPage"));
        String card = "<div class='video-card video-card--grid'><div class='video-card__wrap'><div class='video-card__left'>封面</div><div class='video-card__right'>长标题与UP主</div></div></div>";
        fixture("https://www.bilibili.com/watchlater/list", ".watchlater-list{width:1060px}.watchlater-list-grid{display:grid;grid-template-columns:repeat(4,1fr)}"
                + ".video-card__wrap{display:flex}.video-card__left{width:178px;height:100px}.video-card__right{width:240px}.watchlater-list-empty{width:1060px;margin:120px 0}"
                + ".watchlater-list-empty__img{width:140px;height:140px}",
                "<main class='watchlater-list'><div class='watchlater-list-title'><div class='watchlater-list-title-left'>稍后再看</div><div class='watchlater-list-title-right'>排序</div></div>"
                + "<div class='watchlater-list-grid'>" + card + card + card + "</div><div class='watchlater-list-empty'><div class='watchlater-list-empty__img'>空状态</div></div></main>");
        assertEquals("watchlater", js("document.documentElement.dataset.bilispeedPage"));
        inside(".watchlater-list, .watchlater-list-title, .watchlater-list-grid > *, .video-card__left, .video-card__right, .watchlater-list-empty, .watchlater-list-empty__img");
        js("document.querySelector('.watchlater-list').classList.add('watchlater-list--vertical');document.querySelectorAll('.video-card').forEach(e=>e.className='video-card video-card--list');true");
        inside(".video-card__left, .video-card__right");
        assertEquals(true, js("document.querySelector('.video-card').getBoundingClientRect().width>innerWidth*.8"));
        assertEquals(true, js("parseFloat(getComputedStyle(document.querySelector('.watchlater-list-empty')).marginTop)<50"));
    }

    @Test public void favoriteFoldersExpandSelectAndRemountWithoutDuplicateControls() throws Exception {
        String library = "<div class='space-favlist'><aside class='favlist-aside'><div class='vui_collapse'><div class='fav-collapse'><div class='vui_sidebar'>"
                + "<div class='vui_sidebar-item vui_sidebar-item--active'><span class='vui_ellipsis'>默认收藏夹</span></div>"
                + "<div id='folder-two' class='vui_sidebar-item' onclick=\"document.querySelector('.vui_sidebar-item--active').classList.remove('vui_sidebar-item--active');this.classList.add('vui_sidebar-item--active')\"><span class='vui_ellipsis'>第二个收藏夹</span></div>"
                + "</div></div></div></aside><div class='favlist-main'><div class='fav-list-main'><div class='fav-list-header'><div class='fav-list-header-filter'>"
                + "<div class='fav-list-header-filter__left'><div class='radio-filter'>全部视频</div></div><div class='fav-list-header-filter__right'>"
                + "<input class='fav-list-header-filter__search' aria-label='搜索收藏'></div></div></div><div class='items'>"
                + "<div class='items__item'><a class='bili-video-card' href='#one'>收藏视频一</a></div><div class='items__item'>收藏视频二</div></div></div></div></div>";
        fixture("https://space.bilibili.com/1/favlist", ".space-main{width:1100px}.space-favlist{display:flex;width:980px}.favlist-aside{width:195px}"
                + ".favlist-main{width:769px}.vui_sidebar-item{height:48px}.items{display:grid;grid-template-columns:repeat(4,1fr)}.items__item{min-height:80px}",
                "<main class='space-main'>" + library + "</main>");
        await("document.querySelector('#bilispeed-folder-toggle')");
        assertEquals("false", js("document.querySelector('#bilispeed-folder-toggle').getAttribute('aria-expanded')"));
        assertEquals("none", js("getComputedStyle(document.querySelector('.favlist-aside')).display"));
        inside("#bilispeed-folder-toggle, .favlist-main, .items__item, .fav-list-header-filter__search");
        tap("#bilispeed-folder-toggle");
        await("document.querySelector('#bilispeed-folder-toggle').getAttribute('aria-expanded')==='true'");
        inside(".favlist-aside, #folder-two");
        tap("#folder-two");
        await("document.querySelector('#bilispeed-folder-toggle').getAttribute('aria-expanded')==='false'");
        await("document.querySelector('#bilispeed-folder-toggle').textContent.includes('第二个收藏夹')");
        js("window.__BiliTouch.setSuspended(true);var fresh=document.querySelector('.space-favlist').cloneNode(true);fresh.querySelector('#bilispeed-folder-toggle').remove();fresh.removeAttribute('data-bilispeed-folders');document.querySelector('.space-favlist').replaceWith(fresh);true");
        assertEquals(false, js("!!document.querySelector('#bilispeed-folder-toggle')"));
        js("window.__BiliTouch.setSuspended(false);window.__BiliTouch.refresh();true");
        await("document.querySelector('#bilispeed-folder-toggle')");
        assertEquals(1, ((Number) js("document.querySelectorAll('#bilispeed-folder-toggle').length")).intValue());
    }

    private String dynamicCard() {
        return "<article class='bili-dyn-item'><div class='bili-dyn-item__main'><div class='bili-dyn-item__avatar'><div class='bili-dyn-avatar'>头像</div></div>"
                + "<div class='bili-dyn-item__header'>UP主昵称<div class='bili-dyn-item__desc'>时间</div></div><div class='bili-dyn-item__body'>"
                + "<div class='bili-dyn-content'><div class='bili-dyn-content__orig'><a class='bili-dyn-card-video' href='#video'><div class='bili-dyn-card-video__header'>封面</div>"
                + "<div class='bili-dyn-card-video__body'><div class='bili-dyn-card-video__title'>很长的视频标题</div><div class='bili-dyn-card-video__stat'>1.3万</div></div></a></div></div></div>"
                + "<div class='bili-dyn-item__footer'><div class='bili-dyn-item__action'><button>转发</button></div><div class='bili-dyn-item__action'><button id='comment' onclick='window.commentOpened=true'>评论</button></div>"
                + "<div class='bili-dyn-item__action'><button>点赞</button></div></div></div></article>";
    }

    @Test public void memberDynamicColumnVideoAndActionsFitAndCommentCanBeTapped() throws Exception {
        fixture("https://t.bilibili.com/", ".bili-dyn-home--member{display:flex;width:1100px}.bili-dyn-home--member>aside{width:240px}"
                + ".bili-dyn-home--member>main{width:556px}.bili-dyn-item__main{padding-left:88px}.bili-dyn-item__header,.bili-dyn-item__body,.bili-dyn-item__footer{width:448px}"
                + ".bili-dyn-card-video{width:448px;display:flex}.bili-dyn-card-video__header{width:200px}.bili-dyn-card-video__body{width:248px}"
                + ".bili-dyn-item__footer{display:flex}.bili-dyn-item__action{width:104px}"
                + ".bili-album__preview.grid3{display:grid;width:404px;grid-template-columns:repeat(3,1fr);gap:4px}.bili-album__preview__picture{width:132px;height:132px}",
                "<div class='bili-dyn-home--member'><aside class='left'>电脑侧栏</aside><main><section>" + dynamicCard() + "</section></main><aside class='right'>趋势</aside></div>");
        assertEquals("none", js("getComputedStyle(document.querySelector('aside.left')).display"));
        inside(".bili-dyn-home--member > main, .bili-dyn-item, .bili-dyn-card-video, .bili-dyn-card-video__body, .bili-dyn-item__action button");
        js("var album=document.createElement('div');album.className='bili-album__preview grid3';album.innerHTML='<div class=bilispeed-fixture-photo></div>'.repeat(6);"
                + "album.querySelectorAll('div').forEach(e=>e.className='bili-album__preview__picture');document.querySelector('.bili-dyn-item__body').append(album);true");
        inside(".bili-album__preview, .bili-album__preview__picture");
        assertEquals(true, js("(()=>{let r=document.querySelector('.bili-album__preview__picture').getBoundingClientRect();return Math.abs(r.width-r.height)<2})()"));
        tap("#comment");
        await("window.commentOpened");
    }

    @Test public void dynamicDetailAndLateShadowCommentsFit() throws Exception {
        fixture("https://t.bilibili.com/123456", ".content,.card{width:632px}.bili-dyn-item{width:632px}",
                "<div class='content'><div class='card'>" + dynamicCard() + "</div><bili-comments></bili-comments></div>");
        inside("#app > .content, .card, .bili-dyn-item, .bili-dyn-card-video");
        js("var root=document.querySelector('bili-comments').attachShadow({mode:'open'});var actions=document.createElement('bili-comment-action-buttons-renderer');root.append(actions);"
                + "actions.attachShadow({mode:'open'}).innerHTML='<style>:host{display:flex;width:210px}</style><div id=pubdate>日期</div><div id=reply><button>回复</button></div>';true");
        await("actions.shadowRoot.getElementById('bilispeed-comment-style')");
    }

    @Test public void spaceHomeAndFollowingKeepContentAndAccountActionsInsidePhone() throws Exception {
        fixture("https://space.bilibili.com/1/relation/follow", ".space-main{width:1100px}.space-follow{display:flex}.follow-aside{width:195px}.follow-main{width:600px;margin-left:20px}"
                + ".relation-content-header{display:flex}.relation-content-header__right{width:300px}.relation-content .items{display:grid;grid-template-columns:repeat(2,1fr)}"
                + ".relation-card{display:flex}.relation-card-avatar{width:80px;height:80px}.relation-card-info{width:200px}",
                "<main class='space-main'><div class='space-follow'><aside class='follow-aside'>关注分组</aside><div class='follow-main'><div class='follow-main-title'>全部关注</div>"
                + "<section class='relation-content'><div class='relation-content-header'><div class='relation-content-header__left'>最近关注 最常访问</div>"
                + "<div class='relation-content-header__right'><input class='fav-list-header-filter__search' aria-label='搜索关注'></div></div><div class='items'><div class='item'>"
                + "<div class='relation-card'><a class='relation-card-avatar'>头像</a><div class='relation-card-info'>UP主<button>已关注</button></div></div></div></div></section></div></div></main>");
        inside(".follow-aside, .follow-main, .relation-content-header__right, .relation-card, .relation-card-info, .relation-card-info button");
        fixture("https://space.bilibili.com/1/relation/fans", ".space-fans{display:flex}.relation-aside{width:195px}.fans-main{flex:1}.relation-card{display:flex}",
                "<main class='space-main'><div class='space-fans'><aside class='relation-aside'>关注与粉丝分类</aside><div class='fans-main'><div class='fans-main-head'>我的粉丝</div>"
                + "<section class='relation-content'><div class='items'><div class='item'><div class='relation-card'>粉丝账号</div></div></div></section></div></div></main>");
        inside(".relation-aside, .fans-main, .relation-card");
        assertEquals(true, js("document.querySelector('.fans-main').getBoundingClientRect().width>innerWidth*.8"));
        fixture("https://space.bilibili.com/1", ".space-home{display:flex}.space-home>.content{width:720px}.space-home>.aside{width:240px}",
                "<main class='space-main'><div class='space-home'><div class='content'>投稿与动态</div><div class='aside'>账号资料</div></div></main>");
        inside(".space-home > .content, .space-home > .aside");
        assertEquals(true, js("document.querySelector('.space-home>.aside').getBoundingClientRect().top>=document.querySelector('.space-home>.content').getBoundingClientRect().bottom"));
    }

    @Test public void spaceUploadsPaginationAndPrivacySectionsFitNarrowPhone() throws Exception {
        String card = "<div class='upload-video-card grid-mode'><a class='bili-video-card'>投稿视频</a></div>";
        fixture("https://space.bilibili.com/1/upload/video", ".space-upload{display:flex}.upload-sidenav{width:150px;min-height:300px}.upload-content{width:740px}"
                + ".video-header__bottom{display:flex}.video-order-filter{display:flex}.video-body .video-list{display:grid;grid-template-columns:repeat(4,1fr)}"
                + ".vui_pagenation{display:flex;width:740px}.vui_pagenation--btns{display:flex;gap:8px}.vui_pagenation--btn{width:80px}.side-nav__item{width:150px;height:52px}",
                "<main class='space-main'><div class='space-upload'><aside class='upload-sidenav'><div class='side-nav'><div class='side-nav__item'>视频</div><div class='side-nav__item'>图文</div></div></aside>"
                + "<div class='upload-content'><div class='video-header'><div class='video-header__bottom'><div class='radio-filter video-order-filter'><button>最新发布</button><button>最多播放</button><button>最多收藏</button></div></div></div>"
                + "<div class='video-body'><div class='video-list grid-mode'>" + card + card + card + "</div></div><div class='vui_pagenation'><div class='vui_pagenation--btns'>"
                + "<button class='vui_pagenation--btn'>上一页</button><button class='vui_pagenation--btn'>1</button><button class='vui_pagenation--btn'>2</button><button class='vui_pagenation--btn'>3</button><button class='vui_pagenation--btn'>下一页</button>"
                + "</div></div></div></div></main>");
        inside(".upload-content, .upload-video-card, .video-header__bottom button, .vui_pagenation--btn");
        assertEquals(true, js("(()=>{let a=document.querySelectorAll('.upload-video-card');return Math.abs(a[0].offsetTop-a[1].offsetTop)<2&&a[2].offsetTop>a[0].offsetTop})()"));
        fixture("https://space.bilibili.com/1/settings", ".order-wrap{width:568px}.tags-wrap{width:562px}.privacy{display:grid;grid-template-columns:repeat(2,320px)}.switches,.switch-item,.switch-item>.wrap{width:320px}",
                "<main class='space-main'><div class='space-settings'><div class='section switch-wrap'><div class='privacy'><div class='switches'><div class='switch-item'><div class='wrap'><button>公开关注</button></div></div></div><div class='switches'><div class='switch-item'><div class='wrap'><button>公开收藏</button></div></div></div></div></div>"
                + "<div class='section order-wrap'><div class='title-wrap'>模块顺序</div><div class='column'>拖动排序</div></div><div class='section tags-wrap'><div class='content'><input aria-label='标签'></div></div></div></main>");
        inside(".space-settings .section, .switches, .switch-item, .switch-item>.wrap, .switches button, .column, .tags-wrap input");
    }

    @Test public void teleportedAccountDialogKeepsBothActionsInsidePhone() throws Exception {
        fixture("https://www.bilibili.com/history", ".vui_dialog--wrapper{position:fixed;inset:0;width:100%;height:100%}"
                + ".vui_dialog--content{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:420px;background:white;padding:40px 24px 24px}"
                + ".vui_dialog--footer{display:flex;height:32px}.vui_dialog--btn{width:170px}.vui_dialog--btn:not(:last-child){margin-right:12px}",
                "<main class='history-record'>历史记录</main><div class='vui_dialog--wrapper'><div class='vui_dialog--content'>"
                + "<div class='vui_dialog--body'>这是对官方确认弹窗的独立布局测试，不会修改账号</div><div class='vui_dialog--footer'>"
                + "<button id='cancel-dialog' class='vui_dialog--btn' onclick=\"document.querySelector('.vui_dialog--wrapper').remove();window.dialogCancelled=true\">取消</button>"
                + "<button class='vui_dialog--btn'>确认</button></div></div></div>");
        js("document.body.append(document.querySelector('.vui_dialog--wrapper'));true");
        inside(".vui_dialog--content, .vui_dialog--btn");
        assertEquals(true, js("document.querySelector('.vui_dialog--wrapper').getBoundingClientRect().width>=innerWidth-1"));
        tap("#cancel-dialog");
        await("window.dialogCancelled && !document.querySelector('.vui_dialog--wrapper')");
    }
}
