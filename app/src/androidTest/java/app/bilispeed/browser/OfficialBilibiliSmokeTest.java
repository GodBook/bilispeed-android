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
        assertTrue("No response from the website", latch.await(6, TimeUnit.SECONDS));
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
                + "videos:Array.from(document.querySelectorAll('video')).map(function(v){return {paused:v.paused,time:v.currentTime,"
                + "ready:v.readyState,duration:v.duration,error:v.error&&v.error.code};}),"
                + "body:document.body?document.body.innerText.slice(0,350):''})"));
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
        js("document.querySelector('" + selector + "').scrollIntoView({block:'center'}); true");
        instrumentation.runOnMainSync(() -> activity.browserForTesting().requestFocus());
        SystemClock.sleep(200);
        JSONObject point = new JSONObject((String) js("JSON.stringify((function(){"
                + "var r=document.querySelector('" + selector + "').getBoundingClientRect();"
                + "return {x:r.left+r.width/2,y:r.top+r.height/2,width:innerWidth};})())"));
        int[] position = new int[2];
        float[] scale = new float[1];
        instrumentation.runOnMainSync(() -> {
            activity.browserForTesting().getLocationOnScreen(position);
            scale[0] = (float) (activity.browserForTesting().getWidth() / point.optDouble("width"));
        });
        float x = position[0] + (float) point.getDouble("x") * scale[0];
        float y = position[1] + (float) point.getDouble("y") * scale[0];
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 70, MotionEvent.ACTION_UP, x, y, 0);
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
            // Some site layouts mount the player on the first tap, then show its play control.
            if (Boolean.TRUE.equals(js("document.querySelector('video').paused"))) tapPlayer();
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
            screenshot("BiliSpeed-desktop-fullscreen");
            instrumentation.runOnMainSync(activity::onBackPressed);
            assertFalse(activity.fullscreenForTesting());
            await("document.querySelector('video').playbackRate === 5", 5);
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }
}
