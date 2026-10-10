package app.bilispeed.browser;

import android.app.Instrumentation;
import android.content.Context;
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

public class VideoDetailsInstrumentationTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;
    private static final String VIDEO = "https://www.bilibili.com/video/BV17x411w7KC/";
    private static final String DATA = "{bvid:'BV17x411w7KC',aid:170001,pages:[{page:1,part:'第一集',duration:199},{page:2,part:'第二集',duration:205}]}";

    @Before public void launch() {
        Context target = instrumentation.getTargetContext();
        target.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear().commit();
        target.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().putBoolean("automatic", false).commit();
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(target, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
    @After public void finish() {
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }
    private Object js(String script) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> activity.browserForTesting().evaluateJavascript(script, value -> { result.set(value); latch.countDown(); }));
        assertTrue(latch.await(10, TimeUnit.SECONDS));
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
    private void fixture(String script, String query) throws Exception {
        String html = "<!doctype html><meta name='viewport' content='width=1100'><script>" + script + "</script>"
                + (script.contains("window.slowOfficialBootstrap=true") ? "<script type='application/json' src='https://s1.hdslb.com/bfs/static/jinkela/video/video.fixture.js'></script>" : "")
                + "<style>body{min-width:1100px}#mirror-vdcon{display:flex;width:1100px}.left-container{width:750px}.right-container{width:350px}"
                + ".video-pod-above-modules{width:350px;height:320px}#playerWrap{height:400px}</style>"
                + (script.contains("window.deferHydration=true") ? "<div id='app' data-server-rendered='true'>" : "<div id='app'>")
                + "<div id='mirror-vdcon'><div class='left-container'><div id='playerWrap'><div id='bilibili-player'><video></video></div></div>"
                + "<div class='video-info-container'><h1>测试视频</h1></div><div class='video-toolbar-container'><button id='like' onclick='window.liked=true'>点赞</button></div>"
                + "<div class='video-desc-container' id='v_desc'>完整视频简介</div><div id='commentapp'>评论</div></div>"
                + "<div class='right-container'><div class='video-pod-above-modules'>旧分集占位</div></div></div></div>";
        instrumentation.runOnMainSync(() -> {
            activity.browserForTesting().stopLoading();
            activity.browserForTesting().loadDataWithBaseURL(VIDEO + query, html, "text/html", "UTF-8", null);
        });
        await("window.__BiliTouchVideo");
    }

    @Test public void multiplePartsAreReadableTouchableAndUseOfficialPartUrls() throws Exception {
        fixture("window.__INITIAL_STATE__={videoData:" + DATA + "};", "?p=2&t=15");
        await("document.querySelectorAll('#bilispeed-episodes a').length===2");
        assertEquals("https://www.bilibili.com/video/BV17x411w7KC/?p=2", js("document.querySelectorAll('#bilispeed-episodes a')[1].href"));
        assertEquals("第二集", js("document.querySelector('#bilispeed-episodes a[aria-current] .episode-title').textContent"));
        assertTrue((Boolean) js("Array.from(document.querySelectorAll('#bilispeed-episodes a')).every(function(a){var r=a.getBoundingClientRect();return r.height>=48&&r.left>=0&&r.right<=innerWidth;})"));
        assertEquals("none", js("getComputedStyle(document.querySelector('.video-pod-above-modules')).display"));
        js("document.getElementById('like').click();true");
        assertTrue((Boolean) js("window.liked"));
        assertTrue((Boolean) js("!document.querySelector('[data-tab=选集]').disabled"));
    }

    @Test public void collectionUsesEachEpisodesBvAndKeepsDurationAndCurrentHighlight() throws Exception {
        fixture("window.__INITIAL_STATE__={videoData:{bvid:'BV17x411w7KC',aid:170001,pages:[{page:1}],ugc_season:{title:'世界大草台',sections:[{episodes:["
                + "{bvid:'BV17x411w7KC',title:'当前视频',arc:{duration:1103}},"
                + "{bvid:'BV1xx411c7mD',title:'下一个视频',arc:{duration:1062}},"
                + "{bvid:'javascript:alert(1)',aid:0,title:'无效数据'}]}]}}};", "");
        await("document.querySelectorAll('#bilispeed-episodes a').length===2");
        assertEquals("世界大草台", js("document.querySelector('#bilispeed-episodes h2').textContent"));
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD/", js("document.querySelectorAll('#bilispeed-episodes a')[1].href"));
        assertEquals("18:23", js("document.querySelector('#bilispeed-episodes time').textContent"));
        assertEquals(1, ((Number) js("document.querySelectorAll('#bilispeed-episodes a[aria-current]').length")).intValue());
    }

    @Test public void apiFailureShowsRetryAndLateMetadataRestoresParts() throws Exception {
        fixture("window.fetch=function(){return Promise.reject(new Error('offline'));};", "");
        await("document.querySelector('#bilispeed-episodes button')");
        assertTrue((Boolean) js("document.querySelector('#bilispeed-episodes').textContent.includes('加载失败')"));
        assertFalse((Boolean) js("document.documentElement.hasAttribute('data-bilispeed-episodes-ready')"));
        js("window.fetch=function(){return Promise.resolve({ok:true,json:function(){return Promise.resolve({code:0,data:" + DATA + "});}});};document.querySelector('#bilispeed-episodes button').click();true");
        await("document.querySelectorAll('#bilispeed-episodes a').length===2");
        assertFalse((Boolean) js("!!document.querySelector('#bilispeed-episodes button')"));
    }

    @Test public void staleVideoResponseCannotReplaceNewRouteAndUpgradePolicyExists() throws Exception {
        fixture("window.fetch=function(){return new Promise(function(resolve){window.resolveOld=resolve;});};", "");
        await("window.resolveOld");
        js("history.replaceState({},'', '/video/BV1xx411c7mD/');window.__INITIAL_STATE__={videoData:{bvid:'BV1xx411c7mD',aid:2,pages:[{page:1,part:'新的第一集'},{page:2,part:'新的第二集'}]}};"
                + "window.__BiliTouch.refresh();window.resolveOld({ok:true,json:function(){return Promise.resolve({code:0,data:" + DATA + "});}});true");
        await("document.querySelectorAll('#bilispeed-episodes a').length===2");
        assertTrue((Boolean) js("Array.from(document.querySelectorAll('#bilispeed-episodes a')).every(function(a){return a.href.includes('BV1xx411c7mD');})"));
        assertEquals("upgrade-insecure-requests", js("document.getElementById('bilispeed-https-resources').content"));
    }

    @Test public void addedVideoSectionsWaitForOfficialServerMarkupToHydrate() throws Exception {
        fixture("window.deferHydration=true;window.__INITIAL_STATE__={videoData:" + DATA + "};", "");
        assertFalse((Boolean) js("!!document.getElementById('bilispeed-episodes') || !!document.getElementById('bilispeed-video-tabs') || !!document.getElementById('bilispeed-touch-controls')"));
        js("document.getElementById('app').removeAttribute('data-server-rendered');true");
        await("document.querySelectorAll('#bilispeed-episodes a').length===2 && document.getElementById('bilispeed-video-tabs') && document.getElementById('bilispeed-touch-controls')");
    }

    @Test public void removedServerMarkerStillWaitsForTheOfficialVueMount() throws Exception {
        fixture("window.deferHydration=true;window.slowOfficialBootstrap=true;window.__INITIAL_STATE__={videoData:" + DATA + "};", "");
        js("document.getElementById('app').removeAttribute('data-server-rendered');true");
        // Reproduce a bootstrap which lasts longer than the old 3.5s fallback.
        SystemClock.sleep(3900);
        assertFalse((Boolean) js("!!document.getElementById('bilispeed-episodes') || !!document.getElementById('bilispeed-video-tabs') || !!document.getElementById('bilispeed-touch-controls')"));
        js("document.getElementById('app').__vue__={_isMounted:true};true");
        await("document.querySelectorAll('#bilispeed-episodes a').length===2 && document.getElementById('bilispeed-video-tabs') && document.getElementById('bilispeed-touch-controls')");
    }

    @Test public void failedOfficialBootstrapKeepsDelayedMetadataAndControlFallback() throws Exception {
        fixture("window.deferHydration=true;window.slowOfficialBootstrap=true;window.__INITIAL_STATE__={videoData:" + DATA + "};", "");
        js("window.dispatchEvent(new ErrorEvent('error',{filename:'https://s1.hdslb.com/bfs/static/jinkela/video/video.fixture.js',message:'Fixture bootstrap failure'}));true");
        await("document.querySelectorAll('#bilispeed-episodes a').length===2 && document.getElementById('bilispeed-video-tabs') && document.getElementById('bilispeed-touch-controls')");
    }

    @Test public void incompleteServerCollectionRequestsFullOfficialMetadata() throws Exception {
        fixture("window.__INITIAL_STATE__={videoData:{bvid:'BV17x411w7KC',aid:170001,season_id:42,pages:[{page:1}]}};"
                + "window.fetch=function(){window.requestedMetadata=true;return Promise.resolve({ok:true,json:function(){return Promise.resolve({code:0,data:"
                + "{bvid:'BV17x411w7KC',aid:170001,pages:[{page:1}],ugc_season:{title:'完整合集',sections:[{episodes:[{bvid:'BV17x411w7KC',title:'本集',arc:{duration:20}},{bvid:'BV1xx411c7mD',title:'下一集',arc:{duration:30}}]}]}}});}});};", "");
        await("document.querySelectorAll('#bilispeed-episodes a').length===2");
        assertTrue((Boolean) js("window.requestedMetadata"));
        assertEquals("完整合集", js("document.querySelector('#bilispeed-episodes h2').textContent"));
    }
}
