package app.bilispeed.browser;

import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.MediaExtractor;
import android.media.MediaMuxer;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class PlaybackInstrumentationTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;
    private String media;
    private int fixtureId;
    private static String cachedMedia;

    @Before public void launch() throws Exception {
        Context target = instrumentation.getTargetContext();
        target.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("touchLayout", !"true".equals(InstrumentationRegistry.getArguments().getString("originalLayout"))).commit();
        target.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().putBoolean("automatic", false).commit();
        if (cachedMedia == null) createLongFixture();
        media = cachedMedia;
        activity = start();
        loadFixture("https://www.bilibili.com/__bilispeed_test__/");
    }

    private void createLongFixture() throws Exception {
        File movie = new File(instrumentation.getTargetContext().getCacheDir(), "long-fixture.mp4");
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = new MediaMuxer(movie.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        try (android.content.res.AssetFileDescriptor asset = instrumentation.getContext().getAssets().openFd("flower.mp4")) {
            extractor.setDataSource(asset.getFileDescriptor(), asset.getStartOffset(), asset.getDeclaredLength());
            int[] tracks = new int[extractor.getTrackCount()];
            long duration = 0;
            for (int track = 0; track < tracks.length; track++) {
                MediaFormat format = extractor.getTrackFormat(track);
                duration = Math.max(duration, format.getLong(MediaFormat.KEY_DURATION));
                tracks[track] = muxer.addTrack(format);
                extractor.selectTrack(track);
            }
            muxer.start();
            ByteBuffer sample = ByteBuffer.allocateDirect(4 * 1024 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            // Concatenate the same CC0 clip into a continuous 20s movie. A short
            // looping video spends time seeking between loops, skewing speed measurements.
            for (int repeat = 0; repeat < 4; repeat++) {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                while (extractor.getSampleTrackIndex() >= 0) {
                    sample.clear();
                    int size = extractor.readSampleData(sample, 0);
                    if (size < 0) break;
                    info.set(0, size, extractor.getSampleTime() + repeat * duration, extractor.getSampleFlags());
                    muxer.writeSampleData(tracks[extractor.getSampleTrackIndex()], sample, info);
                    extractor.advance();
                }
            }
            muxer.stop();
        } finally {
            extractor.release(); muxer.release();
        }
        try (InputStream input = new FileInputStream(movie)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int size;
            while ((size = input.read(buffer)) != -1) output.write(buffer, 0, size);
            cachedMedia = "data:video/mp4;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
        }
    }

    @After public void finish() {
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }

    private MainActivity start() {
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return (MainActivity) instrumentation.startActivitySync(intent);
    }

    private void loadFixture(String origin) throws Exception {
        loadFixture(origin, null);
    }

    private void loadFixture(String origin, String historyUrl) throws Exception {
        int identity = ++fixtureId;
        String html = "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<script>window.__BILISPEED_TEST_FIXTURE__=" + identity + ";</script>"
                + "<style>body{margin:0;background:#fff}video{width:100%;max-height:280px}"
                + "button{font-size:18px;padding:12px}</style>"
                + "<video id='v' controls playsinline muted loop preload='auto' src='" + media + "'></video>"
                + "<button id='fs' onclick=\"window._fsClicks=(window._fsClicks||0)+1;"
                + "document.getElementById('v').requestFullscreen().catch(function(e){window._fsError=e.message;})\">全屏</button>";
        instrumentation.runOnMainSync(() -> {
            WebView browser = activity.browserForTesting();
            browser.stopLoading();
            browser.getSettings().setMediaPlaybackRequiresUserGesture(false);
            browser.loadDataWithBaseURL(origin, html, "text/html", "UTF-8", historyUrl);
        });
        await("window.__BILISPEED_TEST_FIXTURE__ === " + identity
                + " && document.getElementById('v') && document.getElementById('v').readyState >= 2", 20000);
        if (MainActivity.isBiliHttps(origin)) await("!!window.__BiliSpeed", 10000);
    }

    private Object js(String script) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> value = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> activity.browserForTesting().evaluateJavascript(script, result -> {
            value.set(result); latch.countDown();
        }));
        assertTrue("JavaScript callback timed out: " + script.substring(0, Math.min(script.length(), 100)), latch.await(6, TimeUnit.SECONDS));
        return new JSONTokener(value.get()).nextValue();
    }

    private void await(String condition, long timeout) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeout;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (Boolean.TRUE.equals(js("Boolean(" + condition + ")"))) return;
            SystemClock.sleep(100);
        }
        fail("Condition timed out: " + condition);
    }

    private JSONObject snapshot() throws Exception {
        return new JSONObject((String) js("JSON.stringify(window.__BiliSpeed.snapshot())"));
    }

    private void tapElement(String selector) throws Exception {
        js("document.querySelector('" + selector + "').scrollIntoView({block:'center'}); true");
        instrumentation.runOnMainSync(() -> activity.browserForTesting().requestFocus());
        SystemClock.sleep(200);
        JSONObject point = new JSONObject((String) js("JSON.stringify((function(){var r=document.querySelector('"
                + selector + "').getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,width:innerWidth};})())"));
        int[] position = new int[2];
        float[] scale = new float[1];
        instrumentation.runOnMainSync(() -> {
            activity.browserForTesting().getLocationOnScreen(position);
            scale[0] = (float) (activity.browserForTesting().getWidth() / point.optDouble("width"));
        });
        float x = position[0] + (float) point.getDouble("x") * scale[0];
        float y = position[1] + (float) point.getDouble("y") * scale[0];
        System.out.println("BILISPEED_TAP_POINT=" + x + "," + y + " CSS=" + point);
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 70, MotionEvent.ACTION_UP, x, y, 0);
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        assertTrue(instrumentation.getUiAutomation().injectInputEvent(down, true));
        assertTrue(instrumentation.getUiAutomation().injectInputEvent(up, true));
        down.recycle(); up.recycle();
    }

    private void choose(float rate) throws Exception {
        instrumentation.runOnMainSync(() -> activity.selectRate(rate));
        await("Math.abs(document.getElementById('v').playbackRate - " + rate + ") < 0.001", 5000);
    }

    private View findText(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findText(group.getChildAt(index), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private <T extends View> T findType(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                T found = findType(group.getChildAt(index), type);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test public void everyPresetChangesRealPlaybackSpeedThroughButtons() throws Exception {
        float[] rates = {1.25f, 1.5f, 2, 2.5f, 3, 3.5f, 4, 5, 1};
        instrumentation.runOnMainSync(activity::openSpeedPanel);
        for (float rate : rates) {
            instrumentation.runOnMainSync(() -> {
                View button = findText(activity.speedDialogForTesting().getWindow().getDecorView(), MainActivity.formatRate(rate));
                assertTrue("Missing preset button " + rate, button instanceof Button);
                button.performClick();
            });
            await("Math.abs(document.getElementById('v').playbackRate - " + rate + ") < 0.001", 5000);
            js("document.getElementById('v').pause(); document.getElementById('v').currentTime=0.15; true");
            await("!document.getElementById('v').seeking && Math.abs(document.getElementById('v').currentTime-0.15)<0.05", 5000);
            js("document.getElementById('v').play(); true");
            await("document.getElementById('v').currentTime > 0.25 && !document.getElementById('v').paused", 5000);
            SystemClock.sleep(350);
            double[] samples = new double[3];
            for (int sample = 0; sample < samples.length; sample++) {
                js("window._probe=null; window._probeVideo=document.getElementById('v'); window._startTime=_probeVideo.currentTime;"
                    + "window._previousTime=_startTime; window._loops=0; window._startWall=performance.now();"
                    + "window._countLoop=function(){if(_probeVideo.currentTime+0.5<_previousTime)_loops++;"
                    + "_previousTime=_probeVideo.currentTime;}; _probeVideo.addEventListener('timeupdate',_countLoop);"
                    + "setTimeout(function(){_countLoop();"
                    + "window._probe=(_probeVideo.currentTime+_loops*_probeVideo.duration-_startTime)/"
                    + "((performance.now()-_startWall)/1000); _probeVideo.removeEventListener('timeupdate',_countLoop);},900); true");
                await("window._probe !== null", 5000);
                samples[sample] = ((Number) js("window._probe")).doubleValue();
            }
            java.util.Arrays.sort(samples);
            double measured = samples[1];
            System.out.println("BILISPEED_RATE_PROBE=" + rate + "x measured=" + measured + " state="
                    + js("JSON.stringify({hidden:document.hidden,state:window.__BiliSpeed.snapshot(),"
                    + "time:document.getElementById('v').currentTime,ready:document.getElementById('v').readyState})"));
            assertEquals("Measured media speed for " + rate + "x", rate, measured, rate * 0.18 + 0.12);
            assertEquals(rate, snapshot().getDouble("rate"), 0.001);
            assertTrue((Boolean) js("document.getElementById('v').preservesPitch"));
            js("document.getElementById('v').pause(); true");
        }
    }

    @Test public void websiteResetsAndVideoReplacementKeepSelectedRate() throws Exception {
        choose(3.5f);
        js("document.getElementById('v').playbackRate=1; document.getElementById('v').defaultPlaybackRate=2; true");
        assertEquals(3.5, ((Number) js("document.getElementById('v').playbackRate")).doubleValue(), 0.001);
        js("var next=document.getElementById('v').cloneNode(false); document.getElementById('v').replaceWith(next); true");
        await("document.getElementById('v').readyState >= 2 && document.getElementById('v').playbackRate === 3.5", 15000);
        await("window.__BiliSpeed.snapshot().videos === 1", 3000);
    }

    @Test public void customInputAndInvalidRates() throws Exception {
        instrumentation.runOnMainSync(() -> {
            activity.openSpeedPanel();
            View panel = activity.speedDialogForTesting().getWindow().getDecorView();
            EditText input = findType(panel, EditText.class);
            input.setText("2.73");
            findText(panel, "应用").performClick();
        });
        await("document.getElementById('v').playbackRate === 2.73", 5000);
        assertFalse((Boolean) js("window.__BiliSpeed.setRate(5.01)"));
        assertFalse((Boolean) js("window.__BiliSpeed.setRate(0)"));
        assertFalse((Boolean) js("window.__BiliSpeed.setRate(NaN)"));
        assertFalse((Boolean) js("window.__BiliSpeed.setRate(Infinity)"));
        assertEquals(2.73, snapshot().getDouble("selected"), 0.001);
        assertFalse(MainActivity.validRate(Float.NaN));
        assertFalse(MainActivity.validRate(6));
    }

    @Test public void iframeReceivesRateChanges() throws Exception {
        choose(2.5f);
        js("var f=document.createElement('iframe'); f.id='f'; f.srcdoc=document.getElementById('v').outerHTML; document.body.append(f); true");
        await("document.getElementById('f').contentWindow.__BiliSpeed && document.getElementById('f').contentDocument.querySelector('video').readyState >= 2", 15000);
        choose(4);
        await("document.getElementById('f').contentDocument.querySelector('video').playbackRate === 4", 5000);
        js("document.getElementById('f').contentDocument.querySelector('video').playbackRate=1; true");
        assertEquals(4, ((Number) js("document.getElementById('f').contentDocument.querySelector('video').playbackRate")).doubleValue(), 0.001);
    }

    @Test public void openShadowRootVideoIsControlled() throws Exception {
        choose(4);
        js("var host=document.createElement('div'); host.id='host'; document.body.append(host);"
                + "var shadow=host.attachShadow({mode:'open'}); shadow.append(document.getElementById('v').cloneNode(false)); true");
        await("document.getElementById('host').shadowRoot.querySelector('video').readyState >= 2", 15000);
        assertEquals(4, ((Number) js("document.getElementById('host').shadowRoot.querySelector('video').playbackRate")).doubleValue(), 0.001);
        assertEquals(2, snapshot().getInt("videos"));
    }

    @Test public void remembersAcrossLaunchAndCanDisableRemembering() throws Exception {
        choose(3.5f);
        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
        activity = start();
        loadFixture(MainActivity.HOME);
        assertEquals(3.5, snapshot().getDouble("selected"), 0.001);
        instrumentation.runOnMainSync(() -> {
            activity.openSpeedPanel();
            Switch remember = findType(activity.speedDialogForTesting().getWindow().getDecorView(), Switch.class);
            remember.setChecked(false);
            activity.speedDialogForTesting().dismiss();
            activity.finish();
        });
        instrumentation.waitForIdleSync();
        activity = start();
        loadFixture(MainActivity.HOME);
        assertEquals(1, snapshot().getDouble("selected"), 0.001);
    }

    @Test public void fullScreenCanChangeSpeedAndBackKeepsPlayback() throws Exception {
        choose(3);
        js("document.getElementById('v').play(); true");
        await("!document.getElementById('v').paused", 5000);
        tapElement("#fs");
        long deadline = SystemClock.elapsedRealtime() + 8000;
        java.util.concurrent.atomic.AtomicBoolean fullscreen = new java.util.concurrent.atomic.AtomicBoolean();
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync(() -> fullscreen.set(activity.fullscreenForTesting()
                    && activity.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE));
            if (fullscreen.get()) break;
            SystemClock.sleep(100);
        }
        System.out.println("BILISPEED_FULLSCREEN_DOM=" + js("JSON.stringify({active:!!document.fullscreenElement,"
                + "enabled:document.fullscreenEnabled,clicks:window._fsClicks,error:window._fsError,focus:document.hasFocus()})"));
        instrumentation.runOnMainSync(() -> {
            View errorTitle = findText(activity.getWindow().getDecorView(), "页面暂时打不开");
            System.out.println("BILISPEED_FULLSCREEN_NATIVE=fullscreen:" + activity.fullscreenForTesting()
                    + " orientation:" + activity.getResources().getConfiguration().orientation
                    + " focus:" + activity.hasWindowFocus() + " errorOverlay:" + (errorTitle != null && errorTitle.isShown()));
        });
        assertTrue("HTML5 fullscreen was not entered", fullscreen.get());
        instrumentation.runOnMainSync(() -> assertFalse("Bottom navigation must not cover fullscreen video",
                findText(activity.getWindow().getDecorView(), "首页").isShown()));
        instrumentation.runOnMainSync(() -> {
            activity.openSpeedPanel();
            assertTrue(activity.speedDialogForTesting().isShowing());
            View button = findText(activity.speedDialogForTesting().getWindow().getDecorView(), "5x");
            button.performClick();
            activity.speedDialogForTesting().dismiss();
        });
        await("document.getElementById('v').playbackRate === 5", 5000);
        SystemClock.sleep(6500);
        System.out.println("BILISPEED_FULLSCREEN_PLAYBACK=" + js("JSON.stringify({hidden:document.hidden,focus:document.hasFocus(),state:window.__BiliSpeed.snapshot()})"));
        instrumentation.runOnMainSync(() -> assertTrue("Fullscreen playback must keep the screen awake",
                (activity.getWindow().getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0));
        instrumentation.runOnMainSync(activity::onBackPressed);
        assertFalse(activity.fullscreenForTesting());
        instrumentation.runOnMainSync(() -> assertTrue("Bottom navigation must return after fullscreen",
                findText(activity.getWindow().getDecorView(), "首页").isShown()));
        assertEquals(5, snapshot().getDouble("selected"), 0.001);
        assertFalse((Boolean) js("document.getElementById('v').paused"));
    }

    @Test public void leavingAppPausesVideo() throws Exception {
        js("document.getElementById('v').play(); true");
        await("!document.getElementById('v').paused", 5000);
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        instrumentation.getTargetContext().startActivity(home);
        await("document.getElementById('v').paused", 5000);
    }

    @Test public void largeWebViewHistoryUsesBoundedStateAndPreservesUrlAndRate() throws Exception {
        choose(3.5f);
        Bundle state = new Bundle();
        instrumentation.runOnMainSync(() -> instrumentation.callActivityOnSaveInstanceState(activity, state));
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(state);
            System.out.println("BILISPEED_SAVED_STATE_BYTES=" + parcel.dataSize());
            assertTrue("Saved state exceeded the activity transaction budget", parcel.dataSize() < 300 * 1024);
        } finally { parcel.recycle(); }
        assertNull("The multi-megabyte data URL history must use the link fallback", state.getBundle("browserState"));
        assertNull("Temporary data URLs must not be stored as a navigation fallback", state.getString("currentUrl"));
        assertEquals(3.5f, state.getFloat("selectedRate"), 0.001f);
        String url = "https://www.bilibili.com/__bilispeed_test__/";
        loadFixture(url, url);
        Bundle httpsState = new Bundle();
        instrumentation.runOnMainSync(() -> instrumentation.callActivityOnSaveInstanceState(activity, httpsState));
        assertEquals(url, httpsState.getString("currentUrl"));
        assertEquals(3.5f, httpsState.getFloat("selectedRate"), 0.001f);
    }

    @Test public void bulkPageChangesDoNotRepeatWholePagePlayerQueries() throws Exception {
        loadFixture("https://www.bilibili.com/video/__bilispeed_test__/");
        js("window._playerQueries=0; window._originalQuery=document.querySelector;"
                + "document.querySelector=function(selector){"
                + "if(selector==='#mirror-vdcon .video-toolbar-container')window._playerQueries++;"
                + "return window._originalQuery.call(this,selector);};"
                + "var fragment=document.createDocumentFragment();"
                + "for(var i=0;i<400;i++){var item=document.createElement('div');item.textContent='Item '+i;fragment.append(item);}"
                + "document.body.append(fragment); true");
        SystemClock.sleep(350);
        int queries = ((Number) js("window._playerQueries")).intValue();
        System.out.println("BILISPEED_BULK_PLAYER_QUERIES=" + queries);
        assertTrue("400 added nodes caused " + queries + " whole-page player queries", queries <= 8);
    }

    @Test public void backgroundBlocksLatePlaybackAndNewAutoplayVideos() throws Exception {
        js("document.getElementById('v').play(); true");
        await("!document.getElementById('v').paused", 5000);
        instrumentation.runOnMainSync(() -> instrumentation.callActivityOnPause(activity));
        try {
            await("document.getElementById('v').paused", 5000);
            js("window._latePlayDone=false;document.getElementById('v').play()"
                    + ".catch(function(){}).then(function(){window._latePlayDone=true;}); true");
            await("window._latePlayDone", 5000);
            assertTrue("A delayed play request restarted the background video",
                    (Boolean) js("document.getElementById('v').paused"));
            js("var next=document.getElementById('v').cloneNode(false);next.id='late';next.autoplay=true;"
                    + "document.body.append(next); true");
            await("document.getElementById('late').readyState >= 2", 15000);
            SystemClock.sleep(300);
            assertTrue("A newly mounted autoplay video played in the background",
                    (Boolean) js("document.getElementById('late').paused"));
        } finally {
            instrumentation.runOnMainSync(() -> instrumentation.callActivityOnResume(activity));
        }
        assertTrue("Returning to the app should keep the paused video paused",
                (Boolean) js("document.getElementById('v').paused"));
        js("document.getElementById('v').play(); true");
        await("!document.getElementById('v').paused", 5000);
    }

    @Test public void bridgeIsUnavailableOnUntrustedOrigin() throws Exception {
        loadFixture("https://example.org/__bilispeed_test__/");
        assertEquals("undefined", js("typeof window.BiliSpeedBridge"));
        assertEquals("undefined", js("typeof window.__BiliSpeed"));
    }

    @Test public void reattachedShadowRootDiscoversNewVideos() throws Exception {
        choose(3.5f);
        js("window._host=document.createElement('div');var shadow=_host.attachShadow({mode:'open'});"
                + "shadow.append(document.getElementById('v').cloneNode(false));document.body.append(_host);true");
        await("window.__BiliSpeed.snapshot().videos === 2", 5000);
        js("_host.remove();true");
        await("window.__BiliSpeed.snapshot().videos === 1", 5000);
        js("_host.shadowRoot.innerHTML='';var next=document.getElementById('v').cloneNode(false);"
                + "next.id='new-shadow-video';_host.shadowRoot.append(next);document.body.append(_host);true");
        await("_host.shadowRoot.querySelector('video').readyState >= 2", 15000);
        await("window.__BiliSpeed.snapshot().videos === 2", 5000);
        assertEquals(3.5, ((Number) js("_host.shadowRoot.querySelector('video').playbackRate")).doubleValue(), 0.001);
    }

    @Test public void backgroundIframeInShadowRootResumesWithLatestRate() throws Exception {
        choose(2.5f);
        instrumentation.runOnMainSync(() -> instrumentation.callActivityOnPause(activity));
        try {
            js("var host=document.createElement('div');host.id='frame-host';document.body.append(host);"
                    + "var shadow=host.attachShadow({mode:'open'});var frame=document.createElement('iframe');"
                    + "frame.srcdoc=document.getElementById('v').outerHTML;shadow.append(frame);true");
            await("document.getElementById('frame-host').shadowRoot.querySelector('iframe').contentWindow.__BiliSpeed", 15000);
            js("window._child=document.getElementById('frame-host').shadowRoot.querySelector('iframe').contentWindow;"
                    + "_child.document.querySelector('video').play().catch(function(){});true");
            SystemClock.sleep(300);
            assertTrue((Boolean) js("_child.document.querySelector('video').paused"));
        } finally {
            instrumentation.runOnMainSync(() -> instrumentation.callActivityOnResume(activity));
        }
        choose(4);
        await("_child.document.querySelector('video').readyState >= 2 && _child.document.querySelector('video').playbackRate === 4", 15000);
        js("_child.document.querySelector('video').play();true");
        await("!_child.document.querySelector('video').paused", 5000);
    }

    @Test public void resolvesShareTextBareLinksAndBvWithoutAcceptingInvalidUrls() {
        assertEquals("https://b23.tv/Ab123", MainActivity.resolveUrlInput("【视频标题】 https://b23.tv/Ab123。"));
        assertEquals("https://m.bilibili.com/video/BV1xx411c7mD", MainActivity.resolveUrlInput("观看视频 (https://m.bilibili.com/video/BV1xx411c7mD)"));
        assertEquals("https://b23.tv/Ab123", MainActivity.resolveUrlInput("b23.tv/Ab123"));
        assertEquals(MainActivity.HOME + "video/BV1xx411c7mD", MainActivity.resolveUrlInput("分享视频 BV1xx411c7mD"));
        assertEquals("https://www.bilibili.com/", MainActivity.resolveUrlInput("http://www.bilibili.com/"));
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", MainActivity.resolveUrlInput("https://en.wikipedia.org/wiki/Foo_(bar)"));
        assertEquals("https://[::1]", MainActivity.resolveUrlInput("https://[::1]"));
        assertNull(MainActivity.resolveUrlInput("javascript:alert(1)"));
        assertNull(MainActivity.resolveUrlInput("https://user:password@m.bilibili.com/"));
        assertNull(MainActivity.resolveUrlInput("这个文本不包含链接"));
        assertFalse(MainActivity.isHttps("https://m.bilibili.com/path with spaces"));
        assertTrue(MainActivity.isHttps("HTTPS://www.bilibili.com:443/video/BV1xx411c7mD?p=2"));
        assertFalse(MainActivity.isHttps("https:/www.bilibili.com/"));
        assertFalse(MainActivity.isHttps("data:video/mp4;base64,AAAA"));
        assertFalse(MainActivity.isBiliHttps("https://m.bilibili.com:8443/"));
        assertTrue(MainActivity.isBiliHttps("https://m.bilibili.com:443/"));
    }

    @Test public void deepLinksAndOriginValidation() {
        assertTrue(MainActivity.isBiliHttps("https://m.bilibili.com/video/BV1xx411c7mD"));
        assertTrue(MainActivity.isBiliHttps("https://passport.bilibili.com/"));
        assertFalse(MainActivity.isBiliHttps("https://bilibili.com.attacker.test/"));
        assertFalse(MainActivity.isBiliHttps("https://fakebilibili.com/"));
        assertFalse(MainActivity.isBiliHttps("http://m.bilibili.com/"));
        assertFalse(MainActivity.isBiliHttps("https://user:password@m.bilibili.com/"));
        assertFalse(MainActivity.isHttps("javascript:alert(1)"));
        assertEquals(MainActivity.HOME + "video/BV1xx411c7mD", MainActivity.deepLinkFallback("bilibili://video/BV1xx411c7mD"));
        assertEquals(MainActivity.HOME + "video/av170001", MainActivity.deepLinkFallback("bilibili://video/170001"));
        assertNull(MainActivity.deepLinkFallback("intent://fake/#Intent;scheme=https;S.browser_fallback_url=https%3A%2F%2Fevil.test;end"));
    }
}
