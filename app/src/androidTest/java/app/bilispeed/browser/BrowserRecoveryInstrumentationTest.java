package app.bilispeed.browser;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewRenderProcess;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONTokener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.*;

/** Terminate the actual WebView renderer while keeping the native Activity alive. */
public class BrowserRecoveryInstrumentationTest {
    private static final String PAGE = "https://www.bilibili.com/video/__bilispeed_recovery__/?p=2&t=15#reply";
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;
    private static String media;

    @Before public void launch() throws Exception {
        Context context = instrumentation.getTargetContext();
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear()
                .putFloat("rate", 3.5f).putFloat("normal_x", 0.3f).putFloat("normal_y", 0.7f).commit();
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
        fixture();
    }

    @After public void finish() {
        instrumentation.runOnMainSync(() -> {
            CookieManager.getInstance().setCookie(PAGE, "bilispeed_recovery_probe=; Max-Age=0; Path=/; Secure");
            if (activity != null) activity.finish();
        });
        instrumentation.waitForIdleSync();
    }

    private void fixture() throws Exception {
        String html = "<!doctype html><meta name='viewport' content='width=device-width'>"
                + "<script>window.recoveryFixture=true;</script><div id='mirror-vdcon'><div class='left-container'>"
                + "<div id='playerWrap'><div id='bilibili-player'><video id='v' playsinline muted preload='auto' src='" + media + "'></video></div></div>"
                + "<div class='video-toolbar-container'></div><div id='v_desc'>恢复测试</div></div></div>";
        instrumentation.runOnMainSync(() -> {
            activity.browserForTesting().stopLoading();
            activity.browserForTesting().loadDataWithBaseURL(PAGE, html, "text/html", "UTF-8", PAGE);
        });
        long deadline = SystemClock.elapsedRealtime() + 20000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (Boolean.TRUE.equals(js("Boolean(window.recoveryFixture && window.__BiliSpeed && window.__BiliTouchPlayer && document.getElementById('v').readyState>=2)"))) return;
            SystemClock.sleep(100);
        }
        fail("Recovery fixture did not load");
    }

    private Object js(String script) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> value = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> activity.browserForTesting().evaluateJavascript(script, result -> {
            value.set(result); latch.countDown();
        }));
        assertTrue("WebView did not respond", latch.await(6, TimeUnit.SECONDS));
        return new JSONTokener(value.get()).nextValue();
    }

    private void awaitNative(BooleanSupplier condition) {
        long deadline = SystemClock.elapsedRealtime() + 20000;
        while (SystemClock.elapsedRealtime() < deadline) {
            boolean[] ready = new boolean[1];
            instrumentation.runOnMainSync(() -> ready[0] = condition.getAsBoolean());
            if (ready[0]) return;
            SystemClock.sleep(100);
        }
        fail("Renderer recovery did not complete");
    }

    private TextView label(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = label(group.getChildAt(index), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    @android.annotation.TargetApi(29)
    private WebView terminateRenderer() {
        WebView[] previous = new WebView[1];
        instrumentation.runOnMainSync(() -> {
            previous[0] = activity.browserForTesting();
            WebViewRenderProcess process = previous[0].getWebViewRenderProcess();
            assertNotNull("This device must use an isolated WebView renderer", process);
            assertTrue("The real renderer must terminate", process.terminate());
        });
        awaitNative(() -> activity.browserForTesting() != previous[0]);
        instrumentation.runOnMainSync(() -> {
            assertFalse("The renderer crash must not finish the Activity", activity.isFinishing());
            assertNull("Recovery must wait for user retry", activity.browserForTesting().getUrl());
            assertTrue(label(activity.getWindow().getDecorView(), "重新加载").isShown());
        });
        return previous[0];
    }

    @Test public void rendererExitRetainsUrlSettingsCookiesAndRetryRebuildsScripts() throws Exception {
        WebChromeClient[] oldClient = new WebChromeClient[1];
        WebView[] oldView = new WebView[1];
        instrumentation.runOnMainSync(() -> {
            oldView[0] = activity.browserForTesting();
            oldClient[0] = oldView[0].getWebChromeClient();
        });
        instrumentation.runOnMainSync(() -> CookieManager.getInstance().setCookie(PAGE,
                "bilispeed_recovery_probe=retained; Path=/; Secure"));
        terminateRenderer();
        AtomicInteger staleCancelled = new AtomicInteger();
        instrumentation.runOnMainSync(() -> {
            oldClient[0].onShowCustomView(new FrameLayout(activity), staleCancelled::incrementAndGet);
            assertTrue(oldClient[0].onShowFileChooser(oldView[0], value -> {
                assertNull(value); staleCancelled.incrementAndGet();
            }, null));
            assertEquals("Callbacks from the dead page must be cancelled", 2, staleCancelled.get());
            assertFalse(activity.fullscreenForTesting());
        });
        Bundle saved = new Bundle();
        instrumentation.runOnMainSync(() -> {
            instrumentation.callActivityOnSaveInstanceState(activity, saved);
            assertEquals(PAGE, saved.getString("currentUrl"));
            assertEquals(3.5f, saved.getFloat("selectedRate"), 0.001f);
            assertTrue(CookieManager.getInstance().getCookie(PAGE).contains("bilispeed_recovery_probe=retained"));
            assertEquals(0.3f, activity.getSharedPreferences("playback", Context.MODE_PRIVATE).getFloat("normal_x", 0), 0.001f);
            assertEquals(0.7f, activity.getSharedPreferences("playback", Context.MODE_PRIVATE).getFloat("normal_y", 0), 0.001f);
        });
        Bitmap image = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File file = new File(instrumentation.getTargetContext().getExternalFilesDir(null), "BiliSpeed-browser-recovery.png");
        try (FileOutputStream output = new FileOutputStream(file)) { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output)); }
        finally { image.recycle(); }
        instrumentation.runOnMainSync(() -> label(activity.getWindow().getDecorView(), "重新加载").performClick());
        awaitNative(() -> PAGE.equals(activity.browserForTesting().getOriginalUrl()));
        fixture();
        assertEquals(3.5, ((Number) js("document.getElementById('v').playbackRate")).doubleValue(), 0.001);
        assertTrue((Boolean) js("document.documentElement.hasAttribute('data-bilispeed-touch')"));
        assertFalse((Boolean) js("document.documentElement.hasAttribute('data-bilispeed-fullscreen')"));
        assertEquals(1, ((Number) js("document.querySelectorAll('#bilispeed-touch-controls').length")).intValue());
        terminateRenderer();
        System.out.println("BILISPEED_RENDERER_RECOVERY=two actual renderer exits; URL, rate, position, cookies and scripts retained");
    }

    @Test public void rendererExitLeavesFullscreenAndCancelsFileChooserOnce() throws Exception {
        AtomicInteger fullscreenClosed = new AtomicInteger();
        AtomicInteger uploadCancelled = new AtomicInteger();
        Field field = MainActivity.class.getDeclaredField("uploadCallback");
        field.setAccessible(true);
        ValueCallback<Uri[]> upload = result -> { assertNull(result); uploadCancelled.incrementAndGet(); };
        js("document.getElementById('v').play();true");
        awaitNative(() -> (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
        instrumentation.runOnMainSync(() -> {
            try { field.set(activity, upload); } catch (IllegalAccessException error) { throw new AssertionError(error); }
            activity.browserForTesting().getWebChromeClient().onShowCustomView(new FrameLayout(activity), fullscreenClosed::incrementAndGet);
        });
        assertTrue(activity.fullscreenForTesting());
        terminateRenderer();
        instrumentation.runOnMainSync(() -> {
            assertFalse(activity.fullscreenForTesting());
            assertEquals(1, fullscreenClosed.get());
            assertEquals(1, uploadCancelled.get());
            assertTrue(label(activity.getWindow().getDecorView(), "首页").isShown());
            assertEquals(0, activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            try { assertNull(field.get(activity)); } catch (IllegalAccessException error) { throw new AssertionError(error); }
        });
        fixture();
        assertFalse((Boolean) js("document.documentElement.hasAttribute('data-bilispeed-fullscreen')"));
        assertEquals(3.5, ((Number) js("document.getElementById('v').playbackRate")).doubleValue(), 0.001);
    }

    @Test public void rendererExitCleansSharedPopupWhileBackgroundAndRestoresOnResume() throws Exception {
        Field field = MainActivity.class.getDeclaredField("popupWindows");
        field.setAccessible(true);
        instrumentation.runOnMainSync(() -> {
            WebView main = activity.browserForTesting();
            WebView.WebViewTransport transport = main.new WebViewTransport();
            Message result = Message.obtain(new Handler(Looper.getMainLooper()), () -> { });
            result.obj = transport;
            assertTrue(main.getWebChromeClient().onCreateWindow(main, false, true, result));
            transport.getWebView().loadDataWithBaseURL(PAGE, "<!doctype html><p>Popup fixture</p>", "text/html", "UTF-8", PAGE);
        });
        SystemClock.sleep(350);
        instrumentation.runOnMainSync(() -> instrumentation.callActivityOnPause(activity));
        try {
            terminateRenderer();
            awaitNative(() -> {
                try { return ((Set<?>) field.get(activity)).isEmpty(); }
                catch (IllegalAccessException error) { throw new AssertionError(error); }
            });
            Bundle saved = new Bundle();
            instrumentation.runOnMainSync(() -> instrumentation.callActivityOnSaveInstanceState(activity, saved));
            assertEquals(PAGE, saved.getString("currentUrl"));
        } finally {
            instrumentation.runOnMainSync(() -> instrumentation.callActivityOnResume(activity));
        }
        fixture();
        assertEquals(3.5, ((Number) js("document.getElementById('v').playbackRate")).doubleValue(), 0.001);
        assertTrue((Boolean) js("document.getElementById('v').paused"));
    }
}
