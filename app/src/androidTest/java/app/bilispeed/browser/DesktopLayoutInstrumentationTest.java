package app.bilispeed.browser;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONTokener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class DesktopLayoutInstrumentationTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;
    private int fixtureId;

    @Before public void launch() {
        Context target = instrumentation.getTargetContext();
        target.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("desktop", false).putFloat("rate", 3.5f).commit();
        target.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().putBoolean("automatic", false).commit();
        activity = start();
    }

    private MainActivity start() {
        return (MainActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    @After public void finish() {
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }

    private Object js(String script) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> activity.browserForTesting().evaluateJavascript(script, value -> {
            result.set(value); latch.countDown();
        }));
        assertTrue("No response from WebView", latch.await(6, TimeUnit.SECONDS));
        return new JSONTokener(result.get()).nextValue();
    }

    private void await(String condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (Boolean.TRUE.equals(js("Boolean(" + condition + ")"))) return;
            SystemClock.sleep(100);
        }
        fail("Condition timed out: " + condition);
    }

    private void fixture(boolean video) throws Exception {
        int identity = ++fixtureId;
        // Reproduce the desktop site's minimum widths and actual container hierarchy.
        String content = video
                ? "<div id='mirror-vdcon'><div class='left-container'><div class='video-info-container'><h1>测试视频</h1></div>"
                    + "<div id='playerWrap'><div id='bilibili-player'><video id='v'></video></div></div>"
                    + "<div class='video-toolbar-container'><div class='video-toolbar-left'>"
                    + "<button id='like' onclick='window.likes=(window.likes||0)+1'>点赞</button></div></div>"
                    + "<div id='v_desc' class='video-desc-container'>简介</div><div id='commentapp'>评论</div></div>"
                    + "<div class='right-container'><div class='right-container-inner'><div class='video-pod-above-modules'>选集</div></div></div></div>"
                : "<div class='bili-feed4-layout'><div class='feed2'><div class='recommended-container_floor-aside'><div class='container is-version8'>"
                    + "<div class='feed-card'>视频一</div><div class='feed-card'>视频二</div>"
                    + "<div class='feed-card'>视频三</div><div class='feed-card'>视频四</div></div></div></div></div>";
        String html = "<!doctype html><meta name='viewport' content='width=1100'>"
                + "<style>body{min-width:1100px;margin:0}#app{min-width:1080px}.bili-feed4-layout{width:1100px;padding:60px}"
                + ".container{display:grid;grid-template-columns:repeat(4,1fr);gap:20px}.feed-card{height:100px}"
                + "#mirror-vdcon{display:flex;width:1100px}.left-container{width:750px}.right-container{width:350px}"
                + "#playerWrap{height:450px}#v{width:100%;height:100%}</style>"
                + "<script>window.fixture=" + identity + ";</script><div id='app'>" + content + "</div>";
        String origin = MainActivity.HOME + (video ? "video/__bilispeed_layout__/" : "");
        instrumentation.runOnMainSync(() -> {
            activity.browserForTesting().stopLoading();
            activity.browserForTesting().loadDataWithBaseURL(origin, html, "text/html", "UTF-8", null);
        });
        await("window.fixture === " + identity + " && window.__BiliSpeed");
    }

    private TextView label(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView result = label(group.getChildAt(index), text);
                if (result != null) return result;
            }
        }
        return null;
    }

    @Test public void mobileLinksAndSearchStayOnDesktopWithQueryAndFragment() {
        assertEquals(MainActivity.HOME, MainActivity.desktopUrl("https://m.bilibili.com/"));
        assertEquals("https://www.bilibili.com/video/BV17x411w7KC?p=2&t=15#reply", MainActivity.desktopUrl(
                "https://m.bilibili.com/video/BV17x411w7KC?p=2&t=15#reply"));
        assertEquals("https://www.bilibili.com/bangumi/play/ep123?p=2", MainActivity.desktopUrl("https://m.bilibili.com/bangumi/play/ep123?p=2"));
        assertEquals("https://search.bilibili.com/all?keyword=%E5%B0%8F%E7%8C%AB", MainActivity.desktopUrl(
                "https://m.bilibili.com/search?keyword=%E5%B0%8F%E7%8C%AB"));
        assertEquals("https://t.bilibili.com/123?foo=bar", MainActivity.desktopUrl("https://m.bilibili.com/dynamic/123?foo=bar"));
        assertEquals("https://space.bilibili.com/123", MainActivity.desktopUrl("https://m.bilibili.com/space/123"));
        assertEquals("https://www.bilibili.com/video/av170001", MainActivity.desktopUrl("https://bilibili.com/video/av170001"));
        assertEquals("https://b23.tv/abc", MainActivity.desktopUrl("https://b23.tv/abc"));
        assertEquals("https://example.org/video/?p=2", MainActivity.desktopUrl("https://example.org/video/?p=2"));
        assertEquals("https://m.bilibili.com.attacker.test/", MainActivity.desktopUrl("https://m.bilibili.com.attacker.test/"));
        assertEquals("https://m.bilibili.com:8443/", MainActivity.desktopUrl("https://m.bilibili.com:8443/"));
        assertNull(MainActivity.desktopUrl(null));
        assertEquals("A&B 小猫", android.net.Uri.parse(MainActivity.searchUrl(" A&B 小猫 ")).getQueryParameter("keyword"));
    }

    @Test public void oldMobilePreferenceUsesDesktopUaAndTwoTouchableColumns() throws Exception {
        fixture(false);
        await("document.documentElement.hasAttribute('data-bilispeed-touch')");
        assertFalse((Boolean) js("/Android|Mobile|; wv/.test(navigator.userAgent)"));
        assertEquals(3.5, ((Number) js("window.__BiliSpeed.snapshot().selected")).doubleValue(), 0.001);
        float[] cssWidth = new float[1];
        instrumentation.runOnMainSync(() -> cssWidth[0] = activity.browserForTesting().getWidth()
                / activity.getResources().getDisplayMetrics().density);
        assertEquals(cssWidth[0], ((Number) js("innerWidth")).doubleValue(), 2);
        assertTrue((Boolean) js("document.documentElement.scrollWidth <= innerWidth+2"));
        assertTrue((Boolean) js("(function(){var c=document.querySelectorAll('.feed-card');"
                + "var a=c[0].getBoundingClientRect(),b=c[1].getBoundingClientRect(),d=c[2].getBoundingClientRect();"
                + "return Math.abs(a.top-b.top)<2 && b.left>a.right && d.top>a.bottom && a.width>140;})()"));
        instrumentation.runOnMainSync(() -> {
            for (String tab : new String[]{"首页", "热门", "搜索", "动态", "我的"}) {
                TextView item = label(activity.getWindow().getDecorView(), tab);
                assertNotNull(item);
                assertTrue(item.isShown() && item.isClickable() && item.getHeight() >= 48
                        * activity.getResources().getDisplayMetrics().density);
            }
        });
    }

    @Test public void videoStartsWithFullWidthPlayerAndOfficialButtonsStillWork() throws Exception {
        fixture(true);
        await("document.getElementById('bilispeed-video-tabs')");
        assertTrue((Boolean) js("(function(){var p=document.getElementById('playerWrap').getBoundingClientRect(),"
                + "t=document.querySelector('h1').getBoundingClientRect(),l=document.querySelector('.left-container').getBoundingClientRect(),"
                + "r=document.querySelector('.right-container').getBoundingClientRect();"
                + "return p.width>=innerWidth-2 && p.top<t.top && r.top>=l.bottom;})()"));
        js("document.getElementById('like').click(); true");
        assertEquals(1, ((Number) js("window.likes")).intValue());
        assertEquals("简介评论选集", js("document.getElementById('bilispeed-video-tabs').textContent"));
    }

    @Test public void originalLayoutChoiceSurvivesRestartAndKeepsDesktopUa() throws Exception {
        instrumentation.runOnMainSync(() -> { activity.setTouchLayout(false); activity.finish(); });
        instrumentation.waitForIdleSync();
        activity = start();
        fixture(false);
        assertFalse((Boolean) js("document.documentElement.hasAttribute('data-bilispeed-touch')"));
        assertFalse((Boolean) js("/Android|Mobile/.test(navigator.userAgent)"));
        assertTrue((Boolean) js("innerWidth>=1000"));
        instrumentation.runOnMainSync(() -> assertFalse(label(activity.getWindow().getDecorView(), "首页").isShown()));
        instrumentation.runOnMainSync(() -> activity.setTouchLayout(true));
        fixture(false);
        await("document.documentElement.hasAttribute('data-bilispeed-touch')");
        assertTrue((Boolean) js("innerWidth<600"));
    }
}
