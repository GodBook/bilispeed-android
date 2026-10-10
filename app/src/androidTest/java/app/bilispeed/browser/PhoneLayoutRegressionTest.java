package app.bilispeed.browser;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONTokener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Layout regression fixtures for current official markup. Preserves the user's settings and Cookies. */
public class PhoneLayoutRegressionTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;

    @Before public void launch() {
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    @After public void finish() {
        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }

    private Object js(String expression) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> activity.browserForTesting().evaluateJavascript(expression, value -> {
            result.set(value); latch.countDown();
        }));
        assertTrue("WebView response", latch.await(8, TimeUnit.SECONDS));
        return new JSONTokener(result.get()).nextValue();
    }

    private void await(String expression) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (Boolean.TRUE.equals(js("Boolean(" + expression + ")"))) return;
            SystemClock.sleep(100);
        }
        fail("Layout did not settle: " + expression);
    }

    private void fixture(String origin, String css, String body) throws Exception {
        CountDownLatch committed = new CountDownLatch(1);
        Object bridge = new Object() { @android.webkit.JavascriptInterface public void ready() { committed.countDown(); } };
        String html = "<!doctype html><meta name='viewport' content='width=1100'><style>body{margin:0;min-width:1100px}"
                + css + "</style><div id='app'>" + body + "</div><script>addEventListener('load',()=>PhoneFixtureLoaded.ready())</script>";
        instrumentation.runOnMainSync(() -> {
            activity.browserForTesting().addJavascriptInterface(bridge, "PhoneFixtureLoaded");
            activity.browserForTesting().stopLoading();
            activity.browserForTesting().loadDataWithBaseURL(origin, html, "text/html", "UTF-8", null);
        });
        // An evaluateJavascript call issued during a document replacement can
        // lose its callback. Wait for the fixture commit before querying it.
        try { assertTrue("Fixture document did not finish loading", committed.await(15, TimeUnit.SECONDS)); }
        finally { instrumentation.runOnMainSync(() -> activity.browserForTesting().removeJavascriptInterface("PhoneFixtureLoaded")); }
        await("window.__BiliTouch && document.documentElement.hasAttribute('data-bilispeed-touch')");
    }

    @Test public void searchPercentageColumnsDoNotShrinkInsideTwoColumnGrid() throws Exception {
        String card = "<div class='col_3 col_xs_1_5 mb_x40'><div class='bili-video-card'>长标题电路基础课程</div></div>";
        fixture("https://search.bilibili.com/all?keyword=test", ".col_3{max-width:25%;flex:0 0 25%;padding:0 8px}",
                "<div class='search-layout'><div class='video-list row'>" + card + card + card + card + "</div></div>");
        assertTrue((Boolean) js("Array.from(document.querySelectorAll('.video-list > *')).every(e=>e.getBoundingClientRect().width>innerWidth*.35)"));
        assertTrue((Boolean) js("(()=>{let a=document.querySelectorAll('.video-list > *');return Math.abs(a[0].getBoundingClientRect().top-a[1].getBoundingClientRect().top)<2"
                + "&&a[2].getBoundingClientRect().top>a[0].getBoundingClientRect().top})()"));
    }

    @Test public void popularScopedCardsAndAllTabsFitPhone() throws Exception {
        String card = "<div class='video-card' data-official><div class='video-card__content'></div><div class='video-card__info'>热门长标题</div></div>";
        String tab = "<div class='nav-tabs__item'><div><span>综合热门</span></div></div>";
        fixture("https://www.bilibili.com/v/popular/all", ".popular-list .card-list .video-card[data-official]{width:calc(50% - 5px)}"
                + ".video-card{height:116px}.video-card__content{width:206px;height:116px;margin-right:10px}",
                "<div class='popular-container'><div class='nav-tabs'>" + tab + tab + tab + tab + tab + "</div>"
                        + "<div class='popular-list'><div class='card-list'>" + card + card + "</div></div></div>");
        assertTrue((Boolean) js("Array.from(document.querySelectorAll('.video-card')).every(e=>e.getBoundingClientRect().width>innerWidth*.8)"));
        assertTrue((Boolean) js("Array.from(document.querySelectorAll('.nav-tabs__item')).every(e=>{let r=e.getBoundingClientRect();return r.width>50&&r.right<=innerWidth+1})"));
        assertTrue((Boolean) js("(()=>{let r=document.querySelector('.video-card__content').getBoundingClientRect();return Math.abs(r.width/r.height-16/9)<.05})()"));
    }

    @Test public void scrollingCommentsCannotTurnMiniPlayerIntoViewportOverlay() throws Exception {
        fixture("https://www.bilibili.com/video/__layout_test__/", ".bpx-player-container[data-screen=mini]{position:fixed;right:84px;bottom:48px}"
                + ".bpx-player-container[data-screen=full]{position:fixed;inset:0}#comments{height:2000px}",
                "<div id='mirror-vdcon'><div class='left-container'><div id='playerWrap'><div id='bilibili-player'>"
                        + "<div class='bpx-player-container' data-screen='mini' style='right:84px;bottom:48px'><video></video></div>"
                        + "</div></div><div id='comments'>评论区域</div></div></div>");
        js("document.documentElement.style.scrollBehavior='auto';window.scrollTo(0,600);true");
        assertTrue((Boolean) js("(()=>{let p=document.querySelector('.bpx-player-container');return getComputedStyle(p).position==='relative'&&p.getBoundingClientRect().bottom<0})()"));
        js("document.querySelector('.bpx-player-container').dataset.screen='full';true");
        assertEquals("fixed", js("getComputedStyle(document.querySelector('.bpx-player-container')).position"));
    }

    @Test public void inlineLoginStacksAgreementAndKeepsTitleAndCloseInsidePhone() throws Exception {
        fixture("https://www.bilibili.com/", ".bili-mini-mask{position:fixed;inset:0;display:flex;align-items:center;justify-content:center}"
                + ".bili-mini-content-wp{display:flex;position:relative;width:820px;min-height:460px}"
                + ".bili-mini-login-right-wp{width:400px;height:250px}.login-agreement-wp{position:absolute;bottom:30px;left:50%;transform:translateX(-50%);width:820px}"
                + ".bili-mini-customer-title{position:absolute;width:820px;top:24px;text-align:center}"
                + ".bili-mini-close-icon{position:absolute;right:20px;top:20px;width:32px;height:32px}",
                "<div class='bili-mini-mask'><div class='bili-mini-content-wp'><div class='bili-mini-close-icon'>×</div>"
                        + "<div class='bili-mini-customer-title'>登录参与社区互动</div><div class='bili-mini-login-right-wp'>登录表单</div>"
                        + "<div class='login-agreement-wp'>登录即代表你同意用户协议和隐私政策</div></div></div>");
        assertTrue((Boolean) js("(()=>{let m=document.querySelector('.bili-mini-content-wp'),a=document.querySelector('.login-agreement-wp').getBoundingClientRect(),"
                + "f=document.querySelector('.bili-mini-login-right-wp').getBoundingClientRect(),t=document.querySelector('.bili-mini-customer-title').getBoundingClientRect(),"
                + "c=document.querySelector('.bili-mini-close-icon').getBoundingClientRect();return a.y>=f.bottom&&a.width>innerWidth*.7"
                + "&&a.left>=m.getBoundingClientRect().left&&a.right<=m.getBoundingClientRect().right"
                + "&&m.scrollWidth<=m.clientWidth+1&&t.right<=c.left&&c.right<=innerWidth})()"));
    }

    @Test public void lateShadowCommentActionsWrapByRowAndResumeAfterBackground() throws Exception {
        fixture("https://www.bilibili.com/video/__layout_test__/", "", "<bili-comments></bili-comments>");
        js("var root=document.querySelector('bili-comments').attachShadow({mode:'open'});"
                + "var actions=document.createElement('bili-comment-action-buttons-renderer');root.append(actions);"
                + "actions.attachShadow({mode:'open'}).innerHTML='<style>:host{display:flex;width:210px}:host>:not(:first-child){margin-left:20px}</style>"
                + "<div id=pubdate>2026-10-10 10:00</div><div id=like><button>999999</button></div><div id=dislike><button>踩</button></div><div id=reply><button>回复</button></div>';"
                + "window.__BiliTouch.refresh();true");
        await("actions.shadowRoot.getElementById('bilispeed-comment-style')");
        assertTrue((Boolean) js("(()=>{let r=actions.shadowRoot;return getComputedStyle(r.querySelector('button')).whiteSpace==='nowrap'"
                + "&&r.getElementById('reply').getBoundingClientRect().top>r.getElementById('pubdate').getBoundingClientRect().top"
                + "&&actions.scrollWidth<=actions.clientWidth+1})()"));
        js("window.__BiliTouch.setSuspended(true);var late=document.createElement('bili-comment-replies-renderer');"
                + "late.attachShadow({mode:'open'}).innerHTML='<div id=expander>迟到回复</div>';root.append(late);true");
        assertEquals(false, js("!!late.shadowRoot.getElementById('bilispeed-comment-style')"));
        js("window.__BiliTouch.setSuspended(false);true");
        await("late.shadowRoot.getElementById('bilispeed-comment-style')");
    }
}
