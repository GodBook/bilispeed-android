package app.bilispeed.browser;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Optional live check; the normal build script only runs the offline playback suite. */
public class OfficialBilibiliSmokeTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;

    private Object js(String script) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> activity.browserForTesting().evaluateJavascript(script, value -> {
            result.set(value); latch.countDown();
        }));
        assertTrue("No response from the website", latch.await(15, TimeUnit.SECONDS));
        return new JSONTokener(result.get()).nextValue();
    }

    private void await(String condition, int timeoutSeconds) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutSeconds * 1000L;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (Boolean.TRUE.equals(js("Boolean(" + condition + ")"))) return;
            SystemClock.sleep(250);
        }
        System.out.println("BILISPEED_LIVE_DIAGNOSTIC=" + js("JSON.stringify({url:location.href,title:document.title,"
                + "state:window.__BiliSpeed&&window.__BiliSpeed.snapshot(),hidden:document.hidden,focus:document.hasFocus(),"
                + "touch:!!window.__BiliTouch,details:!!window.__BiliTouchVideo,ready:window.__BiliTouch&&window.__BiliTouch.isPageReady(),"
                + "serverMarkup:!!document.querySelector('#app[data-server-rendered]'),panel:document.getElementById('bilispeed-episodes')&&document.getElementById('bilispeed-episodes').innerText,"
                + "season:window.__INITIAL_STATE__&&window.__INITIAL_STATE__.videoData&&{id:window.__INITIAL_STATE__.videoData.season_id,hasData:!!window.__INITIAL_STATE__.videoData.ugc_season},"
                + "videos:Array.from(document.querySelectorAll('video')).map(function(v){return {paused:v.paused,time:v.currentTime,"
                + "ready:v.readyState,duration:v.duration,error:v.error&&v.error.code};}),"
                + "tapTrace:window.liveTapTrace,"
                + "body:document.body?document.body.innerText.slice(0,350):''})"));
        screenshot("BiliSpeed-live-failure");
        fail("Website condition timed out: " + condition);
    }

    private void tapPlayer() throws Exception {
        System.out.println("BILISPEED_LIVE_PLAYERS=" + js("JSON.stringify({"
                + "players:Array.from(document.querySelectorAll('#bilibili-player')).map(function(e){var r=e.getBoundingClientRect();"
                + "return {parent:e.parentElement.className,w:r.width,h:r.height};}),"
                + "videos:Array.from(document.querySelectorAll('video')).map(function(v){var r=v.getBoundingClientRect();"
                + "return {parent:v.parentElement.className,w:r.width,h:r.height,paused:v.paused,time:v.currentTime,error:v.error&&v.error.code};})})"));
        tapElement("#bilibili-player");
    }

    private void tapElement(String selector) throws Exception {
        tapElement(selector, .5);
    }

    private void tapElement(String selector, double fraction) throws Exception {
        js("document.querySelector('" + selector + "').scrollIntoView({block:'center'}); true");
        instrumentation.runOnMainSync(() -> activity.browserForTesting().requestFocus());
        SystemClock.sleep(200);
        JSONObject point = new JSONObject((String) js("JSON.stringify((function(){"
                + "var r=document.querySelector('" + selector + "').getBoundingClientRect();"
                + "return {x:r.left+r.width*" + fraction + ",y:r.top+r.height/2,width:innerWidth};})())"));
        int[] position = new int[2];
        float[] scale = new float[1];
        instrumentation.runOnMainSync(() -> {
            android.view.View surface = activity.fullscreenForTesting() ? activity.fullscreenViewForTesting() : activity.browserForTesting();
            surface.getLocationOnScreen(position);
            scale[0] = (float) (surface.getWidth() / point.optDouble("width"));
        });
        float x = position[0] + (float) point.getDouble("x") * scale[0];
        float y = position[1] + (float) point.getDouble("y") * scale[0];
        long now = SystemClock.uptimeMillis();
        MotionEvent down = PlayerControlsInstrumentationTest.fingerInput(now, now, MotionEvent.ACTION_DOWN, x, y);
        MotionEvent up = PlayerControlsInstrumentationTest.fingerInput(now, now + 70, MotionEvent.ACTION_UP, x, y);
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        assertTrue(instrumentation.getUiAutomation().injectInputEvent(down, true));
        assertTrue(instrumentation.getUiAutomation().injectInputEvent(up, true));
        down.recycle(); up.recycle();
    }

    private void screenshot(String name) throws Exception {
        instrumentation.waitForIdleSync();
        SystemClock.sleep(350);
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull("Could not capture the tested screen", bitmap);
        File file = new File(instrumentation.getTargetContext().getExternalFilesDir(null), name + ".png");
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
        System.out.println("BILISPEED_SCREENSHOT=" + file.getAbsolutePath());
    }

    private void swipePlayer() throws Exception {
        JSONObject bounds = new JSONObject((String) js("JSON.stringify((function(){var r=document.querySelector('.bpx-player-video-area').getBoundingClientRect();"
                + "return {left:r.left,top:r.top,width:r.width,height:r.height,viewport:innerWidth};})())"));
        int[] position = new int[2];
        float[] scale = new float[1];
        instrumentation.runOnMainSync(() -> {
            activity.browserForTesting().getLocationOnScreen(position);
            scale[0] = (float) (activity.browserForTesting().getWidth() / bounds.optDouble("viewport"));
        });
        long start = SystemClock.uptimeMillis();
        for (int step = 0; step <= 7; step++) {
            float x = position[0] + (float) (bounds.getDouble("left") + bounds.getDouble("width") * (.25 + .15 * Math.min(step, 6) / 6)) * scale[0];
            float y = position[1] + (float) (bounds.getDouble("top") + bounds.getDouble("height") * .3) * scale[0];
            int action = step == 0 ? MotionEvent.ACTION_DOWN : step == 7 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            MotionEvent event = PlayerControlsInstrumentationTest.fingerInput(start, SystemClock.uptimeMillis(), action, x, y);
            event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            assertTrue(instrumentation.getUiAutomation().injectInputEvent(event, true));
            event.recycle(); SystemClock.sleep(40);
        }
    }

    private void doubleTapPlayer() throws Exception {
        js("document.querySelector('.bpx-player-video-area').scrollIntoView({block:'center'});true");
        SystemClock.sleep(200);
        JSONObject point = new JSONObject((String) js("JSON.stringify((function(){var r=document.querySelector('.bpx-player-video-area').getBoundingClientRect();"
                + "return {x:r.left+r.width*.5,y:r.top+r.height*.3,width:innerWidth};})())"));
        int[] position = new int[2];
        float[] scale = new float[1];
        instrumentation.runOnMainSync(() -> {
            activity.browserForTesting().getLocationOnScreen(position);
            scale[0] = (float) (activity.browserForTesting().getWidth() / point.optDouble("width"));
        });
        float x = position[0] + (float) point.getDouble("x") * scale[0];
        float y = position[1] + (float) point.getDouble("y") * scale[0];
        for (int tap = 0; tap < 2; tap++) {
            long start = SystemClock.uptimeMillis();
            MotionEvent down = PlayerControlsInstrumentationTest.fingerInput(start, start, MotionEvent.ACTION_DOWN, x, y);
            // Queue the contacts at finger speed; waiting for each rendered frame
            // makes a 5x live video turn this into two separate single taps.
            assertTrue(instrumentation.getUiAutomation().injectInputEvent(down, false)); down.recycle();
            SystemClock.sleep(65);
            MotionEvent up = PlayerControlsInstrumentationTest.fingerInput(start, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y);
            assertTrue(instrumentation.getUiAutomation().injectInputEvent(up, false)); up.recycle();
            SystemClock.sleep(65);
        }
    }

    private void assertTouchViewport() throws Exception {
        float[] width = new float[1];
        instrumentation.runOnMainSync(() -> width[0] = activity.browserForTesting().getWidth()
                / activity.getResources().getDisplayMetrics().density);
        System.out.println("BILISPEED_LIVE_WIDTH=" + js("JSON.stringify({url:location.href,viewport:innerWidth,width:document.documentElement.scrollWidth,"
                + "wide:Array.from(document.querySelectorAll('body *')).filter(function(e){return e.getBoundingClientRect().width>"
                + (width[0] + 2) + ";}).slice(0,12).map(function(e){var s=getComputedStyle(e);return {c:e.className,id:e.id,width:s.width,min:s.minWidth};})})"));
        assertEquals("Page must use the actual phone viewport", width[0], ((Number) js("innerWidth")).doubleValue(), 2);
        assertTrue("Page content extended beyond the phone", ((Number) js("document.documentElement.scrollWidth")).doubleValue() <= width[0] + 2);
    }

    private void pauseAutoplayForDetailsCheck() {
        // These checks exercise navigation and loaded sources. Keep software
        // GPU decoding from blocking the emulator while inspecting long lists.
        instrumentation.runOnMainSync(() -> androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
                activity.browserForTesting(), "document.addEventListener('play',function(e){if(e.target instanceof HTMLMediaElement)e.target.pause();},true);",
                java.util.Collections.singleton("https://www.bilibili.com")));
    }

    @Test public void officialVideoDetailsAndParts() throws Exception {
        Context context = instrumentation.getTargetContext();
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().putBoolean("automatic", false).commit();
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            pauseAutoplayForDetailsCheck();
            instrumentation.runOnMainSync(() -> activity.browserForTesting().loadUrl("https://www.bilibili.com/video/BV17x411w7KC/"));
            await("document.querySelectorAll('#bilispeed-episodes a').length===10", 35);
            await("document.querySelector('.up-avatar img') && document.querySelector('.up-avatar img').naturalWidth>0", 20);
            System.out.println("BILISPEED_DETAIL_LOADED=" + js("JSON.stringify({parts:document.querySelectorAll('#bilispeed-episodes a').length,"
                    + "avatar:document.querySelector('.up-avatar img').src,description:document.getElementById('v_desc').innerText.length})"));
            js("document.querySelectorAll('video').forEach(function(v){v.pause();});true");
            tapElement("#bilispeed-episodes a:nth-child(2)");
            await("location.search.includes('p=2') && window.__INITIAL_STATE__ && window.__INITIAL_STATE__.cid===275431", 35);
            await("document.querySelector('#bilispeed-episodes [aria-current]') && document.querySelector('#bilispeed-episodes [aria-current]').href.includes('p=2')", 15);
            await("document.getElementById('v_desc') && document.getElementById('v_desc').innerText.length>100", 10);
            System.out.println("BILISPEED_PART_SWITCHED=" + js("JSON.stringify({url:location.href,cid:window.__INITIAL_STATE__.cid,selected:document.querySelector('#bilispeed-episodes [aria-current]').href})"));
            screenshot("BiliSpeed-video-parts-fixed");
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }

    @Test public void officialCollectionSwitchesToAnotherVideo() throws Exception {
        Context context = instrumentation.getTargetContext();
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().putBoolean("automatic", false).commit();
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            pauseAutoplayForDetailsCheck();
            instrumentation.runOnMainSync(() -> activity.browserForTesting().loadUrl("https://www.bilibili.com/video/BV12gpt6UER4/"));
            await("document.querySelectorAll('#bilispeed-episodes a').length>100", 35);
            assertTrue((Boolean) js("document.querySelector('#bilispeed-episodes h2').textContent.includes('英雄联盟整活小剧场')"));
            assertTouchViewport();
            System.out.println("BILISPEED_LIVE_COLLECTION=" + js("JSON.stringify({count:document.querySelectorAll('#bilispeed-episodes a').length,current:document.querySelector('#bilispeed-episodes [aria-current]').href})"));
            js("document.querySelector('#bilispeed-episodes').scrollIntoView({block:'center'});true");
            screenshot("BiliSpeed-collection-fixed");
            tapElement("#bilispeed-episodes a:first-child");
            await("location.pathname.includes('BV1xXtTeZEVR') && window.__INITIAL_STATE__ && window.__INITIAL_STATE__.cid===25861685381", 35);
            await("document.querySelector('#bilispeed-episodes [aria-current]') && document.querySelector('#bilispeed-episodes [aria-current]').href.includes('BV1xXtTeZEVR')", 20);
            System.out.println("BILISPEED_COLLECTION_SWITCHED=" + js("JSON.stringify({url:location.href,cid:window.__INITIAL_STATE__.cid,title:document.querySelector('h1').textContent})"));
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }

    @Test public void officialDesktopSearchAndPopularFitPhone() throws Exception {
        Context context = instrumentation.getTargetContext();
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().putBoolean("automatic", false).commit();
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            instrumentation.runOnMainSync(() -> activity.browserForTesting().loadUrl(MainActivity.searchUrl("哔哩哔哩")));
            await("location.hostname === 'search.bilibili.com' && document.querySelector('.bili-video-card')"
                    + " && document.documentElement.hasAttribute('data-bilispeed-touch')", 35);
            await("document.querySelector('.bili-video-card__info--tit') && document.querySelector('.bili-video-card__info--tit').textContent.length > 2", 35);
            screenshot("BiliSpeed-desktop-search");
            assertTouchViewport();
            instrumentation.runOnMainSync(() -> activity.browserForTesting().loadUrl("https://www.bilibili.com/v/popular/all"));
            await("location.pathname.startsWith('/v/popular') && document.querySelector('.video-card')"
                    + " && document.documentElement.hasAttribute('data-bilispeed-touch')", 35);
            await("document.querySelector('.video-card__info') && document.querySelector('.video-card__info').textContent.length > 2", 35);
            screenshot("BiliSpeed-desktop-popular");
            System.out.println("BILISPEED_LIVE_LOGIN_POPOVER=" + js("JSON.stringify((function(){var e=Array.from(document.querySelectorAll('div,span,p')).find(function(e){return e.textContent.trim()==='登录后你可以:';});"
                    + "var a=[];for(var i=0;e&&i<5;i++,e=e.parentElement)a.push({c:e.className,id:e.id});return a;})())"));
            assertTrue("Popular cards must fit entirely on the phone", (Boolean) js("Array.from(document.querySelectorAll('.video-card')).slice(0,4).every(function(e){return e.getBoundingClientRect().right<=innerWidth+2;})"));
            assertTouchViewport();
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }

    @Test public void officialDesktopDynamicAndLoginFitPhone() throws Exception {
        Context context = instrumentation.getTargetContext();
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().putBoolean("automatic", false).commit();
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            instrumentation.runOnMainSync(() -> activity.browserForTesting().loadUrl("https://t.bilibili.com/"));
            await("location.hostname === 't.bilibili.com' && document.querySelector('.bili-dyn-item')"
                    + " && document.documentElement.hasAttribute('data-bilispeed-touch')", 35);
            screenshot("BiliSpeed-desktop-dynamic");
            assertTouchViewport();
            assertTrue("Dynamic cards must fit on the phone", (Boolean) js("document.querySelector('.bili-dyn-item').getBoundingClientRect().right <= innerWidth+2"));
            instrumentation.runOnMainSync(() -> activity.browserForTesting().loadUrl("https://account.bilibili.com/account/home"));
            await("location.hostname === 'passport.bilibili.com' && document.querySelector('.login-pwd input')"
                    + " && document.documentElement.hasAttribute('data-bilispeed-touch')", 35);
            screenshot("BiliSpeed-desktop-login");
            assertTouchViewport();
            assertTrue("Official login fields must fit and remain usable", (Boolean) js("Array.from(document.querySelectorAll('.login-pwd input')).every(function(e){"
                    + "var r=e.getBoundingClientRect();return r.width>80&&r.left>=0&&r.right<=innerWidth+2;})"));
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }

    @Test public void officialDesktopTouchHomeAndVideoAtFiveTimes() throws Exception {
        Context context = instrumentation.getTargetContext();
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().putBoolean("automatic", false).commit();
        Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) instrumentation.startActivitySync(intent);
        try {
            await("document.title.includes('哔哩') && document.body && document.body.innerText.length > 100", 35);
            await("location.hostname === 'www.bilibili.com' && document.documentElement.hasAttribute('data-bilispeed-touch')"
                    + " && document.querySelector('.bili-video-card')", 20);
            System.out.println("BILISPEED_LIVE_HOME=" + js("JSON.stringify({url:location.href,title:document.title,"
                    + "viewport:innerWidth,width:document.documentElement.scrollWidth,ua:navigator.userAgent,"
                    + "cards:Array.from(document.querySelectorAll('.feed-card')).slice(0,4).map(function(e){var r=e.getBoundingClientRect();"
                    + "return {x:r.x,y:r.y,w:r.width,h:r.height};})})"));
            screenshot("BiliSpeed-desktop-home");
            assertTouchViewport();
            instrumentation.runOnMainSync(() -> activity.browserForTesting().loadUrl("https://www.bilibili.com/video/BV17x411w7KC"));
            await("document.querySelector('#bilibili-player') && location.pathname.includes('/video/')", 35);
            SystemClock.sleep(1500);
            tapPlayer();
            await("document.querySelector('video') && document.querySelector('video').readyState >= 2", 35);
            instrumentation.runOnMainSync(() -> activity.selectRate(5));
            await("document.querySelector('video').playbackRate === 5", 5);
            // After mounting the official player, start via the explicit touch control.
            if (Boolean.TRUE.equals(js("document.querySelector('video').paused"))) tapElement("[data-bilispeed-control=play]");
            await("!document.querySelector('video').paused && document.querySelector('video').currentTime > 1", 15);
            System.out.println("BILISPEED_LIVE_VIDEO=" + js("JSON.stringify(window.__BiliSpeed.snapshot())"));
            System.out.println("BILISPEED_LIVE_LAYOUT=" + js("JSON.stringify({viewport:innerWidth,width:document.documentElement.scrollWidth,"
                    + "player:(function(){var r=document.querySelector('#playerWrap').getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height};})(),"
                    + "columns:Array.from(document.querySelectorAll('.left-container,.right-container')).map(function(e){var r=e.getBoundingClientRect();return {x:r.x,w:r.width};})})"));
            screenshot("BiliSpeed-desktop-video");
            System.out.println("BILISPEED_LIVE_CONTROLS=" + js("JSON.stringify(Array.from(document.querySelectorAll('.bpx-player-control-wrap,.bpx-player-control-entity,.bpx-player-control-bottom,.bpx-player-ctrl-play'))"
                    + ".map(function(e){var s=getComputedStyle(e),r=e.getBoundingClientRect();return {c:e.className,display:s.display,opacity:s.opacity,visibility:s.visibility,transform:s.transform,x:r.x,y:r.y,w:r.width,h:r.height};}))"));
            assertTouchViewport();
            await("document.querySelector('[data-bilispeed-control=play]') && document.querySelector('[data-bilispeed-control=play]').getBoundingClientRect().width >= 30", 10);
            tapElement("[data-bilispeed-control=play]");
            await("document.querySelector('video').paused", 5);
            tapElement("[data-bilispeed-control=volume]");
            js("window._oldVolume=document.querySelector('video').volume;window._oldMuted=document.querySelector('video').muted;"
                    + "var s=document.querySelector('[data-bilispeed-control=volume-slider]');s.value=35;s.dispatchEvent(new Event('input',{bubbles:true}));true");
            await("Math.abs(document.querySelector('video').volume-.35)<.001 && !document.querySelector('video').muted", 5);
            screenshot("BiliSpeed-live-volume");
            tapElement("[data-bilispeed-control=mute]");
            await("document.querySelector('video').muted", 5);
            tapElement("[data-bilispeed-control=mute]");
            await("!document.querySelector('video').muted", 5);
            js("document.querySelector('video').volume=window._oldVolume;document.querySelector('video').muted=window._oldMuted;true");
            tapElement("[data-bilispeed-control=close-panel]");
            tapElement("[data-bilispeed-control=subtitles]");
            await("!document.getElementById('bilispeed-player-panel').hidden", 5);
            System.out.println("BILISPEED_LIVE_SUBTITLES=" + js("JSON.stringify({panel:document.getElementById('bilispeed-player-panel').innerText,"
                    + "official:!!document.querySelector('.bpx-player-ctrl-subtitle'),languages:Array.from(document.querySelectorAll('.bpx-player-ctrl-subtitle-major-content [data-lan]')).map(function(e){return e.textContent.trim();})})"));
            screenshot("BiliSpeed-live-subtitles");
            if (Boolean.TRUE.equals(js("!!document.querySelector('[data-bilispeed-control=subtitle-language]')"))) {
                tapElement("[data-bilispeed-control=subtitle-language]");
                await("document.querySelector('[data-bilispeed-control=subtitles]').getAttribute('aria-pressed')==='true'", 5);
                tapElement("[data-bilispeed-control=subtitles]");
                tapElement("[data-bilispeed-control=subtitle-off]");
            } else {
                assertTrue((Boolean) js("/暂无可用字幕|登录后/.test(document.getElementById('bilispeed-player-panel').textContent)"));
                tapElement("[data-bilispeed-control=close-panel]");
            }
            js("window.liveTapTrace=[];['touchstart','touchend','click','dblclick','pointerup','resize','play','pause'].forEach(function(name){"
                    + "window.addEventListener(name,function(e){var t=e.target,r={type:e.type,target:t.id||t.className,"
                    + "ignored:t.closest&&!!t.closest('button,input,a,[role=button],.bpx-player-control-wrap,.bpx-player-ending-wrap,.bpx-player-dm-setting'),"
                    + "at:Date.now(),timestamp:e.timeStamp,paused:document.querySelector('video').paused,x:e.changedTouches&&e.changedTouches[0]&&e.changedTouches[0].clientX,"
                    + "y:e.changedTouches&&e.changedTouches[0]&&e.changedTouches[0].clientY};liveTapTrace.push(r);"
                    + "setTimeout(function(){r.prevented=e.defaultPrevented;},0);},true);});true");
            doubleTapPlayer();
            await("!document.querySelector('video').paused", 5);
            doubleTapPlayer();
            await("document.querySelector('video').paused", 5);
            assertFalse("Touch double-tap must not trigger official fullscreen", (Boolean) js("!!document.fullscreenElement"));
            screenshot("BiliSpeed-live-double-tap");
            tapElement(".bpx-player-dm-setting");
            System.out.println("BILISPEED_LIVE_DANMAKU=" + js("JSON.stringify((function(){var w=document.querySelector('.bpx-player-dm-setting-wrap');"
                    + "return {html:w&&w.outerHTML.slice(0,2000),bounds:w&&w.getBoundingClientRect().toJSON(),display:w&&getComputedStyle(w).display};})())"));
            await("document.querySelector('.bpx-player-dm-setting-wrap').checkVisibility()", 5);
            assertTrue("Official danmaku panel must fit the phone", (Boolean) js("(function(){var r=document.querySelector('.bpx-player-dm-setting-wrap').getBoundingClientRect();"
                    + "return r.left>=0&&r.right<=innerWidth+1&&r.top>=0&&r.bottom<=innerHeight+1;})()"));
            screenshot("BiliSpeed-live-danmaku-settings");
            js("window._danmakuOpacityBefore=document.querySelector('.bpx-player-dm-setting-left-opacity').innerText;true");
            // The official slider persists between visits. Pick a different
            // value so a second test run still proves that native input works.
            double opacityTarget = Boolean.TRUE.equals(js("parseFloat(window._danmakuOpacityBefore.match(/[0-9]+%/)[0])<50")) ? .75 : .25;
            tapElement(".bpx-player-dm-setting-left-opacity .bui-progress-wrap", opacityTarget);
            await("document.querySelector('.bpx-player-dm-setting-left-opacity').innerText!==window._danmakuOpacityBefore", 5);
            tapElement(".bpx-player-dm-setting-left-more");
            await("document.querySelector('.bpx-player-dm-setting-right').checkVisibility()", 5);
            screenshot("BiliSpeed-live-danmaku-advanced");
            tapElement("[data-bilispeed-control=danmaku-close]");
            await("!document.querySelector('.bpx-player-dm-setting-wrap').checkVisibility()", 5);
            js("window._beforeSwipe=document.querySelector('video').currentTime;true");
            swipePlayer();
            await("document.querySelector('video').currentTime>window._beforeSwipe+5", 10);
            assertTrue("Swiping a paused official video must preserve pause", (Boolean) js("document.querySelector('video').paused"));
            tapElement("[data-bilispeed-control=seek]");
            await("document.querySelector('video').currentTime > document.querySelector('video').duration * 0.4"
                    + " && document.querySelector('video').currentTime < document.querySelector('video').duration * 0.7", 15);
            tapElement("[data-bilispeed-control=play]");
            await("!document.querySelector('video').paused", 10);
            instrumentation.runOnMainSync(activity::openSpeedPanel);
            screenshot("BiliSpeed-desktop-speed-panel");
            instrumentation.runOnMainSync(() -> activity.speedDialogForTesting().dismiss());
            tapElement("[data-bilispeed-control=fullscreen]");
            long deadline = SystemClock.elapsedRealtime() + 8000;
            java.util.concurrent.atomic.AtomicBoolean fullscreen = new java.util.concurrent.atomic.AtomicBoolean();
            do {
                instrumentation.runOnMainSync(() -> fullscreen.set(activity.fullscreenForTesting()));
                if (fullscreen.get()) break;
                SystemClock.sleep(100);
            } while (SystemClock.elapsedRealtime() < deadline);
            assertTrue("The official desktop fullscreen control must enter Android fullscreen", fullscreen.get());
            PlayerControlsInstrumentationTest.dismissImmersiveHint(instrumentation);
            await("document.getElementById('bilispeed-touch-controls').dataset.hidden==='true'", 5);
            System.out.println("BILISPEED_LIVE_FULLSCREEN_LAYOUT=" + js("JSON.stringify({full:document.fullscreenElement&&document.fullscreenElement.className,"
                    + "viewport:{width:innerWidth,height:innerHeight},intrinsic:{width:document.querySelector('video').videoWidth,height:document.querySelector('video').videoHeight},"
                    + "nodes:Array.from(document.querySelectorAll('.bpx-player-container,.bpx-player-primary-area,.bpx-player-video-area,.bpx-player-video-wrap,video,#bilispeed-touch-controls')).map(function(e){"
                    + "var r=e.getBoundingClientRect(),s=getComputedStyle(e);return {c:e.className,id:e.id,screen:e.dataset.screen,x:r.x,y:r.y,w:r.width,h:r.height,padding:s.padding,position:s.position};})})"));
            screenshot("BiliSpeed-desktop-fullscreen");
            tapElement(".bpx-player-video-area");
            await("document.getElementById('bilispeed-touch-controls').dataset.hidden==='false'", 5);
            assertFalse("Revealing fullscreen controls must not pause the video", (Boolean) js("document.querySelector('video').paused"));
            screenshot("BiliSpeed-live-fullscreen-controls");
            SystemClock.sleep(3400);
            await("document.getElementById('bilispeed-touch-controls').dataset.hidden==='true'", 5);
            instrumentation.runOnMainSync(activity::onBackPressed);
            assertFalse(activity.fullscreenForTesting());
            await("document.querySelector('video').playbackRate === 5", 5);
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }
}
