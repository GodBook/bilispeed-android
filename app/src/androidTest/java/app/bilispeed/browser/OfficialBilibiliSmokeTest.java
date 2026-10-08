package app.bilispeed.browser;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.MotionEvent;

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
                + "videos:document.querySelectorAll('video').length,body:document.body?document.body.innerText.slice(0,350):''})"));
        fail("Website condition timed out: " + condition);
    }

    private void tapPlayer() throws Exception {
        System.out.println("BILISPEED_LIVE_PLAYERS=" + js("JSON.stringify({pageType:window.__INITIAL_STATE__&&window.__INITIAL_STATE__.pageType,"
                + "players:Array.from(document.querySelectorAll('.m-video-player')).map(function(e){var r=e.getBoundingClientRect();"
                + "return {parent:e.parentElement.className,w:r.width,h:r.height};}),"
                + "videos:Array.from(document.querySelectorAll('video')).map(function(v){var r=v.getBoundingClientRect();"
                + "return {parent:v.parentElement.className,w:r.width,h:r.height,paused:v.paused,time:v.currentTime,error:v.error&&v.error.code};})})"));
        js("window._visiblePlayer=Array.from(document.querySelectorAll('.m-video-player')).find(function(e){"
                + "var r=e.getBoundingClientRect();return r.width>10&&r.height>10;});"
                + "window._visiblePlayer&&window._visiblePlayer.scrollIntoView({block:'center'}); true");
        assertTrue("No visible mobile webpage player", (Boolean) js("!!window._visiblePlayer"));
        instrumentation.runOnMainSync(() -> activity.browserForTesting().requestFocus());
        SystemClock.sleep(200);
        JSONObject point = new JSONObject((String) js("JSON.stringify((function(){"
                + "var r=window._visiblePlayer.getBoundingClientRect();"
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

    @Test public void officialMobileHomeAndVideoAtFiveTimes() throws Exception {
        Context context = instrumentation.getTargetContext();
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear().commit();
        Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) instrumentation.startActivitySync(intent);
        try {
            await("document.title.includes('哔哩') && document.body && document.body.innerText.length > 100", 35);
            System.out.println("BILISPEED_LIVE_HOME=" + js("JSON.stringify({url:location.href,title:document.title})"));
            instrumentation.runOnMainSync(() -> activity.browserForTesting().loadUrl("https://m.bilibili.com/video/BV17x411w7KC"));
            await("document.querySelector('.m-video-player') && location.pathname.includes('/video/')", 35);
            SystemClock.sleep(1500);
            tapPlayer();
            await("document.querySelector('video') && document.querySelector('video').readyState >= 2", 35);
            instrumentation.runOnMainSync(() -> activity.selectRate(5));
            await("document.querySelector('video').playbackRate === 5", 5);
            await("!document.querySelector('video').paused && document.querySelector('video').currentTime > 1", 15);
            System.out.println("BILISPEED_LIVE_VIDEO=" + js("JSON.stringify(window.__BiliSpeed.snapshot())"));
            instrumentation.runOnMainSync(activity::openSpeedPanel);
            // Keep the actual website and speed panel visible long enough to capture a screenshot.
            SystemClock.sleep(4000);
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }
}
