package app.bilispeed.browser;

import android.app.Instrumentation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaMuxer;
import android.os.SystemClock;
import android.util.Base64;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Real WebView media and Android input checks for the touch player's controls. */
public class PlayerControlsInstrumentationTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;
    private String fixtureHtml;
    private static String media;
    private long touchStart;
    private boolean inputActive;
    private float[] lastPoint;
    private static final String HOST = ".bpx-player-video-area";
    private static final String CONTROLS = "document.getElementById('bilispeed-touch-controls')";

    @Before public void launch() throws Exception {
        Context context = instrumentation.getTargetContext();
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().putBoolean("automatic", false).commit();
        if (media == null) {
            try (InputStream input = instrumentation.getContext().getAssets().open("flower.mp4")) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
                media = "data:video/mp4;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
            }
        }
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        instrumentation.runOnMainSync(() -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        String width = InstrumentationRegistry.getArguments().getString("layoutWidth");
        if (width != null) instrumentation.runOnMainSync(() -> {
            android.view.ViewGroup.LayoutParams params = activity.browserForTesting().getLayoutParams();
            params.width = Math.round(Integer.parseInt(width) * activity.getResources().getDisplayMetrics().density);
            activity.browserForTesting().setLayoutParams(params);
        });
        String html = "<!doctype html><meta name='viewport' content='width=1100'>"
                + "<style>body{margin:0;min-width:1100px}#mirror-vdcon{width:1100px;display:flex}"
                + ".left-container{width:750px}.right-container{width:350px}#playerWrap{height:450px}"
                + ".bpx-player-container{height:100%;position:relative}.bpx-player-video-area{height:100%;overflow:hidden}"
                + "#v{width:100%;height:100%}.bpx-player-control-wrap{position:absolute;bottom:0}"
                + "</style><script>window.playerFixture=true;"
                + "window.touchEvent=function(type,points){var target=document.querySelector('" + HOST + "');"
                + "var touches=points.map(function(p){return new Touch({identifier:p[0],target:target,clientX:p[1],clientY:p[2]});});"
                + "target.dispatchEvent(new TouchEvent(type,{bubbles:true,cancelable:true,touches:touches,targetTouches:touches,changedTouches:touches}));};"
                + "</script><div id='app'><div id='mirror-vdcon'><div class='left-container'>"
                + "<div class='video-info-container'><h1>触屏播放器测试</h1></div><div id='playerWrap'>"
                + "<div id='bilibili-player'><div class='bpx-player-container'><div class='bpx-player-video-area'>"
                + "<video id='v' playsinline muted preload='auto' src='" + media + "'></video>"
                + "<div class='bpx-player-control-wrap'>官方控制栏</div></div></div></div></div>"
                + "<div class='video-toolbar-container'>官方点赞、收藏</div><p>测试视频简介</p></div>"
                + "<div class='right-container'><p style='height:900px'>推荐与评论</p></div></div></div>";
        fixtureHtml = html;
        instrumentation.runOnMainSync(() -> {
            activity.browserForTesting().stopLoading();
            activity.browserForTesting().loadDataWithBaseURL("https://www.bilibili.com/video/__bilispeed_controls__/", html, "text/html", "UTF-8", null);
        });
        await("window.playerFixture && window.__BiliTouchPlayer && document.getElementById('v').readyState>=2 && " + CONTROLS);
        await("innerHeight>innerWidth");
        if (width != null) assertEquals(Integer.parseInt(width), ((Number) js("innerWidth")).doubleValue(), 1);
        js("window.v=document.getElementById('v');v.pause();v.currentTime=1;true");
        await("!v.seeking && v.currentTime>0.9");
    }

    @After public void finish() {
        if (inputActive && lastPoint != null) {
            MotionEvent cancel = touchInput(MotionEvent.ACTION_CANCEL, lastPoint);
            instrumentation.getUiAutomation().injectInputEvent(cancel, true); cancel.recycle();
        }
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }

    private Object js(String script) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> activity.browserForTesting().evaluateJavascript(script, value -> {
            result.set(value); latch.countDown();
        }));
        assertTrue("WebView did not respond", latch.await(6, TimeUnit.SECONDS));
        return new JSONTokener(result.get()).nextValue();
    }

    private void await(String condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 12000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (Boolean.TRUE.equals(js("Boolean(" + condition + ")"))) return;
            SystemClock.sleep(100);
        }
        System.out.println("BILISPEED_CONTROL_DIAGNOSTIC=" + js("JSON.stringify({time:window.v&&v.currentTime,"
                + "nativeFullscreen:window.__BILI_TOUCH_FULLSCREEN__,domFullscreen:!!document.fullscreenElement,layoutFullscreen:document.documentElement.hasAttribute('data-bilispeed-fullscreen'),"
                + "hidden:document.getElementById('bilispeed-touch-controls')&&document.getElementById('bilispeed-touch-controls').dataset.hidden,"
                + "events:window.fullTouchTrace,viewport:{width:innerWidth,height:innerHeight}})"));
        screenshot("BiliSpeed-player-failure");
        fail("Condition timed out: " + condition);
    }

    private void click(String name) throws Exception {
        js("document.querySelector('[data-bilispeed-control=\"" + name + "\"]').click();true");
    }

    private float[] point(String selector, double x, double y) throws Exception {
        JSONObject bounds = new JSONObject((String) js("JSON.stringify((function(){var r=document.querySelector('" + selector
                + "').getBoundingClientRect();return {x:r.left+r.width*" + x + ",y:r.top+r.height*" + y + ",width:innerWidth};})())"));
        int[] position = new int[2];
        float[] scale = new float[1];
        instrumentation.runOnMainSync(() -> {
            View surface = activity.fullscreenForTesting() ? activity.fullscreenViewForTesting() : activity.browserForTesting();
            surface.getLocationOnScreen(position);
            scale[0] = (float) (surface.getWidth() / bounds.optDouble("width"));
            if (activity.fullscreenForTesting()) System.out.println("BILISPEED_FULL_INPUT=" + bounds + " native="
                    + surface.getWidth() + "x" + surface.getHeight() + " at " + position[0] + "," + position[1]);
        });
        return new float[]{position[0] + (float) bounds.getDouble("x") * scale[0], position[1] + (float) bounds.getDouble("y") * scale[0]};
    }

    private void input(int action, float[] point) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) touchStart = now;
        lastPoint = point;
        inputActive = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL;
        MotionEvent event = touchInput(action, point);
        assertTrue(instrumentation.getUiAutomation().injectInputEvent(event, true));
        event.recycle();
        SystemClock.sleep(40);
    }

    private MotionEvent touchInput(int action, float[] point) {
        return fingerInput(touchStart, SystemClock.uptimeMillis(), action, point[0], point[1]);
    }

    static MotionEvent fingerInput(long downTime, long eventTime, int action, float x, float y) {
        MotionEvent.PointerProperties finger = new MotionEvent.PointerProperties();
        finger.id = 0; finger.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
        coordinates.x = x; coordinates.y = y; coordinates.size = 1;
        coordinates.pressure = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ? 0 : 1;
        return MotionEvent.obtain(downTime, eventTime, action, 1,
                new MotionEvent.PointerProperties[]{finger}, new MotionEvent.PointerCoords[]{coordinates},
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    }

    private void tap(String selector) throws Exception {
        if (selector.equals("[data-bilispeed-control=volume]") || selector.equals("[data-bilispeed-control=subtitles]")) {
            if (Boolean.TRUE.equals(js("document.querySelector('" + selector + "').hidden"))) {
                tap("[data-bilispeed-control=more]");
                selector = selector.replace("=", "=more-");
            }
        }
        instrumentation.runOnMainSync(() -> {
            if (!activity.fullscreenForTesting()) activity.browserForTesting().requestFocus();
        });
        float[] location = point(selector, .5, .3);
        input(MotionEvent.ACTION_DOWN, location); input(MotionEvent.ACTION_UP, location);
    }

    private void swipe(double fromX, double fromY, double toX, double toY) throws Exception {
        float[] from = point(HOST, fromX, fromY), to = point(HOST, toX, toY);
        input(MotionEvent.ACTION_DOWN, from);
        for (int step = 1; step <= 6; step++) input(MotionEvent.ACTION_MOVE,
                new float[]{from[0] + (to[0] - from[0]) * step / 6, from[1] + (to[1] - from[1]) * step / 6});
        input(MotionEvent.ACTION_UP, to);
    }

    private void screenshot(String name) throws Exception {
        instrumentation.waitForIdleSync();
        SystemClock.sleep(350);
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File file = new File(instrumentation.getTargetContext().getExternalFilesDir(null), name + ".png");
        try (FileOutputStream output = new FileOutputStream(file)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); }
        finally { bitmap.recycle(); }
        System.out.println("BILISPEED_SCREENSHOT=" + file.getAbsolutePath());
    }

    @Test public void realSingleFingerSwipePreviewsThenSeeksWithoutPlaying() throws Exception {
        js("window.touchTrace=[];['touchstart','touchmove','touchend','touchcancel','pointerdown','pointermove','pointercancel','resize'].forEach(function(name){"
                + "window.addEventListener(name,function(e){touchTrace.push({type:e.type,target:e.target.id||e.target.className,cancelable:e.cancelable,"
                + "touches:e.touches&&Array.from(e.touches).map(function(t){return {id:t.identifier,x:t.clientX,y:t.clientY};})});},true);});true");
        float[] from = point(HOST, .25, .3), to = point(HOST, .65, .3);
        input(MotionEvent.ACTION_DOWN, from);
        input(MotionEvent.ACTION_MOVE, new float[]{(from[0] + to[0]) / 2, from[1]});
        input(MotionEvent.ACTION_MOVE, to);
        System.out.println("BILISPEED_GESTURE_TRACE=" + js("JSON.stringify({events:touchTrace,feedback:document.getElementById('bilispeed-seek-feedback').outerHTML,"
                + "host:document.querySelector('.bpx-player-video-area').getBoundingClientRect().toJSON(),action:getComputedStyle(document.querySelector('.bpx-player-video-area')).touchAction})"));
        assertEquals(1, ((Number) js("v.currentTime")).doubleValue(), .15);
        assertFalse((Boolean) js("document.getElementById('bilispeed-seek-feedback').hidden"));
        input(MotionEvent.ACTION_UP, to);
        await("!v.seeking && v.currentTime > 1.5");
        assertEquals(1 + .4 * ((Number) js("v.duration")).doubleValue(), ((Number) js("v.currentTime")).doubleValue(), .2);
        assertTrue((Boolean) js("v.paused"));
        // Stay outside Android's system back-gesture zones at the display edges.
        swipe(.85, .3, .15, .3);
        await("!v.seeking && v.currentTime<0.1");
        swipe(.15, .3, .85, .3);
        swipe(.15, .3, .85, .3);
        await("!v.seeking && Math.abs(v.duration-v.currentTime)<0.1");
    }

    @Test public void verticalMultitouchAndCancelledGesturesLeaveTimeAlone() throws Exception {
        swipe(.5, .2, .5, .6);
        assertEquals(1, ((Number) js("v.currentTime")).doubleValue(), .15);
        js("touchEvent('touchstart',[[1,80,50]]);touchEvent('touchmove',[[1,220,50]]);touchEvent('touchcancel',[]);true");
        assertEquals(1, ((Number) js("v.currentTime")).doubleValue(), .15);
        js("touchEvent('touchstart',[[1,80,50]]);touchEvent('touchmove',[[1,220,50]]);"
                + "touchEvent('touchstart',[[1,220,50],[2,250,60]]);touchEvent('touchend',[]);true");
        assertEquals(1, ((Number) js("v.currentTime")).doubleValue(), .15);
        assertTrue((Boolean) js("document.getElementById('bilispeed-seek-feedback').hidden"));
    }

    private void doubleTap() throws Exception {
        float[] location = point(HOST, .5, .3);
        // Describe two 50ms contacts 100ms apart, independently of how long the
        // test thread is descheduled while the emulator decodes video.
        long sequenceStart = SystemClock.uptimeMillis();
        for (int tap = 0; tap < 2; tap++) {
            long start = sequenceStart + tap * 100;
            MotionEvent down = fingerInput(start, start, MotionEvent.ACTION_DOWN, location[0], location[1]);
            assertTrue(instrumentation.getUiAutomation().injectInputEvent(down, false)); down.recycle();
            SystemClock.sleep(50);
            MotionEvent up = fingerInput(start, start + 50, MotionEvent.ACTION_UP, location[0], location[1]);
            assertTrue(instrumentation.getUiAutomation().injectInputEvent(up, false)); up.recycle();
            SystemClock.sleep(50);
        }
    }

    @Test public void realDoubleTapTogglesOnceAndSingleTapDoesNotPause() throws Exception {
        js("v.loop=true;var overlay=document.createElement('div');overlay.className='bpx-player-hinter-area';"
                + "overlay.style.cssText='position:absolute;inset:0;z-index:1';document.querySelector('" + HOST + "').append(overlay);"
                + "window.officialClicks=0;['click','dblclick'].forEach(function(name){"
                + "document.querySelector('" + HOST + "').addEventListener(name,function(){officialClicks++;v.paused?v.play():v.pause();});});true");
        doubleTap();
        await("!v.paused && document.getElementById('bilispeed-seek-feedback').textContent==='继续播放'");
        SystemClock.sleep(420);
        tap(HOST);
        SystemClock.sleep(420);
        assertFalse((Boolean) js("v.paused"));
        doubleTap();
        await("v.paused && document.getElementById('bilispeed-seek-feedback').textContent==='已暂停'");
        assertEquals("Do not let official click handlers toggle a second time", 0, ((Number) js("officialClicks")).intValue());
        assertFalse((Boolean) js("!!document.fullscreenElement"));
        screenshot("BiliSpeed-double-tap-paused");
    }

    @Test public void doubleTapWorksWithHiddenFullscreenControls() throws Exception {
        tap("[data-bilispeed-control=fullscreen]");
        await("document.fullscreenElement && innerWidth>innerHeight && " + CONTROLS + ".dataset.hidden==='true'");
        dismissImmersiveHint(instrumentation);
        js("v.loop=true;true");
        doubleTap();
        await("!v.paused && " + CONTROLS + ".dataset.hidden==='false'");
        SystemClock.sleep(3400);
        await(CONTROLS + ".dataset.hidden==='true'");
        doubleTap();
        await("v.paused && " + CONTROLS + ".dataset.hidden==='false'");
        assertTrue((Boolean) js("!!document.fullscreenElement"));
        screenshot("BiliSpeed-double-tap-fullscreen");
    }

    @Test public void largeSubtitleButtonKeepsFullscreenSettingsAndCaptionsInsidePlayer() throws Exception {
        instrumentation.runOnMainSync(() -> instrumentation.getTargetContext().getSharedPreferences("playback", Context.MODE_PRIVATE)
                .edit().putInt("subtitle_size", 150).putInt("subtitle_opacity", 30).commit());
        js("var captions=document.createElement('div');captions.className='bpx-player-subtitle-wrap';"
                + "captions.textContent='测试字幕';captions.style.cssText='position:absolute;bottom:12px;left:25%;width:50%;height:24px;color:white';"
                + "document.querySelector('" + HOST + "').append(captions);"
                + "window.__BiliTouchPlayer.setAppearance({size:150,opacity:30});true");
        tap("[data-bilispeed-control=fullscreen]");
        await("document.fullscreenElement && innerWidth>innerHeight && " + CONTROLS + ".dataset.hidden==='true'");
        dismissImmersiveHint(instrumentation);
        // Rotation and immersive system-bar insets can dispatch a final resize
        // after the CSS viewport changes. Resize intentionally cancels a pending
        // tap, so let that transition settle before testing the surface action.
        SystemClock.sleep(400);
        tap(HOST); await(CONTROLS + ".dataset.hidden==='false'");
        click("volume");
        assertEquals("0.3", js("getComputedStyle(document.querySelector('[data-bilispeed-control=subtitles]')).opacity"));
        assertTrue((Boolean) js("(function(){var p=document.getElementById('bilispeed-player-panel').getBoundingClientRect(),"
                + "c=document.querySelector('.bpx-player-subtitle-wrap').getBoundingClientRect(),bar=" + CONTROLS + ".getBoundingClientRect();"
                + "return p.top>=0&&p.bottom<=innerHeight&&c.top>=0&&c.bottom<=bar.top&&p.right<=innerWidth;})()"));
        screenshot("BiliSpeed-large-subtitle-fullscreen");
    }

    @Test public void distantMultifingerCancelledAndSwipedContactsDoNotDoubleTap() throws Exception {
        js("touchEvent('touchstart',[[1,80,50]]);touchEvent('touchend',[]);"
                + "touchEvent('touchstart',[[1,240,50]]);touchEvent('touchend',[]);true");
        SystemClock.sleep(400);
        assertTrue((Boolean) js("v.paused"));
        js("touchEvent('touchstart',[[1,80,50]]);touchEvent('touchend',[]);"
                + "touchEvent('touchstart',[[1,80,50],[2,100,50]]);touchEvent('touchend',[]);"
                + "touchEvent('touchstart',[[1,80,50]]);touchEvent('touchend',[]);true");
        SystemClock.sleep(400);
        assertTrue((Boolean) js("v.paused"));
        js("touchEvent('touchstart',[[1,80,50]]);touchEvent('touchend',[]);"
                + "touchEvent('touchstart',[[1,80,50]]);touchEvent('touchcancel',[]);"
                + "touchEvent('touchstart',[[1,80,50]]);touchEvent('touchend',[]);true");
        SystemClock.sleep(400);
        assertTrue((Boolean) js("v.paused"));
        js("touchEvent('touchstart',[[1,80,50]]);touchEvent('touchend',[]);touchEvent('touchstart',[[1,80,50]]);true");
        SystemClock.sleep(320);
        js("touchEvent('touchend',[]);touchEvent('touchstart',[[1,80,50]]);touchEvent('touchend',[]);true");
        SystemClock.sleep(400);
        assertTrue((Boolean) js("v.paused"));
        swipe(.3, .3, .6, .3);
        tap(HOST);
        SystemClock.sleep(400);
        assertTrue((Boolean) js("v.paused"));
        click("subtitles");
        assertFalse((Boolean) js("document.getElementById('bilispeed-player-panel').hidden"));
    }

    @Test public void danmakuPopupFitsAndKeepsItsOfficialInputsUsable() throws Exception {
        js("var sending=document.createElement('div');sending.className='bpx-player-sending-bar';"
                + "sending.innerHTML=\"<div class='bpx-player-dm-setting'><div class='bpx-player-dm-setting-wrap'>"
                + "<div class='bpx-player-dm-setting-box'><div class='bui-panel-wrap' style='width:320px;height:120px;overflow:hidden'>"
                + "<div class='bui-panel-move' style='width:586px;transform:translateX(0px)'>"
                + "<div class='bui-panel-item bui-panel-item-active' style='width:320px;height:120px'><div class='bpx-player-dm-setting-left'>"
                + "<div class='bpx-player-dm-setting-left-radio'><label><input type='checkbox' id='dm-safe'>智能防挡弹幕</label></div>"
                + "<div class='bpx-player-dm-setting-left-opacity'><span class='bpx-player-dm-setting-left-opacity-title'>不透明度</span>"
                + "<div class='bpx-player-dm-setting-left-opacity-content'><input id='dm-opacity' type='range' value='53'></div></div>"
                + "<button id='dm-more'>高级设置</button><p id='dm-tail' style='margin:30px 0'>弹幕速度</p>"
                + "</div></div></div></div></div></div></div>\";"
                + "document.getElementById('bilibili-player').append(sending);"
                + "var style=document.createElement('style');style.textContent='.bpx-player-sending-bar{display:flex;align-items:center;height:46px}'"
                + "+'.bpx-player-dm-setting{position:relative;width:32px;height:32px}'"
                + "+'.bpx-player-dm-setting-wrap{width:320px;height:359px;position:absolute;bottom:46px;right:-149px}'"
                + "+'.bpx-player-dm-setting-box{position:absolute;bottom:0;right:0;width:320px;height:359px;background:#242528;color:#fff}'"
                + "+'.bpx-player-dm-setting-left{width:100%;height:100%;padding:12px 20px}'"
                + "+'.bpx-player-dm-setting-left-opacity{display:flex;width:100%}'"
                + "+'.bpx-player-dm-setting-left-opacity-title{width:61px}'"
                + "+'.bpx-player-dm-setting-left-opacity-content{width:200px;margin-left:10px;flex:1}'"
                + "+'#dm-opacity{width:200px;height:44px}';document.head.append(style);true");
        await("document.querySelector('.bpx-player-dm-setting-wrap').getBoundingClientRect().width>200");
        await("document.querySelector('[data-bilispeed-control=danmaku-close]')");
        js("document.querySelector('.bpx-player-dm-setting').click();true");
        System.out.println("BILISPEED_DANMAKU_BOUNDS=" + js("JSON.stringify(['.bpx-player-dm-setting-wrap','.bpx-player-dm-setting-box','#dm-opacity','#dm-safe'].map(function(s){"
                + "var e=document.querySelector(s);return {s:s,bounds:e.getBoundingClientRect().toJSON(),style:getComputedStyle(e).cssText,width:innerWidth,height:innerHeight};}))"));
        screenshot("BiliSpeed-danmaku-settings");
        assertTrue((Boolean) js("['.bpx-player-dm-setting-wrap','.bpx-player-dm-setting-box','#dm-opacity','#dm-safe','#dm-tail'].every(function(s){"
                + "var r=document.querySelector(s).getBoundingClientRect();return r.left>=0&&r.right<=innerWidth&&r.top>=0&&r.bottom<=innerHeight;})"));
        tap("#dm-safe");
        assertTrue((Boolean) js("document.getElementById('dm-safe').checked"));
        assertTrue((Boolean) js("v.paused"));
        js("window.dmCloseTrace=[];['touchstart','touchend','click'].forEach(function(name){document.addEventListener(name,function(e){"
                + "dmCloseTrace.push({type:e.type,target:e.target.id||e.target.className,control:e.target.dataset&&e.target.dataset.bilispeedControl});},true);});"
                + "var done=document.querySelector('[data-bilispeed-control=danmaku-close]'),r=done.getBoundingClientRect();"
                + "window.dmCloseHit=document.elementFromPoint(r.left+r.width*.5,r.top+r.height*.3).outerHTML.slice(0,400);true");
        tap("[data-bilispeed-control=danmaku-close]");
        SystemClock.sleep(350);
        boolean nativeClosed = (Boolean) js("!document.querySelector('.bpx-player-dm-setting-wrap').checkVisibility()");
        System.out.println("BILISPEED_DANMAKU_CLOSE=" + js("JSON.stringify({trace:dmCloseTrace,hit:dmCloseHit,"
                + "flags:document.querySelector('.bpx-player-dm-setting-wrap').outerHTML.slice(0,600)})"));
        if (!nativeClosed) {
            js("document.querySelector('[data-bilispeed-control=danmaku-close]').click();true");
            System.out.println("BILISPEED_DANMAKU_PROGRAMMATIC_CLOSE=" + js("!document.querySelector('.bpx-player-dm-setting-wrap').checkVisibility()"));
        }
        assertTrue("Native touch must reach the danmaku close button", nativeClosed);
        js("document.querySelector('.bpx-player-dm-setting').click();true");
        assertTrue((Boolean) js("document.querySelector('.bpx-player-dm-setting-wrap').checkVisibility()"));
    }

    @Test public void volumeSliderUnmutesAndMuteRestoresAudibleVolume() throws Exception {
        click("volume");
        js("var range=document.querySelector('[data-bilispeed-control=volume-slider]');range.value=37;range.dispatchEvent(new Event('input',{bubbles:true}));true");
        assertEquals(.37, ((Number) js("v.volume")).doubleValue(), .001);
        assertFalse((Boolean) js("v.muted"));
        click("mute"); assertTrue((Boolean) js("v.muted"));
        click("mute"); assertFalse((Boolean) js("v.muted"));
        assertEquals(.37, ((Number) js("v.volume")).doubleValue(), .001);
        js("range.value=0;range.dispatchEvent(new Event('input',{bubbles:true}));true");
        assertTrue((Boolean) js("v.muted && v.volume===0"));
        click("mute");
        assertTrue((Boolean) js("!v.muted && Math.abs(v.volume-.37)<.001"));
        screenshot("BiliSpeed-player-volume");
    }

    @Test public void toolbarSpeedUsesNativePreferenceValidatesCustomInputAndSurvivesRestart() throws Exception {
        tap("[data-bilispeed-control=speed]");
        await("!document.getElementById('bilispeed-player-panel').hidden");
        tap("[data-rate=\"1.5\"]");
        await("v.playbackRate===1.5&&document.querySelector('[data-bilispeed-control=speed]').textContent==='1.5x'");
        assertEquals(1.5f, instrumentation.getTargetContext().getSharedPreferences("playback", 0).getFloat("rate", 0), .001f);
        assertEquals("true", js("document.querySelector('[data-rate=\"1.5\"]').getAttribute('aria-pressed')"));
        js("var input=document.querySelector('[data-bilispeed-control=custom-rate]');input.scrollIntoView({block:'nearest'});input.value='5.5';true");
        tap("[data-bilispeed-control=apply-rate]");
        assertFalse((Boolean) js("document.querySelector('[data-bilispeed-control=speed-error]').hidden"));
        assertEquals(1.5, ((Number) js("v.playbackRate")).doubleValue(), .001);
        js("document.querySelector('[data-bilispeed-control=custom-rate]').value='2.75';true");
        tap("[data-bilispeed-control=apply-rate]");
        await("v.playbackRate===2.75");
        assertEquals(2.75f, instrumentation.getTargetContext().getSharedPreferences("playback", 0).getFloat("rate", 0), .001f);
        click("close-panel");
        instrumentation.runOnMainSync(() -> activity.selectRate(3.5f));
        await("v.playbackRate===3.5&&document.querySelector('[data-bilispeed-control=speed]').textContent==='3.5x'");
        instrumentation.runOnMainSync(activity::finish); instrumentation.waitForIdleSync();
        Context context = instrumentation.getTargetContext();
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        instrumentation.runOnMainSync(() -> activity.browserForTesting().loadDataWithBaseURL(
                "https://www.bilibili.com/video/__bilispeed_controls__/", "<!doctype html><script>window.rateRestartFixture=true;</script><video></video>", "text/html", "UTF-8", null));
        await("window.rateRestartFixture&&window.__BiliSpeed&&window.__BiliSpeed.snapshot().selected===3.5");
    }

    @Test public void fullscreenSpeedPickerChangesRateWithoutLeavingFullscreenAndStaysOpen() throws Exception {
        tap("[data-bilispeed-control=fullscreen]");
        await("document.fullscreenElement&&innerWidth>innerHeight&&" + CONTROLS + ".dataset.hidden==='true'");
        dismissImmersiveHint(instrumentation);
        SystemClock.sleep(400);
        tap(HOST); await(CONTROLS + ".dataset.hidden==='false'");
        tap("[data-bilispeed-control=speed]");
        tap("[data-rate=\"1.5\"]");
        await("v.playbackRate===1.5");
        assertTrue((Boolean) js("!!document.fullscreenElement"));
        SystemClock.sleep(3300);
        assertEquals("false", js(CONTROLS + ".dataset.hidden"));
        assertTrue((Boolean) js("(function(){var r=document.getElementById('bilispeed-player-panel').getBoundingClientRect();return r.top>=0&&r.right<=innerWidth&&r.bottom<=innerHeight;})()"));
        screenshot("BiliSpeed-1.2.8-fullscreen-speed");
    }

    private AtomicInteger serveEpisodeDocuments(String metadata) {
        AtomicInteger loaded = new AtomicInteger();
        Object signal = new Object() { @android.webkit.JavascriptInterface public void ready() { loaded.incrementAndGet(); } };
        instrumentation.runOnMainSync(() -> {
            WebView view = activity.browserForTesting();
            WebViewClient original = view.getWebViewClient();
            view.addJavascriptInterface(signal, "NextEpisodeFixtureLoaded");
            view.setWebViewClient(new WebViewClient() {
                @Override public WebResourceResponse shouldInterceptRequest(WebView owner, WebResourceRequest request) {
                    if (!request.isForMainFrame()) return null;
                    String html = fixtureHtml + "<script>window.nextEpisodeFixture=true;" + metadata
                            + "window.__INITIAL_STATE__.videoData.bvid=location.pathname.split('/')[2];"
                            + "addEventListener('load',()=>NextEpisodeFixtureLoaded.ready());</script>";
                    return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                }
                @Override public void onPageStarted(WebView owner, String url, Bitmap favicon) { original.onPageStarted(owner, url, favicon); }
                @Override public void onPageFinished(WebView owner, String url) { original.onPageFinished(owner, url); }
            });
        });
        return loaded;
    }

    private void changeFixtureEpisode(String direction, AtomicInteger loads) throws Exception {
        int before = loads.get();
        tap("[data-bilispeed-control=" + direction + "]");
        long deadline = SystemClock.elapsedRealtime() + 15000;
        while (loads.get() == before && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
        assertTrue("Next episode document did not commit", loads.get() > before);
        await("window.nextEpisodeFixture&&document.querySelector('video').readyState>=2&&" + CONTROLS);
    }

    private void assertImmersiveEpisode() throws Exception {
        await("window.__BILI_TOUCH_FULLSCREEN__&&document.documentElement.hasAttribute('data-bilispeed-fullscreen')&&innerWidth>innerHeight");
        instrumentation.runOnMainSync(() -> {
            assertTrue(activity.fullscreenForTesting());
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.getRequestedOrientation());
            assertEquals(0, ((android.widget.FrameLayout.LayoutParams) activity.browserForTesting().getLayoutParams()).bottomMargin);
        });
        assertTrue((Boolean) js("(()=>{var r=document.querySelector('" + HOST + "').getBoundingClientRect();"
                + "return Math.abs(r.x)<2&&Math.abs(r.y)<2&&Math.abs(r.width-innerWidth)<2&&Math.abs(r.height-innerHeight)<2;})()"));
    }

    @Test public void fullscreenNextAndPreviousKeepLandscapePlaybackAndBackExits() throws Exception {
        String metadata = "window.__INITIAL_STATE__={videoData:{bvid:'BV17x411w7KC',aid:170001,pages:[{page:1,part:'第一集'},{page:2,part:'第二集'}]}};";
        js("history.replaceState({},'', '/video/BV17x411w7KC/');" + metadata + "window.__BiliTouch.refresh();true");
        await("document.querySelector('[data-bilispeed-control=next][data-episode-url]')");
        AtomicInteger loads = serveEpisodeDocuments(metadata);
        tap("[data-bilispeed-control=fullscreen]");
        await("document.fullscreenElement&&innerWidth>innerHeight&&" + CONTROLS + ".dataset.hidden==='true'");
        dismissImmersiveHint(instrumentation); SystemClock.sleep(400);
        tap(HOST); await(CONTROLS + ".dataset.hidden==='false'");
        tap("[data-bilispeed-control=speed]"); tap("[data-rate=\"2\"]");
        await("v.playbackRate===2");
        click("close-panel"); changeFixtureEpisode("next", loads);
        assertEquals("?p=2", js("location.search"));
        assertImmersiveEpisode();
        await("document.querySelector('video').playbackRate===2");
        tap(HOST); await(CONTROLS + ".dataset.hidden==='false'");
        tap("[data-bilispeed-control=play]"); await("!document.querySelector('video').paused");
        screenshot("BiliSpeed-1.2.9-fullscreen-next");
        changeFixtureEpisode("previous", loads);
        assertImmersiveEpisode();
        tap(HOST); await(CONTROLS + ".dataset.hidden==='false'");
        instrumentation.runOnMainSync(activity::onBackPressed);
        await("!document.documentElement.hasAttribute('data-bilispeed-fullscreen')&&innerHeight>innerWidth");
        assertFalse((Boolean) js("window.__BILI_TOUCH_FULLSCREEN__||!!document.fullscreenElement"));
        instrumentation.runOnMainSync(() -> assertFalse(activity.fullscreenForTesting()));
        // A queued speed configuration from the previous fullscreen page must
        // not reinstate its obsolete fullscreen flag after Android has exited.
        js("window.__BiliSpeed.configure({type:'config',rate:2,suspended:false,fullscreen:true});true");
        assertFalse((Boolean) js("window.__BILI_TOUCH_FULLSCREEN__||document.documentElement.hasAttribute('data-bilispeed-fullscreen')"));
        // Reproduce the official mini-player state reached by scrolling to
        // episodes after that navigation. It must remain in its document slot.
        js("var player=document.querySelector('.bpx-player-container');player.dataset.screen='mini';"
                + "player.style.cssText='position:fixed;right:84px;bottom:48px;width:320px;height:180px';"
                + "window.v=document.querySelector('video');window.__BiliTouch.refresh();true");
        assertEquals("relative", js("getComputedStyle(document.querySelector('.bpx-player-container')).position"));
        assertContained();
    }

    @Test public void fullscreenCollectionNavigationKeepsPresentationAndExitButtonRestoresPage() throws Exception {
        String metadata = "window.__INITIAL_STATE__={videoData:{bvid:'BV17x411w7KC',aid:170001,pages:[{page:1}],"
                + "ugc_season:{title:'合集',sections:[{episodes:[{bvid:'BV17x411w7KC',title:'第一集'},{bvid:'BV1xx411c7mD',title:'第二集'}]}]}}};";
        js("history.replaceState({},'', '/video/BV17x411w7KC/');" + metadata + "window.__BiliTouch.refresh();true");
        await("document.querySelector('[data-bilispeed-control=next][data-episode-url]')");
        AtomicInteger loads = serveEpisodeDocuments(metadata);
        tap("[data-bilispeed-control=fullscreen]");
        await("document.fullscreenElement&&innerWidth>innerHeight");
        dismissImmersiveHint(instrumentation); SystemClock.sleep(400);
        tap(HOST); await(CONTROLS + ".dataset.hidden==='false'");
        changeFixtureEpisode("next", loads);
        assertEquals("/video/BV1xx411c7mD/", js("location.pathname"));
        assertImmersiveEpisode();
        tap(HOST); await(CONTROLS + ".dataset.hidden==='false'");
        changeFixtureEpisode("previous", loads); assertImmersiveEpisode();
        tap(HOST); await(CONTROLS + ".dataset.hidden==='false'");
        tap("[data-bilispeed-control=fullscreen]");
        await("!window.__BILI_TOUCH_FULLSCREEN__&&!document.documentElement.hasAttribute('data-bilispeed-fullscreen')&&innerHeight>innerWidth");
        instrumentation.runOnMainSync(() -> assertFalse(activity.fullscreenForTesting()));
        assertContained();
    }

    @Test public void compactEpisodeControlsStayOnOneRowAndMoreOpensVolumeAndSubtitles() throws Exception {
        js("history.replaceState({},'', '/video/BV17x411w7KC/');window.__INITIAL_STATE__={videoData:{bvid:'BV17x411w7KC',aid:170001,"
                + "pages:[{page:1,part:'第一集'},{page:2,part:'第二集'}]}};window.__BiliTouch.refresh();true");
        await("document.querySelector('[data-bilispeed-control=next][data-episode-url]')");
        assertContained();
        assertTrue((Boolean) js("(()=>{var buttons=Array.from(document.querySelectorAll('#bilispeed-touch-controls>button:not([hidden])'));"
                + "var y=buttons[0].getBoundingClientRect().y;return buttons.every(b=>Math.abs(b.getBoundingClientRect().y-y)<1);})()"));
        if (Boolean.TRUE.equals(js("innerWidth<=560"))) {
            tap("[data-bilispeed-control=volume]");
            await("document.querySelector('[data-bilispeed-control=volume-slider]').checkVisibility()");
            tap("[data-bilispeed-control=close-panel]");
            tap("[data-bilispeed-control=subtitles]");
            await("document.getElementById('bilispeed-player-panel').textContent.includes('暂无可用字幕')");
        }
        screenshot("BiliSpeed-1.2.9-single-row-controls");
    }

    @Test public void subtitleLanguagesAndOffOperateRealTextTracks() throws Exception {
        js("window.zh=v.addTextTrack('subtitles','中文字幕','zh');zh.addCue(new VTTCue(0,5,'测试字幕'));"
                + "window.en=v.addTextTrack('subtitles','English','en');en.addCue(new VTTCue(0,5,'Test caption'));true");
        click("subtitles");
        js("document.querySelectorAll('[data-bilispeed-control=subtitle-track]')[0].click();true");
        await("zh.mode==='showing' && en.mode!=='showing' && document.querySelector('[data-bilispeed-control=subtitles]').getAttribute('aria-pressed')==='true'");
        click("subtitles");
        js("document.querySelectorAll('[data-bilispeed-control=subtitle-track]')[1].click();true");
        await("en.mode==='showing' && zh.mode==='disabled'");
        click("subtitles"); screenshot("BiliSpeed-player-subtitles"); click("subtitle-off");
        await("en.mode==='disabled' && zh.mode==='disabled' && document.querySelector('[data-bilispeed-control=subtitles]').getAttribute('aria-pressed')==='false'");
    }

    @Test public void officialSubtitleItemsReceiveClicksAndStateChanges() throws Exception {
        js("var root=document.createElement('div');root.className='bpx-player-ctrl-subtitle';"
                + "root.innerHTML='<div class=\"bpx-player-ctrl-subtitle-close-switch bpx-state-active\">关闭</div>'"
                + "+'<div class=\"bpx-player-ctrl-subtitle-major-content\"><div class=\"bpx-player-ctrl-subtitle-language-item\" data-lan=\"zh\">中文（自动生成）</div></div>';"
                + "document.querySelector('.bpx-player-control-wrap').append(root);window.captionClicks=0;"
                + "window.lang=root.querySelector('[data-lan]');window.off=root.querySelector('.bpx-player-ctrl-subtitle-close-switch');"
                + "lang.onclick=function(){captionClicks++;lang.classList.add('bpx-state-active');off.classList.remove('bpx-state-active');};"
                + "off.onclick=function(){captionClicks++;lang.classList.remove('bpx-state-active');off.classList.add('bpx-state-active');};true");
        await("window.__BiliTouchPlayer && lang.isConnected");
        js("window.__BiliTouchPlayer.refresh();true");
        click("subtitles"); click("subtitle-language");
        assertEquals(1, ((Number) js("captionClicks")).intValue());
        await("document.querySelector('[data-bilispeed-control=subtitles]').getAttribute('aria-pressed')==='true'");
        click("subtitles"); click("subtitle-off");
        assertEquals(2, ((Number) js("captionClicks")).intValue());
        js("lang.click();true");
        await("document.querySelector('[data-bilispeed-control=subtitles]').getAttribute('aria-pressed')==='true'");
    }

    @Test public void fullscreenStartsHiddenTapRevealsAndOpenPanelKeepsControls() throws Exception {
        tap("[data-bilispeed-control=fullscreen]");
        await("document.fullscreenElement && innerWidth>innerHeight && " + CONTROLS + ".dataset.hidden==='true'");
        dismissImmersiveHint(instrumentation);
        instrumentation.runOnMainSync(() -> assertTrue(activity.fullscreenForTesting()));
        assertEquals("hidden", js("getComputedStyle(" + CONTROLS + ").visibility"));
        screenshot("BiliSpeed-player-fullscreen-hidden");
        js("window.fullTouchTrace=[];['click','touchstart','touchmove','touchend','touchcancel','pointerdown','pointermove','pointercancel','mousedown','mousemove','mouseup','resize'].forEach(function(name){window.addEventListener(name,function(e){"
                + "fullTouchTrace.push({type:e.type,pointer:e.pointerType,target:e.target.id||e.target.className,cancelable:e.cancelable,touches:e.touches&&Array.from(e.touches).map(function(t){return {x:t.clientX,y:t.clientY};})});},true);});true");
        tap(HOST);
        await(CONTROLS + ".dataset.hidden==='false'");
        assertTrue((Boolean) js("v.paused"));
        screenshot("BiliSpeed-player-fullscreen-controls");
        SystemClock.sleep(3400);
        await(CONTROLS + ".dataset.hidden==='true'");
        tap(HOST); click("volume");
        SystemClock.sleep(3400);
        assertEquals("false", js(CONTROLS + ".dataset.hidden"));
        click("close-panel");
        SystemClock.sleep(3400);
        await(CONTROLS + ".dataset.hidden==='true'");
        js("window.fullTouchTrace=[];true");
        swipe(.25, .3, .65, .3);
        System.out.println("BILISPEED_FULL_GESTURE_TRACE=" + js("JSON.stringify({events:fullTouchTrace,time:v.currentTime,seeking:v.seeking,"
                + "host:document.querySelector('.bpx-player-video-area').getBoundingClientRect().toJSON(),viewport:innerWidth,"
                + "feedback:document.getElementById('bilispeed-seek-feedback').outerHTML})"));
        await("!v.seeking && v.currentTime>1.5");
        assertEquals("true", js(CONTROLS + ".dataset.hidden"));
        tap(HOST);
        await(CONTROLS + ".dataset.hidden==='false'");
        float[] rangeStart = point("[data-bilispeed-control=seek]", .35, .5), rangeEnd = point("[data-bilispeed-control=seek]", .7, .5);
        input(MotionEvent.ACTION_DOWN, rangeStart); input(MotionEvent.ACTION_MOVE, rangeEnd);
        SystemClock.sleep(3400);
        assertEquals("false", js(CONTROLS + ".dataset.hidden"));
        input(MotionEvent.ACTION_UP, rangeEnd);
        SystemClock.sleep(3400);
        await(CONTROLS + ".dataset.hidden==='true'");
        instrumentation.runOnMainSync(activity::onBackPressed);
        await("!document.fullscreenElement && " + CONTROLS + ".dataset.hidden==='false'");
    }

    static void dismissImmersiveHint(Instrumentation instrumentation) {
        if ("confirmed".equals(android.provider.Settings.Secure.getString(
                instrumentation.getTargetContext().getContentResolver(), "immersive_mode_confirmations"))) return;
        AccessibilityServiceInfo service = instrumentation.getUiAutomation().getServiceInfo();
        service.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        instrumentation.getUiAutomation().setServiceInfo(service);
        long deadline = SystemClock.elapsedRealtime() + 4000;
        while (SystemClock.elapsedRealtime() < deadline) {
            for (AccessibilityWindowInfo window : instrumentation.getUiAutomation().getWindows()) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText("Got it")) {
                    if (!"com.android.systemui".contentEquals(node.getPackageName())) continue;
                    AccessibilityNodeInfo button = node;
                    while (button != null && !button.isClickable()) button = button.getParent();
                    if (button != null && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        SystemClock.sleep(400); return;
                    }
                }
            }
            SystemClock.sleep(100);
        }
    }

    @Test public void replacingVideoCancelsOldPreviewAndReusesOnlyOneToolbar() throws Exception {
        click("volume");
        js("touchEvent('touchstart',[[1,80,50]]);touchEvent('touchmove',[[1,220,50]]);"
                + "var replacement=v.cloneNode(true);v.replaceWith(replacement);window.v=replacement;true");
        await("v.readyState>=2 && " + CONTROLS + ".isConnected && document.getElementById('bilispeed-player-panel').hidden");
        js("touchEvent('touchend',[]);true");
        assertTrue((Boolean) js("v.currentTime<.1 && document.querySelectorAll('#bilispeed-touch-controls').length===1"));
        assertTrue((Boolean) js("document.getElementById('bilispeed-seek-feedback').hidden"));
        js("document.getElementById('playerWrap').remove();true");
        await("!document.getElementById('bilispeed-touch-controls')");
    }

    @Test public void controlsAndDanmakuUpdatesDoNotRescanThePage() throws Exception {
        js("var danmaku=document.createElement('div');danmaku.className='bpx-player-dm-wrap';"
                + "document.querySelector('.bpx-player-video-area').append(danmaku);true");
        SystemClock.sleep(350);
        js("window._touchQueries=0;window._queryBeforeProbe=document.querySelector;"
                + "document.querySelector=function(selector){"
                + "if(selector==='#mirror-vdcon .video-toolbar-container')window._touchQueries++;"
                + "return window._queryBeforeProbe.call(this,selector);};"
                + "window._churnCount=0;window._churn=setInterval(function(){"
                + "danmaku.textContent='弹幕 '+_churnCount;"
                + "document.querySelector('[data-bilispeed-control=time]').textContent='进度 '+_churnCount;"
                + "if(++_churnCount===60)clearInterval(_churn);},16);true");
        await("window._churnCount===60");
        SystemClock.sleep(250);
        int queries = ((Number) js("window._touchQueries")).intValue();
        System.out.println("BILISPEED_TOUCH_CHURN_QUERIES=" + queries);
        assertEquals("Control labels and danmaku must not trigger whole-page layout scans", 0, queries);
    }

    @Test public void backgroundDefersTouchLayoutScansAndDiscoversReplacementOnResume() throws Exception {
        SystemClock.sleep(350);
        click("subtitles");
        js("window._subtitleBeforePause=document.querySelector('[data-subtitle-option]');true");
        instrumentation.runOnMainSync(() -> instrumentation.callActivityOnPause(activity));
        try {
            js("window._touchQueries=0;window._queryBeforeProbe=document.querySelector;"
                    + "document.querySelector=function(selector){"
                    + "if(selector==='#mirror-vdcon .video-toolbar-container')window._touchQueries++;"
                    + "return window._queryBeforeProbe.call(this,selector);};"
                    + "v.addTextTrack('subtitles','后台新字幕','zh');"
                    + "var replacement=v.cloneNode(false);replacement.id='replacement';v.replaceWith(replacement);"
                    + "window._churnRoot=document.createElement('div');document.body.append(_churnRoot);true");
            // onPause can throttle JavaScript timers. Drive the DOM changes from
            // instrumentation instead so this checks the app's observation policy.
            for (int index = 0; index < 8; index++) {
                js("_churnRoot.append(document.createElement('div'));true");
                SystemClock.sleep(175);
            }
            SystemClock.sleep(1500);
            int queries = ((Number) js("window._touchQueries")).intValue();
            System.out.println("BILISPEED_BACKGROUND_TOUCH_QUERIES=" + queries);
            assertEquals("Touch layout must not keep scanning while the activity is paused", 0, queries);
            assertTrue("Subtitle track events must not rebuild the background panel",
                    (Boolean) js("_subtitleBeforePause===document.querySelector('[data-subtitle-option]')"));
        } finally {
            instrumentation.runOnMainSync(() -> instrumentation.callActivityOnResume(activity));
        }
        await("document.getElementById('replacement').readyState>=2 && !document.querySelector('[data-bilispeed-control=seek]').disabled");
        click("play");
        await("!document.getElementById('replacement').paused");
        assertEquals(1, ((Number) js("document.querySelectorAll('#bilispeed-touch-controls').length")).intValue());
    }

    @Test public void liveAndUnavailableSubtitlesHaveClearStates() throws Exception {
        click("subtitles");
        assertTrue((Boolean) js("document.getElementById('bilispeed-player-panel').textContent.includes('暂无可用字幕')"));
        click("close-panel");
        js("Object.defineProperty(v,'duration',{configurable:true,value:Infinity});v.dispatchEvent(new Event('durationchange'));true");
        assertTrue((Boolean) js("document.querySelector('[data-bilispeed-control=seek]').disabled"));
        assertEquals("直播", js("document.querySelector('[data-bilispeed-control=time]').textContent"));
        swipe(.25, .3, .65, .3);
        assertEquals(1, ((Number) js("v.currentTime")).doubleValue(), .15);
    }

    @Test public void rotationKeepsControlsContainedAndOriginalLayoutRetainsZoom() throws Exception {
        assertContained();
        instrumentation.runOnMainSync(() -> {
            assertFalse(activity.browserForTesting().getSettings().supportZoom());
            assertFalse(activity.browserForTesting().getSettings().getBuiltInZoomControls());
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        });
        await("innerWidth>innerHeight");
        assertContained();
        instrumentation.runOnMainSync(() -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        await("innerHeight>innerWidth");
        assertContained();
        screenshot("BiliSpeed-player-portrait");
        instrumentation.runOnMainSync(() -> {
            activity.setTouchLayout(false);
            assertTrue(activity.browserForTesting().getSettings().supportZoom());
            assertTrue(activity.browserForTesting().getSettings().getBuiltInZoomControls());
        });
    }

    @Test public void portraitVideoUsesItsRealAspectRatioWithoutFillingTheWholePage() throws Exception {
        File movie = new File(instrumentation.getTargetContext().getCacheDir(), "player-portrait-fixture.mp4");
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = new MediaMuxer(movie.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        try (android.content.res.AssetFileDescriptor asset = instrumentation.getContext().getAssets().openFd("flower.mp4")) {
            extractor.setDataSource(asset.getFileDescriptor(), asset.getStartOffset(), asset.getDeclaredLength());
            int[] tracks = new int[extractor.getTrackCount()];
            for (int index = 0; index < tracks.length; index++) {
                tracks[index] = muxer.addTrack(extractor.getTrackFormat(index)); extractor.selectTrack(index);
            }
            muxer.setOrientationHint(90); muxer.start();
            ByteBuffer buffer = ByteBuffer.allocateDirect(2 * 1024 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (extractor.getSampleTrackIndex() >= 0) {
                buffer.clear(); int length = extractor.readSampleData(buffer, 0);
                if (length < 0) break;
                info.set(0, length, extractor.getSampleTime(), extractor.getSampleFlags());
                muxer.writeSampleData(tracks[extractor.getSampleTrackIndex()], buffer, info); extractor.advance();
            }
            muxer.stop();
        } finally { extractor.release(); muxer.release(); }
        String portrait;
        try (InputStream input = new FileInputStream(movie)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            portrait = "data:video/mp4;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
        }
        js("v.src='" + portrait + "';v.load();true");
        await("v.readyState>=2 && v.videoHeight>v.videoWidth");
        js("v.currentTime=1;true");
        await("!v.seeking && v.currentTime>.9");
        await("document.getElementById('playerWrap').getBoundingClientRect().height>innerWidth");
        assertContained();
        assertTrue((Boolean) js("document.getElementById('playerWrap').getBoundingClientRect().height <= innerHeight*.68+2"));
        screenshot("BiliSpeed-player-portrait-video");
    }

    private void assertContained() throws Exception {
        assertTrue((Boolean) js("(function(){var h=document.querySelector('" + HOST + "').getBoundingClientRect();"
                + "return document.documentElement.scrollWidth<=innerWidth+2 && getComputedStyle(v).objectFit==='contain' && "
                + "Array.from(document.querySelectorAll('#bilispeed-touch-controls>button:not([hidden])')).every(function(b){var r=b.getBoundingClientRect();"
                + "return r.width>=44 && r.height>=44 && r.left>=h.left-1 && r.right<=h.right+1 && r.bottom<=h.bottom+1;});})()"));
    }
}
