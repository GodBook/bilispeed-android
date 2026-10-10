package app.bilispeed.browser;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONTokener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Safe to run on the user's phone: restores preferences and never clears Cookies. */
public class SettingsInstrumentationTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private final Map<String, Map<String, ?>> original = new HashMap<>();
    private MainActivity activity;
    private String media;

    @Before public void start() throws Exception {
        for (String name : new String[]{"playback", "updates"}) {
            original.put(name, new HashMap<>(context.getSharedPreferences(name, 0).getAll()));
        }
        context.getSharedPreferences("updates", 0).edit().putBoolean("automatic", false).commit();
        context.getSharedPreferences("playback", 0).edit().putBoolean("touchLayout", true).commit();
        try (InputStream input = instrumentation.getContext().getAssets().open("flower.mp4")) {
            media = "data:video/mp4;base64," + android.util.Base64.encodeToString(input.readAllBytes(), android.util.Base64.NO_WRAP);
        }
        launchFixture();
    }

    private void launchFixture() throws Exception {
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        String html = "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{margin:0}.bpx-player-video-area{height:220px}video{width:100%;height:100%}</style>"
                + "<div id='playerWrap'><div id='bilibili-player'><div class='bpx-player-container'>"
                + "<div class='bpx-player-video-area'><video id='v' playsinline muted loop src='" + media + "'></video>"
                + "<div class='bpx-player-dm-container'></div></div></div></div></div>"
                + "<div class='video-toolbar-container'></div><div class='left-container'><div class='video-desc'>简介</div>"
                + "<div id='comment'>评论</div><div style='height:1800px'>滚动区域</div></div><script>window.pageIdentity={};</script>";
        instrumentation.runOnMainSync(() -> {
            activity.browserForTesting().stopLoading();
            activity.browserForTesting().loadDataWithBaseURL("https://www.bilibili.com/video/BV17x411w7KC/", html, "text/html", "UTF-8", null);
        });
        await("window.__BiliSpeed && document.getElementById('v').readyState>=2 && !!document.getElementById('bilispeed-touch-controls')");
    }

    @After public void restore() {
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
        for (Map.Entry<String, Map<String, ?>> entry : original.entrySet()) {
            SharedPreferences.Editor editor = context.getSharedPreferences(entry.getKey(), 0).edit().clear();
            for (Map.Entry<String, ?> item : entry.getValue().entrySet()) {
                Object value = item.getValue(); String key = item.getKey();
                if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                else if (value instanceof Float) editor.putFloat(key, (Float) value);
                else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                else if (value instanceof Long) editor.putLong(key, (Long) value);
                else if (value instanceof String) editor.putString(key, (String) value);
                else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
            }
            assertTrue(editor.commit());
        }
    }

    private Object js(String script) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> activity.browserForTesting().evaluateJavascript(script, value -> { result.set(value); done.countDown(); }));
        assertTrue(done.await(8, TimeUnit.SECONDS));
        return new JSONTokener(result.get()).nextValue();
    }
    private void await(String condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 15000;
        do { if (Boolean.TRUE.equals(js("!!(" + condition + ")"))) return; SystemClock.sleep(100); } while (SystemClock.elapsedRealtime() < deadline);
        fail("Timed out: " + condition);
    }
    private View find(View root, String text, boolean description) {
        if ((description && root.getContentDescription() != null && text.contentEquals(root.getContentDescription()))
                || (!description && root instanceof TextView && text.contentEquals(((TextView) root).getText()))) return root;
        if (root instanceof ViewGroup) for (int index = 0; index < ((ViewGroup) root).getChildCount(); index++) {
            View result = find(((ViewGroup) root).getChildAt(index), text, description);
            if (result != null) return result;
        }
        return null;
    }
    private View labeled(String text) { return find(activity.getWindow().getDecorView(), text, true); }
    private void tap(View view) {
        assertNotNull(view);
        instrumentation.runOnMainSync(() -> view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true));
        instrumentation.waitForIdleSync();
        SystemClock.sleep(150);
        int[] point = new int[2];
        instrumentation.runOnMainSync(() -> view.getLocationOnScreen(point));
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, point[0]+view.getWidth()/2f, point[1]+view.getHeight()/2f, 0);
        MotionEvent up = MotionEvent.obtain(now, now+55, MotionEvent.ACTION_UP, point[0]+view.getWidth()/2f, point[1]+view.getHeight()/2f, 0);
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN); up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        assertTrue(instrumentation.getUiAutomation().injectInputEvent(down, true));
        assertTrue(instrumentation.getUiAutomation().injectInputEvent(up, true));
        down.recycle(); up.recycle();
        instrumentation.waitForIdleSync();
    }
    private void openSettings() { tap(labeled("B站设置")); }

    @Test public void settingsTabKeepsPagePlaybackAndScrollAndNeverShowsFloatingButtons() throws Exception {
        js("window.originalIdentity=pageIdentity;window.scrollTo(0,350);document.getElementById('v').play();true");
        await("!document.getElementById('v').paused");
        double scroll = ((Number) js("scrollY")).doubleValue();
        openSettings();
        instrumentation.runOnMainSync(() -> {
            assertTrue(activity.settingsForTesting().isShown());
            assertTrue(labeled("B站设置").isSelected());
            assertFalse(find(activity.getWindow().getDecorView(), "···", false).isShown());
        });
        assertTrue((Boolean) js("pageIdentity===originalIdentity && !document.getElementById('v').paused"));
        screenshot("BiliSpeed-1.2.6-settings");
        tap(find(activity.settingsForTesting(), "返回浏览", false));
        assertEquals(scroll, ((Number) js("scrollY")).doubleValue(), 2);
        assertTrue((Boolean) js("pageIdentity===originalIdentity && !document.getElementById('v').paused"));
        openSettings();
        instrumentation.runOnMainSync(activity::onBackPressed);
        instrumentation.runOnMainSync(() -> assertFalse(activity.settingsForTesting().isShown()));
    }

    @Test public void settingsPresetsCustomValidationAndRememberSurviveRestart() throws Exception {
        openSettings();
        for (float rate : new float[]{1, 1.25f, 1.5f, 2, 2.5f, 3, 3.5f, 4, 5}) {
            View button = labeled("选择 " + MainActivity.formatRate(rate) + " 倍速");
            assertTrue(button instanceof Button);
            tap(button);
            await("Math.abs(document.getElementById('v').playbackRate-" + rate + ")<.001");
            assertTrue(button.isSelected());
        }
        EditText input = (EditText) labeled("自定义倍速，输入 0.25 到 5");
        instrumentation.runOnMainSync(() -> input.setText("5.2"));
        tap(find(activity.settingsForTesting(), "应用", false));
        instrumentation.runOnMainSync(() -> assertNotNull(input.getError()));
        assertEquals(5, ((Number) js("document.getElementById('v').playbackRate")).doubleValue(), .001);
        instrumentation.runOnMainSync(() -> input.setText("2.73"));
        tap(find(activity.settingsForTesting(), "应用", false));
        await("document.getElementById('v').playbackRate===2.73");
        instrumentation.runOnMainSync(() -> ((Switch) labeled("记住上次倍速")).setChecked(true));
        instrumentation.runOnMainSync(activity::finish); instrumentation.waitForIdleSync();
        launchFixture();
        await("document.getElementById('v').playbackRate===2.73");
        openSettings();
        tap(labeled("记住上次倍速"));
        instrumentation.runOnMainSync(activity::finish); instrumentation.waitForIdleSync();
        launchFixture();
        await("document.getElementById('v').playbackRate===1");
    }

    @Test public void desktopLayoutRetainsSettingsAndUpdateSwitchAndAppearanceEntry() throws Exception {
        openSettings();
        tap(labeled("启动时检查更新"));
        assertTrue(context.getSharedPreferences("updates", 0).getBoolean("automatic", false));
        tap(labeled("启动时检查更新"));
        assertFalse(context.getSharedPreferences("updates", 0).getBoolean("automatic", true));
        tap(labeled("字幕按钮外观，调整大小与不透明度"));
        instrumentation.runOnMainSync(() -> { assertTrue(activity.appearanceDialogForTesting().isShowing()); activity.appearanceDialogForTesting().dismiss(); });
        tap(labeled("手机触屏布局"));
        instrumentation.runOnMainSync(() -> assertTrue(labeled("B站设置").isShown()));
        assertFalse(context.getSharedPreferences("playback", 0).getBoolean("touchLayout", true));
        tap(labeled("手机触屏布局"));
        assertTrue(context.getSharedPreferences("playback", 0).getBoolean("touchLayout", false));
    }

    @Test public void everySettingPresetAdvancesRealMediaAtTheSelectedRate() throws Exception {
        openSettings();
        for (float rate : new float[]{1, 1.25f, 1.5f, 2, 2.5f, 3, 3.5f, 4, 5}) {
            tap(labeled("选择 " + MainActivity.formatRate(rate) + " 倍速"));
            await("Math.abs(document.getElementById('v').playbackRate-" + rate + ")<.001");
            double[] ratios = new double[3];
            for (int sample = 0; sample < ratios.length; sample++) {
                js("var v=document.getElementById('v');v.pause();v.currentTime=.15;true");
                await("!v.seeking && document.getElementById('v').currentTime<.2");
                js("document.getElementById('v').play();true");
                await("!v.paused && v.currentTime>.25");
                js("window.timing=null;var before=v.currentTime,wall=performance.now();"
                        + "setTimeout(()=>{window.timing={ratio:(v.currentTime-before)/((performance.now()-wall)/1000),paused:v.paused}},450);true");
                await("!!window.timing");
                assertFalse((Boolean) js("timing.paused"));
                ratios[sample] = ((Number) js("timing.ratio")).doubleValue();
            }
            java.util.Arrays.sort(ratios);
            System.out.println("BILISPEED_SETTINGS_REAL_RATE=" + rate + " MEDIAN=" + ratios[1]);
            assertEquals("Real media timing at " + rate, rate, ratios[1], Math.max(.2, rate * .22));
        }
    }

    @Test public void overlayChurnDoesNotScanAndReplacementMediaIsStillDiscovered() throws Exception {
        js("window.scanCount=0;window.queryBefore=Element.prototype.querySelectorAll;"
                + "Element.prototype.querySelectorAll=function(s){if(s==='*' && this.closest('.bpx-player-dm-container')) scanCount++;return queryBefore.call(this,s);};"
                + "window.churn=setInterval(()=>{const e=document.querySelector('.bpx-player-dm-container');e.innerHTML='<span><b>弹幕</b></span>'.repeat(18)},130);true");
        SystemClock.sleep(1800);
        js("clearInterval(churn);true");
        assertEquals("Animated overlay changes must not rediscover media", 0, ((Number) js("scanCount")).intValue());
        js("Element.prototype.querySelectorAll=queryBefore;var v=document.getElementById('v');var n=v.cloneNode(false);n.id='next';v.replaceWith(n);true");
        await("window.__BiliSpeed.snapshot().videos===1 && document.getElementById('next').readyState>=2");
        instrumentation.runOnMainSync(() -> activity.selectRate(3.5f));
        await("document.getElementById('next').playbackRate===3.5");
    }

    private void screenshot(String name) throws Exception {
        instrumentation.waitForIdleSync();
        SystemClock.sleep(400); // Wait for the hardware frame after native view attachment.
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream output = new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }
}
