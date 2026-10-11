package app.bilispeed.browser;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONTokener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Real Android touch input with controlled HTTPS responses and production page callbacks. */
public class HomeRefreshInstrumentationTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final AtomicInteger requests = new AtomicInteger();
    private final String query = "?__bilispeed_refresh_test__=" + System.nanoTime();
    private MainActivity activity;
    private SwipeRefreshLayout refresh;
    private volatile CountDownLatch responseGate;
    private volatile boolean failNext;
    private final AtomicInteger httpErrors = new AtomicInteger();

    @Before public void start() throws Exception {
        Context context = instrumentation.getTargetContext();
        context.getSharedPreferences("updates", 0).edit().putBoolean("automatic", false).commit();
        context.getSharedPreferences("playback", 0).edit().putBoolean("touchLayout", true).commit();
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        CountDownLatch committed = new CountDownLatch(1);
        Object bridge = new Object() { @android.webkit.JavascriptInterface public void ready() { committed.countDown(); } };
        instrumentation.runOnMainSync(() -> {
            activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            WebView view = activity.browserForTesting();
            view.addJavascriptInterface(bridge, "HomeFixtureLoaded");
            refresh = (SwipeRefreshLayout) view.getParent();
            WebViewClient original = view.getWebViewClient();
            view.stopLoading();
            view.setWebViewClient(new WebViewClient() {
                @Override public WebResourceResponse shouldInterceptRequest(WebView owner, WebResourceRequest request) {
                    if (!request.getUrl().toString().endsWith(query)) return original.shouldInterceptRequest(owner, request);
                    int serial = requests.incrementAndGet();
                    CountDownLatch gate = responseGate;
                    if (gate != null) try { gate.await(10, TimeUnit.SECONDS); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                    boolean failed = failNext;
                    failNext = false;
                    String html = "<!doctype html><meta name='viewport' content='width=1100'>"
                            + "<style>body{margin:0;min-width:1100px}.bili-header__bar{height:56px}"
                            + ".container{display:grid;grid-template-columns:repeat(4,1fr)}"
                            + ".recommended-swipe{height:320px;grid-column:span 2;grid-row:span 2}"
                            + ".feed-card{height:140px;background:#eee}</style><div id='app'>"
                            + "<header class='bili-header'><div class='bili-header__bar'><input class='nav-search-input' placeholder='搜索视频'></div></header>"
                            + "<div class='bili-feed4-layout'><div class='recommended-container_floor-aside'><div class='container'>"
                            + "<div class='recommended-swipe'>大型轮播推荐</div><a class='feed-card'>视频一</a><a class='feed-card'>视频二</a>"
                            + "<a class='feed-card'>视频三</a><a class='feed-card'>视频四</a></div></div></div>"
                            + "<div style='height:2000px'>滚动区域</div></div><script>window.requestSerial=" + serial
                            + ";addEventListener('load',()=>window.HomeFixtureLoaded?.ready());</script>";
                    return new WebResourceResponse("text/html", "UTF-8", failed ? 503 : 200,
                            failed ? "Service Unavailable" : "OK", Collections.singletonMap("Cache-Control", "no-store"),
                            new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                }
                @Override public void onPageStarted(WebView owner, String url, android.graphics.Bitmap icon) {
                    System.out.println("HOME_REFRESH_STARTED=" + url);
                    original.onPageStarted(owner, url, icon);
                }
                @Override public void onPageFinished(WebView owner, String url) { original.onPageFinished(owner, url); }
                @Override public void doUpdateVisitedHistory(WebView owner, String url, boolean reload) {
                    original.doUpdateVisitedHistory(owner, url, reload);
                }
                @Override public void onReceivedHttpError(WebView owner, WebResourceRequest request, WebResourceResponse response) {
                    httpErrors.incrementAndGet();
                    System.out.println("HOME_REFRESH_HTTP_ERROR=" + response.getStatusCode() + " main=" + request.isForMainFrame());
                    original.onReceivedHttpError(owner, request, response);
                }
            });
            view.loadUrl(MainActivity.HOME + query);
        });
        try { assertTrue("Home fixture commit", committed.await(15, TimeUnit.SECONDS)); }
        finally { instrumentation.runOnMainSync(() -> activity.browserForTesting().removeJavascriptInterface("HomeFixtureLoaded")); }
        awaitJs("window.requestSerial === 1 && document.documentElement.dataset.bilispeedPage === 'home'");
        awaitNative(() -> refresh.isEnabled() && !refresh.isRefreshing() && refresh.getHeight() > 500);
    }

    @After public void finish() {
        if (responseGate != null) responseGate.countDown();
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }

    private Object js(String expression) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> activity.browserForTesting().evaluateJavascript(expression,
                value -> { result.set(value); done.countDown(); }));
        // Chromium can discard evaluations issued during document replacement.
        if (!done.await(2, TimeUnit.SECONDS)) return org.json.JSONObject.NULL;
        return new JSONTokener(result.get()).nextValue();
    }
    private void awaitJs(String condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        do { if (Boolean.TRUE.equals(js("Boolean(" + condition + ")"))) return; SystemClock.sleep(80); }
        while (SystemClock.elapsedRealtime() < deadline);
        fail(condition + " snapshot=" + js("JSON.stringify({url:location.href,serial:window.requestSerial,kind:document.documentElement.dataset.bilispeedPage,"
                + "touch:!!window.__BiliTouch,enabled:window.__BILI_TOUCH_ENABLED__,ready:document.readyState})") + " requests=" + requests.get());
    }
    private void awaitNative(BooleanSupplier condition) {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        AtomicReference<Boolean> ready = new AtomicReference<>();
        do {
            instrumentation.runOnMainSync(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            SystemClock.sleep(80);
        } while (SystemClock.elapsedRealtime() < deadline);
        AtomicReference<String> snapshot = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> snapshot.set("enabled=" + refresh.isEnabled() + " refreshing=" + refresh.isRefreshing()));
        fail("Native refresh state did not settle: " + snapshot.get() + " requests=" + requests.get() + " errors=" + httpErrors.get());
    }
    private void swipe(float x1, float y1, float x2, float y2, boolean cancel) {
        int[] origin = new int[2];
        instrumentation.runOnMainSync(() -> activity.browserForTesting().getLocationOnScreen(origin));
        float density = activity.getResources().getDisplayMetrics().density;
        long down = SystemClock.uptimeMillis();
        for (int step = 0; step <= 13; step++) {
            float fraction = Math.min(step, 12) / 12f;
            int action = step == 0 ? MotionEvent.ACTION_DOWN : step == 13
                    ? (cancel ? MotionEvent.ACTION_CANCEL : MotionEvent.ACTION_UP) : MotionEvent.ACTION_MOVE;
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                    origin[0] + (x1 + (x2 - x1) * fraction) * density,
                    origin[1] + (y1 + (y2 - y1) * fraction) * density, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            assertTrue(instrumentation.getUiAutomation().injectInputEvent(event, true));
            event.recycle();
            SystemClock.sleep(25);
        }
        instrumentation.waitForIdleSync();
    }
    private void pull() { swipe(180, 100, 180, 330, false); }
    private void assertNoReload() {
        SystemClock.sleep(600);
        assertEquals(1, requests.get());
        instrumentation.runOnMainSync(() -> assertFalse(refresh.isRefreshing()));
    }

    @Test public void pullReloadsOnceShowsProgressAndKeepsHomeUrl() throws Exception {
        responseGate = new CountDownLatch(1);
        pull();
        awaitNative(() -> requests.get() == 2 && refresh.isRefreshing());
        pull();
        assertEquals("Ignore another pull while loading", 2, requests.get());
        responseGate.countDown();
        awaitJs("window.requestSerial === 2");
        awaitNative(() -> !refresh.isRefreshing());
        instrumentation.runOnMainSync(() -> assertEquals(MainActivity.HOME + query, activity.browserForTesting().getUrl()));
    }
    @Test public void shortHorizontalAndCancelledGesturesDoNotReload() {
        swipe(180, 100, 180, 125, false);
        assertNoReload();
        swipe(30, 100, 330, 100, false);
        assertNoReload();
        swipe(180, 100, 180, 330, true);
        assertNoReload();
    }
    @Test public void normalScrollingDoesNotRefreshUntilBackAtTop() throws Exception {
        js("scrollTo(0,600);true");
        awaitNative(() -> activity.browserForTesting().canScrollVertically(-1));
        pull();
        assertNoReload();
        js("scrollTo(0,0);true");
        awaitNative(() -> !activity.browserForTesting().canScrollVertically(-1));
        pull();
        awaitJs("window.requestSerial === 2");
    }
    @Test public void settingsAndOtherPagesDisablePullRefresh() throws Exception {
        instrumentation.runOnMainSync(activity::showSettings);
        instrumentation.runOnMainSync(() -> assertFalse(refresh.isEnabled()));
        pull();
        assertNoReload();
        instrumentation.runOnMainSync(activity::closeSettings);
        awaitNative(refresh::isEnabled);
        instrumentation.runOnMainSync(() -> activity.browserForTesting().loadUrl(MainActivity.HOME + "video/__refresh_test__/" + query));
        awaitJs("window.requestSerial === 2 && location.pathname.includes('video')");
        instrumentation.runOnMainSync(() -> assertFalse(refresh.isEnabled()));
        pull();
        SystemClock.sleep(600);
        assertEquals(2, requests.get());
    }
    @Test public void failedRefreshStopsIndicatorAndHomeCanBeOpenedAgain() throws Exception {
        failNext = true;
        pull();
        awaitNative(() -> requests.get() == 2 && !refresh.isRefreshing() && !refresh.isEnabled());
        instrumentation.runOnMainSync(() -> activity.browserForTesting().loadUrl(MainActivity.HOME + query));
        awaitJs("window.requestSerial === 3");
        awaitNative(refresh::isEnabled);
        pull();
        awaitJs("window.requestSerial === 4");
        awaitNative(() -> !refresh.isRefreshing());
    }
    @Test public void homeStartsWithTwoVideoColumnsAndKeepsHeaderSearch() throws Exception {
        assertEquals(true, js("(()=>{const banner=document.querySelector('.recommended-swipe'),cards=document.querySelectorAll('.feed-card'),"
                + "a=cards[0].getBoundingClientRect(),b=cards[1].getBoundingClientRect(),header=document.querySelector('header').getBoundingClientRect();"
                + "return !banner.getBoundingClientRect().height && a.top-header.bottom<20 && Math.abs(a.top-b.top)<2 && b.left>a.left"
                + "&& document.querySelector('.nav-search-input').getBoundingClientRect().width>100 && document.documentElement.scrollWidth<=innerWidth+2;})()"));
        instrumentation.runOnMainSync(() -> activity.setTouchLayout(false));
        awaitJs("innerWidth >= 1000 && document.querySelector('.recommended-swipe').getBoundingClientRect().height > 0");
        instrumentation.runOnMainSync(() -> activity.setTouchLayout(true));
        awaitJs("innerWidth < 600 && document.querySelector('.recommended-swipe').getBoundingClientRect().height === 0");
    }
}
