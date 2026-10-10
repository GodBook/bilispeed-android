package app.bilispeed.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    static final String HOME = "https://www.bilibili.com/";
    static final String MY_PAGE = HOME + "__bilispeed__/me";
    private static final int PINK = Color.rgb(232, 85, 127);
    private static final int INK = Color.rgb(40, 40, 48);
    private static final int MUTED = Color.rgb(116, 116, 125);
    private static final int FILE_PICKER = 100;
    private static final int MAX_BROWSER_STATE_BYTES = 256 * 1024;
    private static final float[] PRESETS = {1, 1.25f, 1.5f, 2, 2.5f, 3, 3.5f, 4, 5};
    private static final String[] APPEARANCE_TARGETS = {"menu", "speed", "subtitle"};
    private static final Set<String> ORIGINS = new HashSet<>(Arrays.asList(
            "https://bilibili.com", "https://*.bilibili.com"));

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<JavaScriptReplyProxy, FrameState> frameStates = new LinkedHashMap<>();
    private final ArrayList<Button> presetButtons = new ArrayList<>();
    private final Set<WebView> popupWindows = new HashSet<>();
    private SharedPreferences preferences;
    private AppUpdater updater;
    private FrameLayout root;
    private WebView browser;
    private FrameLayout fullscreenHost;
    private LinearLayout floating;
    private LinearLayout navigation;
    private SettingsPanel settingsPanel;
    private final ArrayList<TextView> navigationItems = new ArrayList<>();
    private TextView speedButton;
    private TextView menuButton;
    private FrameLayout menuTouchTarget;
    private FrameLayout speedTouchTarget;
    private TextView statusText;
    private TextView chosenText;
    private EditText customInput;
    private SeekBar speedSlider;
    private ProgressBar progress;
    private LinearLayout errorPanel;
    private TextView errorText;
    private Dialog speedDialog;
    private Dialog appearanceDialog;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private boolean episodeFullscreen;
    private String fullscreenEpisodeUrl;
    private ValueCallback<Uri[]> uploadCallback;
    private ScriptHandler documentScript;
    private String documentState;
    private int pageGeneration;
    private String injection;
    private String touchInjection;
    private String lastPageUrl = HOME;
    private String recoveryUrl;
    private float selectedRate = 1;
    private boolean touchLayout;
    private boolean foreground;
    private boolean destroyed;
    private boolean failedNavigation;
    private boolean keepingScreenOn;
    private int previousOrientation;
    private long lastBlockedMessage;

    private static final class FrameState {
        JSONObject value;
        long received;
        boolean main;
    }

    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (destroyed || !foreground) return;
            // Poll as a fallback for WebViews without the scoped message bridge.
            if ((!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) || fullscreenView != null)
                    && isBiliHttps(browser.getUrl())) {
                WebView checkedBrowser = browser;
                checkedBrowser.evaluateJavascript("window.__BiliSpeed ? JSON.stringify(window.__BiliSpeed.snapshot()) : null", value -> {
                    if (destroyed || !foreground || checkedBrowser != browser) return;
                    try {
                        Object parsed = new org.json.JSONTokener(value).nextValue();
                        if (parsed instanceof String) {
                            FrameState state = new FrameState();
                            state.value = new JSONObject((String) parsed);
                            state.received = SystemClock.elapsedRealtime();
                            state.main = true;
                            frameStates.put(null, state);
                        }
                    } catch (Exception ignored) { }
                    updatePlaybackStatus();
                });
            }
            if (fullscreenView != null) configureFrames();
            updatePlaybackStatus();
            handler.postDelayed(this, 1500);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("playback", MODE_PRIVATE);
        selectedRate = preferences.getBoolean("remember", true)
                ? preferences.getFloat("rate", 1) : 1;
        if (savedInstanceState != null) selectedRate = savedInstanceState.getFloat("selectedRate", selectedRate);
        if (!validRate(selectedRate)) selectedRate = 1;
        // Old versions saved desktop=false. The site now always uses the desktop
        // player; the separate preference only controls its touch-friendly layout.
        touchLayout = preferences.getBoolean("touchLayout", true);
        injection = readAsset("speed-controller.js");
        touchInjection = readAsset("desktop-touch.js") + "\n" + readAsset("video-details.js") + "\n" + readAsset("player-controls.js");
        buildInterface();
        configureBrowser();
        updater = new AppUpdater(this);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        }
        Bundle browserState = savedInstanceState == null ? null : savedInstanceState.getBundle("browserState");
        if (browserState == null) browserState = savedInstanceState;
        boolean restored = browserState != null && browser.restoreState(browserState) != null;
        String shared = sharedUrl(getIntent());
        if (shared != null || !restored) {
            String previous = savedInstanceState == null ? null : savedInstanceState.getString("currentUrl");
            String start = shared != null ? shared : isHttps(previous) && previous.length() <= 8192 ? previous : HOME;
            browser.loadUrl(desktopUrl(start));
        } else {
            String previous = browser.getUrl();
            if (previous != null && !previous.equals(desktopUrl(previous))) browser.loadUrl(desktopUrl(previous));
        }
        updateFloatingLabel();
        if (savedInstanceState != null && savedInstanceState.getBoolean("settingsOpen")) {
            showSettings();
            int scroll = savedInstanceState.getInt("settingsScroll");
            settingsPanel.post(() -> settingsPanel.scrollTo(0, scroll));
        }
    }

    private String readAsset(String name) {
        try (InputStream input = getAssets().open(name)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Missing browser asset: " + name, exception);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void buildInterface() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                root.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                root.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            root.post(this::positionFloating);
            return insets;
        });
        browser = createBrowser();
        root.addView(browser, new FrameLayout.LayoutParams(-1, -1));
        fullscreenHost = new FrameLayout(this);
        fullscreenHost.setBackgroundColor(Color.BLACK);
        fullscreenHost.setVisibility(View.GONE);
        root.addView(fullscreenHost, new FrameLayout.LayoutParams(-1, -1));

        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(dp(28), dp(28), dp(28), dp(28));
        errorPanel.setBackgroundColor(Color.WHITE);
        TextView title = text("页面暂时打不开", 22, INK);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        errorPanel.addView(title);
        errorText = text("检查网络后重试", 14, MUTED);
        errorText.setGravity(Gravity.CENTER);
        errorText.setPadding(0, dp(16), 0, dp(22));
        errorPanel.addView(errorText);
        Button retry = button("重新加载", true);
        retry.setOnClickListener(view -> reloadPage());
        errorPanel.addView(retry, new LinearLayout.LayoutParams(dp(180), dp(48)));
        errorPanel.setVisibility(View.GONE);
        root.addView(errorPanel, new FrameLayout.LayoutParams(-1, -1));

        navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setBackgroundColor(Color.WHITE);
        navigation.setElevation(dp(3));
        String[] labels = {"首页", "热门", "搜索", "动态", "我的", "设置"};
        for (int index = 0; index < labels.length; index++) {
            final int item = index;
            TextView tab = text(labels[index], 11, MUTED);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            NavigationIcon icon = new NavigationIcon(index, MUTED);
            icon.setBounds(0, 0, dp(21), dp(21));
            tab.setCompoundDrawables(null, icon, null, null);
            tab.setCompoundDrawablePadding(dp(4));
            tab.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(24, 232, 85, 127)), null, null));
            tab.setClickable(true);
            tab.setFocusable(true);
            tab.setContentDescription("B站" + labels[index]);
            tab.setOnClickListener(view -> {
                hideInputKeyboard(browser);
                if (isFullscreenActive()) exitFullscreen();
                if (item == 5) { showSettings(); return; }
                closeSettings();
                switch (item) {
                    case 0: browser.loadUrl(HOME); break;
                    case 1: browser.loadUrl("https://www.bilibili.com/v/popular/all"); break;
                    case 2: showSearch(); break;
                    case 3: browser.loadUrl("https://t.bilibili.com/"); break;
                    case 4: browser.loadUrl(MY_PAGE); break;
                }
            });
            navigationItems.add(tab);
            navigation.addView(tab, new LinearLayout.LayoutParams(0, -1, 1));
        }
        root.addView(navigation, new FrameLayout.LayoutParams(-1, dp(56), Gravity.BOTTOM));
        updateBrowserLayout();

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(PINK));
        root.addView(progress, new FrameLayout.LayoutParams(-1, dp(2), Gravity.TOP));

        floating = new LinearLayout(this);
        floating.setGravity(Gravity.CENTER);
        floating.setElevation(dp(6));
        menuButton = text("···", 24, INK);
        menuButton.setGravity(Gravity.CENTER);
        menuButton.setBackground(surface(Color.WHITE, 24, Color.rgb(235, 231, 233)));
        menuButton.setOnClickListener(view -> showBrowserMenu());
        menuButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        menuTouchTarget = new FrameLayout(this);
        menuTouchTarget.setContentDescription("浏览器菜单，拖动可移动");
        menuTouchTarget.setFocusable(true);
        menuTouchTarget.setOnClickListener(view -> showBrowserMenu());
        menuTouchTarget.addView(menuButton);
        floating.addView(menuTouchTarget);
        speedButton = text("1x  倍速", 15, Color.WHITE);
        speedButton.setGravity(Gravity.CENTER);
        speedButton.setTypeface(null, Typeface.BOLD);
        speedButton.setBackground(surface(PINK, 24, PINK));
        speedButton.setOnClickListener(view -> openSpeedPanel());
        speedButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        speedTouchTarget = new FrameLayout(this);
        speedTouchTarget.setFocusable(true);
        speedTouchTarget.setOnClickListener(view -> openSpeedPanel());
        speedTouchTarget.addView(speedButton);
        LinearLayout.LayoutParams speedLayout = new LinearLayout.LayoutParams(-2, -2);
        speedLayout.leftMargin = dp(6);
        floating.addView(speedTouchTarget, speedLayout);
        root.addView(floating, new FrameLayout.LayoutParams(-2, -2));
        // The bottom Settings tab owns both functions now. Never expose the old
        // floating targets, including after IME, rotation or fullscreen changes.
        floating.setVisibility(View.GONE);
        applyFloatingAppearance();
        View.OnTouchListener drag = new View.OnTouchListener() {
            private float downX, downY, startX, startY;
            private boolean moved;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = event.getRawX(); downY = event.getRawY();
                    startX = floating.getTranslationX(); startY = floating.getTranslationY();
                    moved = false;
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                    if (Math.hypot(dx, dy) > dp(8)) moved = true;
                    if (moved) {
                        floating.setTranslationX(clamp(startX + dx, dp(8), maxFloatX()));
                        floating.setTranslationY(clamp(startY + dy, dp(8), maxFloatY()));
                    }
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (moved) saveFloatingPosition(); else view.performClick();
                    return true;
                }
                return event.getActionMasked() == MotionEvent.ACTION_CANCEL;
            }
        };
        menuTouchTarget.setOnTouchListener(drag);
        speedTouchTarget.setOnTouchListener(drag);
        menuButton.setOnTouchListener(drag);
        speedButton.setOnTouchListener(drag);
        floating.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) positionFloating();
        });
        root.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) positionFloating();
        });
        root.post(this::positionFloating);
        setSystemBarsFullscreen(false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureBrowser() {
        WebView owner = browser;
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        // B站 resolves video streams asynchronously after a tap. Allow its player to
        // start after that request completes, as it does in the mobile app.
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUseWideViewPort(!touchLayout);
        settings.setLoadWithOverviewMode(!touchLayout);
        settings.setSupportZoom(!touchLayout);
        settings.setBuiltInZoomControls(!touchLayout);
        settings.setDisplayZoomControls(false);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSafeBrowsingEnabled(true);
        applyUserAgent();
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(browser, "BiliSpeedBridge", ORIGINS,
                    (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
                if (destroyed || view != browser || !isBiliHttps(sourceOrigin.toString())) return;
                try {
                    String payload = message.getData();
                    if (payload == null || payload.length() > 2048) return;
                    JSONObject data = new JSONObject(payload);
                    if ("change-episode".equals(data.optString("type"))) {
                        if (foreground && isMainFrame && touchLayout && isFullscreenActive()) {
                            String url = data.optString("url");
                            if (isEpisodeUrl(url)) {
                                // A document's Fullscreen API ends on navigation. Keep
                                // Android immersive mode and the orientation instead.
                                episodeFullscreen = true;
                                fullscreenEpisodeUrl = url;
                                hideCustomFullscreen();
                                browser.loadUrl(url);
                            }
                        }
                        return;
                    }
                    if ("exit-fullscreen".equals(data.optString("type"))) {
                        if (foreground && isMainFrame) exitFullscreen();
                        return;
                    }
                    if ("select-rate".equals(data.optString("type"))) {
                        // The touch player's speed picker shares the native
                        // preference and controller, including in fullscreen.
                        if (foreground && isMainFrame) selectRate((float) data.optDouble("rate", Double.NaN));
                        return;
                    }
                    if (!"state".equals(data.optString("type"))) return;
                    if (!foreground) {
                        if (!data.optBoolean("suspended")) replyProxy.postMessage(configMessage());
                        return;
                    }
                    boolean fresh = !frameStates.containsKey(replyProxy);
                    FrameState state = new FrameState();
                    state.value = data;
                    state.main = isMainFrame;
                    state.received = SystemClock.elapsedRealtime();
                    frameStates.put(replyProxy, state);
                    if (fresh || Math.abs(data.optDouble("selected", 1) - selectedRate) > 0.001) {
                        replyProxy.postMessage(configMessage());
                    }
                    updatePlaybackStatus();
                } catch (Exception ignored) { }
            });
        }
        updateDocumentScript();
        browser.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equals(uri.getScheme()) || !"www.bilibili.com".equals(uri.getHost())
                        || uri.getPort() != -1 || !"GET".equals(request.getMethod())) return null;
                String path = uri.getPath();
                String asset, mime;
                if ("/__bilispeed__/me".equals(path) && request.isForMainFrame()) {
                    asset = "my-page.html"; mime = "text/html";
                } else if ("/__bilispeed__/my-page.css".equals(path)) {
                    asset = "my-page.css"; mime = "text/css";
                } else if ("/__bilispeed__/my-page.js".equals(path)) {
                    asset = "my-page.js"; mime = "application/javascript";
                } else return null;
                try {
                    Map<String, String> headers = new LinkedHashMap<>();
                    headers.put("Cache-Control", "no-store");
                    headers.put("X-Content-Type-Options", "nosniff");
                    headers.put("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self'; "
                            + "connect-src https://api.bilibili.com; img-src https://*.hdslb.com https://*.bilibili.com; "
                            + "base-uri 'none'; frame-ancestors 'none'");
                    return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, getAssets().open(asset));
                } catch (Exception ignored) { return null; }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (destroyed || view != browser) return true;
                // Frame requests must not replace the top-level page.
                if (!request.isForMainFrame()) {
                    String scheme = request.getUrl().getScheme();
                    return !("https".equals(scheme) || "about".equals(scheme) || "blob".equals(scheme));
                }
                return navigate(request.getUrl().toString(), request.hasGesture());
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                pageGeneration++;
                if (destroyed || view != browser) return;
                if (episodeFullscreen && !sameEpisode(url, fullscreenEpisodeUrl)) exitFullscreen();
                String mapped = desktopUrl(url);
                if (!mapped.equals(url)) { view.stopLoading(); view.loadUrl(mapped); return; }
                view.getSettings().setMediaPlaybackRequiresUserGesture(!isBiliHttps(url));
                rememberPageUrl(url);
                recoveryUrl = null;
                updateNavigation(url);
                failedNavigation = false;
                errorPanel.setVisibility(View.GONE);
                frameStates.clear();
                progress.setProgress(0);
                progress.setVisibility(View.VISIBLE);
                updatePlaybackStatus();
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (destroyed || view != browser) return;
                progress.setVisibility(View.GONE);
                notifyTouchFullscreen();
                injectIntoPage();
                CookieManager.getInstance().flush();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!destroyed && view == browser && request.isForMainFrame()) showPageError("检查网络连接，或稍后再试。\n" + error.getDescription());
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (!destroyed && view == browser && request.isForMainFrame() && response.getStatusCode() >= 400) {
                    showPageError("网站返回 " + response.getStatusCode() + "，请稍后重试。\n可通过菜单重新加载或打开其他视频链接。");
                }
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler sslHandler, SslError error) {
                sslHandler.cancel();
                if (!destroyed && view == browser) showPageError("网站的安全连接未能建立，请检查网络和系统时间。");
            }
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                if (destroyed || view != browser) return;
                rememberPageUrl(url);
                updateNavigation(url);
            }
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (!destroyed && view == browser) recoverBrowser(view);
                return true;
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                if (destroyed || view != browser) return;
                progress.setProgress(value);
                progress.setVisibility(value < 100 && !failedNavigation ? View.VISIBLE : View.GONE);
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (destroyed || owner != browser) { callback.onCustomViewHidden(); return; }
                enterFullscreen(view, callback);
            }
            @Override public void onHideCustomView() { if (!destroyed && owner == browser) hideCustomFullscreen(); }
            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                if (destroyed || view != browser || !isUserGesture) return false;
                // A short-lived WebView resolves target=_blank without allowing app popups.
                WebView popup = new WebView(MainActivity.this);
                popupWindows.add(popup);
                java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
                Runnable close = () -> {
                    if (closed.compareAndSet(false, true) && popupWindows.remove(popup)) popup.destroy();
                };
                popup.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView ignored, WebResourceRequest request) {
                        if (destroyed || owner != browser || closed.get()) return true;
                        String url = request.getUrl().toString();
                        if (!navigate(url, true) && "https".equals(request.getUrl().getScheme())) browser.loadUrl(desktopUrl(url));
                        handler.post(close);
                        return true;
                    }
                    @Override public boolean onRenderProcessGone(WebView ignored, RenderProcessGoneDetail detail) {
                        handler.removeCallbacks(close);
                        close.run();
                        return true;
                    }
                });
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(popup);
                resultMsg.sendToTarget();
                handler.postDelayed(close, 10000);
                return true;
            }
            @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (destroyed || webView != browser) { callback.onReceiveValue(null); return true; }
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = callback;
                try { startActivityForResult(params.createIntent(), FILE_PICKER); }
                catch (ActivityNotFoundException exception) {
                    uploadCallback.onReceiveValue(null);
                    uploadCallback = null;
                    toast("没有可用的文件选择器");
                }
                return true;
            }
        });
        browser.setDownloadListener((url, userAgent, disposition, mimeType, length) -> {
            if (destroyed || owner != browser) return;
            if (url.toLowerCase(Locale.ROOT).contains(".apk")) toast("请通过网页继续浏览");
            else openExternal(url);
        });
    }

    private WebView createBrowser() {
        WebView view = new WebView(this);
        view.setId(View.generateViewId());
        return view;
    }

    private void rememberPageUrl(String url) {
        if (url != null && url.length() <= 8192 && isHttps(url)) lastPageUrl = desktopUrl(url);
    }

    private void reloadPage() {
        if (recoveryUrl != null) browser.loadUrl(recoveryUrl);
        else browser.reload();
    }

    private void recoverBrowser(WebView failedBrowser) {
        // A dead renderer's WebView cannot be reused, even for reload/saveState.
        // Keep the Activity and app settings, and offer an explicit page retry.
        recoveryUrl = lastPageUrl;
        handler.removeCallbacks(heartbeat);
        frameStates.clear();
        setKeepingScreenOn(false);
        if (speedDialog != null) speedDialog.dismiss();
        if (appearanceDialog != null) appearanceDialog.dismiss();
        if (uploadCallback != null) {
            ValueCallback<Uri[]> pending = uploadCallback;
            uploadCallback = null;
            pending.onReceiveValue(null);
        }
        documentScript = null;
        browser = createBrowser();
        root.addView(browser, 0, new FrameLayout.LayoutParams(-1, -1));
        exitFullscreen();
        updateBrowserLayout();
        configureBrowser();
        root.removeView(failedBrowser);
        failedBrowser.destroy();
        showPageError("页面意外关闭，已保留当前链接和倍速设置。\n点击「重新加载」继续浏览。");
        updateNavigation(recoveryUrl);
        updatePlaybackStatus();
        if (foreground) { browser.onResume(); handler.post(heartbeat); }
        else browser.onPause();
    }

    private void updateDocumentScript() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
        String state = controllerInitialState();
        if (documentScript != null && state.equals(documentState)) return;
        if (documentScript != null) documentScript.remove();
        documentScript = WebViewCompat.addDocumentStartJavaScript(browser,
                state + touchInjection + "\n" + injection, ORIGINS);
        documentState = state;
    }

    private String controllerInitialState() {
        return "window.__BILI_SPEED_INITIAL__=" + selectedRate + ";window.__BILI_SPEED_SUSPENDED__="
                + !foreground + ";window.__BILI_TOUCH_ENABLED__=" + touchLayout
                + ";window.__BILI_TOUCH_FULLSCREEN__=" + isFullscreenActive()
                + ";window.__BILI_BUTTON_APPEARANCE__=" + subtitleAppearance() + ";\n";
    }

    private void injectIntoPage() {
        if (destroyed || !isBiliHttps(browser.getUrl())) return;
        WebView owner = browser;
        int generation = pageGeneration;
        // State changes should not send and parse all adaptation scripts again.
        // Retain the full bootstrap only for older WebViews or a new document
        // which has not received its document-start script yet.
        owner.evaluateJavascript("!!window.__BiliSpeed && window.__BiliSpeed.configure(" + configMessage() + ")", value -> {
            if (destroyed || owner != browser || generation != pageGeneration || "true".equals(value)
                    || !isBiliHttps(owner.getUrl())) return;
            owner.evaluateJavascript(controllerInitialState() + touchInjection + "\n" + injection
                    + "\nwindow.__BiliSpeed && window.__BiliSpeed.configure(" + configMessage() + ");", null);
        });
    }

    private String configMessage() {
        return "{\"type\":\"config\",\"rate\":" + selectedRate + ",\"suspended\":" + !foreground
                + ",\"buttons\":" + subtitleAppearance() + "}";
    }

    private void configureFrames() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return;
        for (JavaScriptReplyProxy proxy : new ArrayList<>(frameStates.keySet())) {
            if (proxy != null) { try { proxy.postMessage(configMessage()); } catch (Exception ignored) { } }
        }
    }

    static boolean validRate(float rate) { return Float.isFinite(rate) && rate >= 0.25f && rate <= 5; }

    void selectRate(float rate) {
        if (!validRate(rate)) return;
        float rounded = Math.round(rate * 100) / 100f;
        if (Math.abs(selectedRate - rounded) < .001f) {
            if (settingsPanel != null) settingsPanel.setRate(selectedRate);
            return;
        }
        selectedRate = rounded;
        if (preferences.getBoolean("remember", true)) preferences.edit().putFloat("rate", selectedRate).apply();
        updateDocumentScript();
        configureFrames();
        injectIntoPage();
        updateFloatingLabel();
        updatePanelSelection();
        if (settingsPanel != null) settingsPanel.setRate(selectedRate);
        updatePlaybackStatus();
    }

    private void updateFloatingLabel() {
        speedButton.setText(formatRate(selectedRate) + "  倍速");
        speedButton.setContentDescription("播放倍速 " + formatRate(selectedRate) + "，点击调节，拖动可移动");
        speedTouchTarget.setContentDescription(speedButton.getContentDescription());
    }

    private void updatePlaybackStatus() {
        if (browser == null) return;
        boolean playing = false, ready = false, hasVideo = false, live = false, applied = true;
        long now = SystemClock.elapsedRealtime();
        frameStates.entrySet().removeIf(entry -> now - entry.getValue().received > 10000);
        for (FrameState state : frameStates.values()) {
            if (now - state.received > 4500) continue;
            JSONObject data = state.value;
            boolean video = data.optInt("videos") > 0;
            hasVideo |= video;
            playing |= data.optBoolean("playing");
            ready |= video && data.optBoolean("ready");
            live |= video && data.optBoolean("live");
            if (video && data.optBoolean("ready") && !data.optBoolean("live")) {
                applied &= Math.abs(data.optDouble("rate", 1) - selectedRate) < 0.01;
            }
        }
        setKeepingScreenOn(playing && foreground);
        if (statusText != null || settingsShown()) {
            String status;
            if (live) status = "直播保持 1x；倍速将用于普通视频";
            else if (ready && applied) status = "已应用 " + formatRate(selectedRate) + " · " + (playing ? "正在播放" : "视频已就绪");
            else if (ready) status = "正在应用 " + formatRate(selectedRate) + "…";
            else if (hasVideo) status = "已选择 " + formatRate(selectedRate) + "，视频加载后生效";
            else status = "已选择 " + formatRate(selectedRate) + "，打开视频后自动生效";
            if (statusText != null && !status.contentEquals(statusText.getText())) statusText.setText(status);
            if (settingsShown()) {
                settingsPanel.setPlaybackStatus(status);
                settingsPanel.setUpdateLabel(updater.menuLabel());
            }
        }
    }

    private boolean settingsShown() { return settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE; }

    void showSettings() {
        if (isFullscreenActive()) exitFullscreen();
        if (settingsPanel == null) {
            settingsPanel = new SettingsPanel(this, new SettingsPanel.Listener() {
                @Override public void onRate(float rate) { selectRate(rate); }
                @Override public void onRemember(boolean enabled) {
                    SharedPreferences.Editor editor = preferences.edit().putBoolean("remember", enabled);
                    if (enabled) editor.putFloat("rate", selectedRate); else editor.remove("rate");
                    editor.apply();
                }
                @Override public void onTouchLayout(boolean enabled) { setTouchLayout(enabled); }
                @Override public void onAutomatic(boolean enabled) {
                    if (updater.automaticEnabled() != enabled) updater.toggleAutomatic();
                }
                @Override public void onAction(String action) { settingsAction(action); }
            });
            FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(-1, -1);
            layout.bottomMargin = dp(56);
            // Opaque native content covers the web page without changing its
            // history, scroll position or media element.
            root.addView(settingsPanel, root.indexOfChild(navigation), layout);
        }
        settingsPanel.setVisibility(View.VISIBLE);
        settingsPanel.sync(selectedRate, preferences.getBoolean("remember", true), touchLayout,
                updater.automaticEnabled(), updater.menuLabel());
        browser.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        errorPanel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        updateNavigation(browser.getUrl());
        updatePlaybackStatus();
    }

    void closeSettings() {
        if (!settingsShown()) return;
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(settingsPanel.getWindowToken(), 0);
        settingsPanel.clearFocus();
        settingsPanel.setVisibility(View.GONE);
        browser.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        errorPanel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        updateNavigation(browser.getUrl());
    }

    private void settingsAction(String action) {
        switch (action) {
            case "back": closeSettings(); break;
            case "appearance": showButtonAppearance(); break;
            case "open": showOpenLink(); break;
            case "copy":
                String url = recoveryUrl != null ? recoveryUrl : browser.getUrl();
                if (url != null) {
                    ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(
                            android.content.ClipData.newPlainText("视频链接", url));
                    toast("链接已复制");
                }
                break;
            case "reload": closeSettings(); reloadPage(); break;
            case "external": openExternal(recoveryUrl != null ? recoveryUrl : browser.getUrl()); break;
            case "update": updater.checkManually(); break;
            case "source": openExternal("https://github.com/" + BuildConfig.UPDATE_REPOSITORY); break;
            case "about":
                new AlertDialog.Builder(this).setTitle("B站倍速浏览器 " + BuildConfig.VERSION_NAME)
                        .setMessage("在底部「设置」或视频播放栏调节 0.25–5x 倍速，修改后即时生效并自动保存。全屏播放时可直接点播放栏的倍速按钮。\n\n"
                                + "手机触屏布局提供双击暂停、滑动进度、音量与字幕；关闭后可使用电脑原版和双指缩放。\n\n"
                                + "这是个人第三方浏览器，内容、登录和播放权限由 B 站官方网页提供；暂不支持离线缓存。\n\n"
                                + "更新来自本项目 GitHub Releases，下载和安装需手动确认。")
                        .setPositiveButton("知道了", null).show();
                break;
        }
    }

    private void setKeepingScreenOn(boolean enabled) {
        if (keepingScreenOn == enabled) return;
        keepingScreenOn = enabled;
        if (enabled) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    void openSpeedPanel() {
        if (speedDialog != null && speedDialog.isShowing()) return;
        speedDialog = new Dialog(this);
        speedDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setFocusableInTouchMode(true);
        panel.setPadding(dp(22), dp(14), dp(22), dp(18));
        panel.setBackground(surface(Color.WHITE, 24, Color.WHITE));
        View handle = new View(this);
        handle.setBackground(surface(Color.rgb(223, 223, 227), 3, Color.rgb(223, 223, 227)));
        LinearLayout.LayoutParams handleLayout = new LinearLayout.LayoutParams(dp(34), dp(4));
        handleLayout.gravity = Gravity.CENTER_HORIZONTAL;
        handleLayout.bottomMargin = dp(18);
        panel.addView(handle, handleLayout);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("播放倍速", 22, INK);
        title.setTypeface(null, Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        chosenText = text(formatRate(selectedRate), 26, PINK);
        chosenText.setTypeface(null, Typeface.BOLD);
        heading.addView(chosenText);
        panel.addView(heading);
        statusText = text("", 13, MUTED);
        statusText.setPadding(0, dp(8), 0, dp(18));
        statusText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        panel.addView(statusText);

        presetButtons.clear();
        for (int row = 0; row < 3; row++) {
            LinearLayout line = new LinearLayout(this);
            for (int column = 0; column < 3; column++) {
                float rate = PRESETS[row * 3 + column];
                Button item = button(formatRate(rate), false);
                item.setContentDescription("选择 " + formatRate(rate) + " 倍速");
                item.setTag(rate);
                item.setOnClickListener(view -> selectRate(rate));
                LinearLayout.LayoutParams itemLayout = new LinearLayout.LayoutParams(0, dp(48), 1);
                itemLayout.rightMargin = column < 2 ? dp(8) : 0;
                line.addView(item, itemLayout);
                presetButtons.add(item);
            }
            LinearLayout.LayoutParams lineLayout = new LinearLayout.LayoutParams(-1, -2);
            lineLayout.bottomMargin = dp(8);
            panel.addView(line, lineLayout);
        }

        TextView customLabel = text("自由调节 · 0.25–5x", 14, MUTED);
        customLabel.setPadding(0, dp(10), 0, dp(4));
        panel.addView(customLabel);
        speedSlider = new SeekBar(this);
        speedSlider.setMax(95);
        speedSlider.setContentDescription("自由调节倍速，0.25 到 5 倍");
        speedSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) {
                    float rate = 0.25f + value * 0.05f;
                    chosenText.setText(formatRate(rate));
                    customInput.setText(number(rate));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { selectRate(0.25f + bar.getProgress() * 0.05f); }
        });
        panel.addView(speedSlider, new LinearLayout.LayoutParams(-1, dp(44)));
        LinearLayout customRow = new LinearLayout(this);
        customRow.setGravity(Gravity.CENTER_VERTICAL);
        customInput = new EditText(this);
        customInput.setSingleLine(true);
        customInput.setTextSize(16);
        customInput.setTextColor(INK);
        customInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        customInput.setContentDescription("自定义倍速，输入 0.25 到 5");
        customInput.setPadding(dp(14), 0, dp(14), 0);
        customInput.setBackground(surface(Color.rgb(247, 247, 249), 12, Color.rgb(235, 235, 239)));
        customRow.addView(customInput, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button apply = button("应用", false);
        apply.setOnClickListener(view -> {
            try {
                float rate = Float.parseFloat(customInput.getText().toString().trim().replace(',', '.'));
                if (!validRate(rate)) throw new IllegalArgumentException();
                selectRate(rate);
                customInput.clearFocus();
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(customInput.getWindowToken(), 0);
            } catch (IllegalArgumentException exception) { customInput.setError("请输入 0.25 到 5 之间的数值"); }
        });
        LinearLayout.LayoutParams applyLayout = new LinearLayout.LayoutParams(dp(82), dp(46));
        applyLayout.leftMargin = dp(10);
        customRow.addView(apply, applyLayout);
        panel.addView(customRow);
        Switch remember = new Switch(this);
        remember.setText("记住上次倍速");
        remember.setTextSize(14);
        remember.setTextColor(INK);
        remember.setChecked(preferences.getBoolean("remember", true));
        remember.setPadding(0, dp(12), 0, dp(12));
        remember.setOnCheckedChangeListener((button, checked) -> {
            SharedPreferences.Editor editor = preferences.edit().putBoolean("remember", checked);
            if (checked) editor.putFloat("rate", selectedRate); else editor.remove("rate");
            editor.apply();
        });
        panel.addView(remember, new LinearLayout.LayoutParams(-1, dp(52)));
        Button done = button("完成", true);
        done.setOnClickListener(view -> speedDialog.dismiss());
        panel.addView(done, new LinearLayout.LayoutParams(-1, dp(48)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.addView(panel);
        speedDialog.setContentView(scroll);
        speedDialog.setOnDismissListener(dialog -> {
            statusText = null; chosenText = null; customInput = null; speedSlider = null;
            presetButtons.clear();
        });
        Window window = speedDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.25f);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            window.setGravity(Gravity.BOTTOM);
        }
        speedDialog.show();
        panel.requestFocus();
        if (window != null) {
            window.setLayout(-1, -2);
            int available = Math.max(dp(180), root.getHeight() - root.getPaddingTop() - root.getPaddingBottom() - dp(20));
            scroll.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST));
            window.setLayout(-1, Math.min(scroll.getMeasuredHeight(), available));
        }
        updatePanelSelection();
        updatePlaybackStatus();
    }

    private void updatePanelSelection() {
        if (chosenText == null) return;
        chosenText.setText(formatRate(selectedRate));
        customInput.setText(number(selectedRate));
        speedSlider.setProgress(Math.round((selectedRate - 0.25f) / 0.05f));
        for (Button button : presetButtons) {
            boolean active = Math.abs((Float) button.getTag() - selectedRate) < 0.001;
            button.setTextColor(active ? Color.WHITE : INK);
            button.setBackground(surface(active ? PINK : Color.rgb(247, 247, 249), 12,
                    active ? PINK : Color.rgb(237, 237, 241)));
            button.setSelected(active);
        }
    }

    private int appearanceValue(String target, String property) {
        int value = preferences.getInt(target + "_" + property, 100);
        return Math.max(property.equals("size") ? 70 : 20,
                Math.min(property.equals("size") ? 150 : 100, value));
    }

    private String subtitleAppearance() {
        return "{\"size\":" + appearanceValue("subtitle", "size")
                + ",\"opacity\":" + appearanceValue("subtitle", "opacity") + "}";
    }

    private void styleAppearancePreview(TextView view, String target) {
        float scale = appearanceValue(target, "size") / 100f;
        int width = target.equals("speed") ? 92 : 44;
        int textSize = target.equals("menu") ? 24 : target.equals("speed") ? 15 : 13;
        view.setTextSize(textSize * scale);
        view.setAlpha(appearanceValue(target, "opacity") / 100f);
        if (view.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) view.getBackground()).setCornerRadius(dp(24 * scale));
        }
        view.setLayoutParams(new FrameLayout.LayoutParams(dp(width * scale), dp(48 * scale), Gravity.CENTER));
    }

    private void applyFloatingAppearance() {
        if (floating == null) return;
        styleAppearancePreview(menuButton, "menu");
        styleAppearancePreview(speedButton, "speed");
        for (int index = 0; index < 2; index++) {
            FrameLayout target = index == 0 ? menuTouchTarget : speedTouchTarget;
            LinearLayout.LayoutParams layout = (LinearLayout.LayoutParams) target.getLayoutParams();
            float scale = appearanceValue(APPEARANCE_TARGETS[index], "size") / 100f;
            // Keep a comfortable touch target even when the visible button is small.
            layout.width = dp(Math.max(44, (index == 0 ? 44 : 92) * scale));
            layout.height = dp(Math.max(48, 48 * scale));
            target.setLayoutParams(layout);
        }
        floating.post(this::positionFloating);
    }

    private SeekBar addAppearanceSlider(LinearLayout panel, String title, String target,
                                        String property, Runnable preview) {
        boolean size = property.equals("size");
        int minimum = size ? 70 : 20;
        int maximum = size ? 150 : 100;
        TextView value = text("", 14, MUTED);
        panel.addView(value);
        SeekBar slider = new SeekBar(this);
        slider.setMax((maximum - minimum) / 5);
        slider.setProgress((appearanceValue(target, property) - minimum) / 5);
        slider.setContentDescription(title);
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(PINK));
        slider.setThumbTintList(android.content.res.ColorStateList.valueOf(PINK));
        panel.addView(slider, new LinearLayout.LayoutParams(-1, dp(44)));
        int valueFormat = size ? R.string.button_size_percent : R.string.button_opacity_percent;
        value.setText(getString(valueFormat, appearanceValue(target, property)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int percent = minimum + progress * 5;
                value.setText(getString(valueFormat, percent));
                preferences.edit().putInt(target + "_" + property, percent).apply();
                preview.run();
                applyFloatingAppearance();
                updateDocumentScript();
                configureFrames();
                if (!destroyed && isBiliHttps(browser.getUrl())) browser.evaluateJavascript(
                        "window.__BiliTouchPlayer && window.__BiliTouchPlayer.setAppearance(" + subtitleAppearance() + ");", null);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        return slider;
    }

    void showButtonAppearance() {
        if (appearanceDialog != null && appearanceDialog.isShowing()) return;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), dp(12));
        TextView hint = text("调节后即时生效并自动保存。字幕按钮的设置用于触屏布局。", 13, MUTED);
        panel.addView(hint);
        ArrayList<SeekBar> sliders = new ArrayList<>();
        String[] labels = {"三点按钮", "倍速按钮", "字幕按钮"};
        for (int index = 2; index < APPEARANCE_TARGETS.length; index++) {
            String target = APPEARANCE_TARGETS[index];
            LinearLayout heading = new LinearLayout(this);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = text(labels[index], 16, INK);
            label.setTypeface(null, Typeface.BOLD);
            heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            FrameLayout previewHost = new FrameLayout(this);
            previewHost.setBackground(surface(index == 2 ? INK : Color.rgb(246, 246, 248), 12,
                    index == 2 ? INK : Color.rgb(237, 237, 241)));
            TextView preview = text(index == 0 ? "···" : index == 1 ? formatRate(selectedRate) + "  倍速" : "字幕", 15,
                    index == 0 ? INK : Color.WHITE);
            preview.setGravity(Gravity.CENTER);
            if (index < 2) preview.setBackground(surface(index == 0 ? Color.WHITE : PINK, 24,
                    index == 0 ? Color.rgb(235, 231, 233) : PINK));
            previewHost.addView(preview);
            heading.addView(previewHost, new LinearLayout.LayoutParams(dp(148), dp(80)));
            panel.addView(heading);
            Runnable updatePreview = () -> styleAppearancePreview(preview, target);
            updatePreview.run();
            sliders.add(addAppearanceSlider(panel, labels[index] + "大小", target, "size", updatePreview));
            sliders.add(addAppearanceSlider(panel, labels[index] + "不透明度", target, "opacity", updatePreview));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("字幕按钮外观").setView(scroll)
                .setNeutralButton("恢复默认", null).setPositiveButton("完成", null).create();
        appearanceDialog = dialog;
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
            for (int index = 0; index < sliders.size(); index++) {
                // Changing these controls uses the same persistence and preview path.
                sliders.get(index).setProgress(index % 2 == 0 ? 6 : 16);
            }
        }));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int available = Math.max(dp(240), root.getHeight() - root.getPaddingTop() - root.getPaddingBottom() - dp(32));
            window.setLayout(Math.min(root.getWidth() - dp(24), dp(440)), Math.min(dp(390), available));
        }
    }

    private void showBrowserMenu() {
        String[] items = {"B站首页", "后退", "刷新", "打开链接", "复制当前链接", "在系统浏览器打开",
                touchLayout ? "切换电脑原版布局" : "切换触屏布局", "按钮外观", updater.menuLabel(),
                "启动时检查更新：" + (updater.automaticEnabled() ? "开" : "关"), "项目源码", "关于"};
        new AlertDialog.Builder(this).setTitle("浏览器").setItems(items, (dialog, which) -> {
            switch (which) {
                case 0: browser.loadUrl(HOME); break;
                case 1: goBack(); break;
                case 2: reloadPage(); break;
                case 3: showOpenLink(); break;
                case 4:
                    String url = recoveryUrl != null ? recoveryUrl : browser.getUrl();
                    if (url != null) {
                        ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(
                                android.content.ClipData.newPlainText("视频链接", url));
                        toast("链接已复制");
                    }
                    break;
                case 5: openExternal(recoveryUrl != null ? recoveryUrl : browser.getUrl()); break;
                case 6:
                    setTouchLayout(!touchLayout);
                    break;
                case 7: showButtonAppearance(); break;
                case 8: updater.checkManually(); break;
                case 9: updater.toggleAutomatic(); break;
                case 10: openExternal("https://github.com/" + BuildConfig.UPDATE_REPOSITORY); break;
                case 11: new AlertDialog.Builder(this).setTitle("B站倍速浏览器 " + BuildConfig.VERSION_NAME)
                        .setMessage("使用B站电脑端网页，默认按手机触屏排版；底部可进入首页、热门、搜索、动态和我的。\n\n"
                                + "点击粉色按钮调节倍速，拖动按钮可移动位置。\n\n"
                                + "支持 1.25x、1.5x、2x、2.5x、3x、3.5x、4x、5x，"
                                + "也可输入 0.25–5x 的自定义速度。\n\n"
                                + "这是个人第三方浏览器，使用B站官方网页。部分功能仅在官方App内提供。"
                                + "会员和付费内容仍需相应账号权限。\n\n"
                                + "菜单可检查更新；成功检查后 24 小时内不重复，安装需在系统中确认。")
                        .setPositiveButton("知道了", null).show(); break;
            }
        }).show();
    }

    private void showOpenLink() {
        EditText input = new EditText(this);
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("粘贴链接、分享文本或 BV 号");
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(20), dp(10), dp(20), 0);
        holder.addView(input, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("打开链接").setView(holder)
                .setNegativeButton("取消", (ignored, which) -> hideInputKeyboard(input)).setPositiveButton("打开", null).create();
        dialog.setOnDismissListener(ignored -> finishTextInput(input));
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = resolveUrlInput(input.getText().toString());
            if (value == null) { input.setError("请输入网页链接、分享文本或 BV 号"); return; }
            hideInputKeyboard(input);
            browser.loadUrl(desktopUrl(value));
            closeSettings();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private boolean navigate(String url, boolean gesture) {
        if (isHttps(url)) {
            String mapped = desktopUrl(url);
            if (!mapped.equals(url)) { browser.loadUrl(mapped); return true; }
            return false;
        }
        if (url.startsWith("http://")) {
            browser.loadUrl(desktopUrl("https://" + url.substring(7)));
            return true;
        }
        String fallback = deepLinkFallback(url);
        if (fallback != null) {
            // Auto app-open prompts should not interrupt a video already playing.
            if (gesture) browser.loadUrl(desktopUrl(fallback));
            return true;
        }
        if (gesture && SystemClock.elapsedRealtime() - lastBlockedMessage > 2500) {
            lastBlockedMessage = SystemClock.elapsedRealtime();
            toast("此操作需要官方App；可通过菜单打开视频网页链接");
        }
        return true;
    }

    static String deepLinkFallback(String url) {
        try {
            Uri uri = Uri.parse(url);
            if ("intent".equals(uri.getScheme())) {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                String fallback = intent.getStringExtra("browser_fallback_url");
                if (isBiliHttps(fallback)) return fallback;
                if (intent.getData() != null && "bilibili".equals(intent.getData().getScheme())) {
                    return deepLinkFallback(intent.getData().toString());
                }
                return null;
            }
            if (!"bilibili".equals(uri.getScheme())) return null;
            Matcher bv = Pattern.compile("BV[0-9A-Za-z]{10}").matcher(url);
            if (bv.find()) return HOME + "video/" + bv.group();
            Matcher av = Pattern.compile("(?:video/(?:av)?|aid=)([0-9]+)").matcher(url);
            if (av.find()) return HOME + "video/av" + av.group(1);
            Matcher bangumi = Pattern.compile("(?:bangumi/)?(?:season|play)/([0-9]+)").matcher(url);
            if (bangumi.find()) return HOME + "bangumi/play/ss" + bangumi.group(1);
        } catch (Exception ignored) { }
        return null;
    }

    private static boolean isEpisodeUrl(String url) {
        if (!isBiliHttps(url)) return false;
        Uri uri = Uri.parse(url);
        return ("www.bilibili.com".equals(uri.getHost()) || "bilibili.com".equals(uri.getHost()))
                && uri.getPath() != null && uri.getPath().matches("/video/(?:BV[0-9A-Za-z]{10}|av[0-9]+)/?");
    }

    private static boolean sameEpisode(String url, String expected) {
        if (!isEpisodeUrl(url) || !isEpisodeUrl(expected)) return false;
        Uri actual = Uri.parse(url), target = Uri.parse(expected);
        String actualPart = actual.getQueryParameter("p"), targetPart = target.getQueryParameter("p");
        if (actualPart == null) actualPart = "1";
        if (targetPart == null) targetPart = "1";
        return actual.getPath().replaceAll("/$", "").equals(target.getPath().replaceAll("/$", ""))
                && actualPart.equals(targetPart);
    }

    static String desktopUrl(String url) {
        if (!isHttps(url)) return url;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (!(host.equalsIgnoreCase("bilibili.com") || host.equalsIgnoreCase("www.bilibili.com")
                || host.equalsIgnoreCase("m.bilibili.com")) || (uri.getPort() != -1 && uri.getPort() != 443)) return url;
        String path = uri.getPath() == null ? "/" : uri.getPath();
        Uri.Builder mapped = uri.buildUpon().scheme("https");
        if (host.equalsIgnoreCase("m.bilibili.com")) {
            if (path.equals("/search") || path.startsWith("/search/")) {
                return mapped.authority("search.bilibili.com").path("/all").build().toString();
            }
            if (path.equals("/dynamic") || path.startsWith("/dynamic/")) {
                return mapped.authority("t.bilibili.com").path(path.substring("/dynamic".length())).build().toString();
            }
            if (path.matches("/space/[0-9]+/?")) {
                return mapped.authority("space.bilibili.com").path(path.substring("/space".length())).build().toString();
            }
        }
        return mapped.authority("www.bilibili.com").build().toString();
    }

    static boolean isBiliHttps(String url) {
        if (!isHttps(url)) return false;
        String host = Uri.parse(url).getHost().toLowerCase(Locale.ROOT);
        int port = Uri.parse(url).getPort();
        return (port == -1 || port == 443) && (host.equals("bilibili.com") || host.endsWith(".bilibili.com"));
    }

    static boolean isHttps(String url) {
        // Data/blob URLs can contain megabytes of media or page state. Reject
        // their scheme before asking URI to parse the entire string.
        if (url == null || !url.regionMatches(true, 0, "https://", 0, 8)) return false;
        try {
            java.net.URI uri = new java.net.URI(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && !uri.getHost().isEmpty() && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || (uri.getPort() > 0 && uri.getPort() <= 65535));
        } catch (java.net.URISyntaxException exception) { return false; }
    }

    private void applyUserAgent() {
        String ua = WebSettings.getDefaultUserAgent(this).replace("; wv", "").replace(" Version/4.0", "")
                .replaceFirst("\\([^)]*\\)", "(X11; Linux x86_64)").replace(" Mobile", "");
        browser.getSettings().setUserAgentString(ua);
    }

    void setTouchLayout(boolean enabled) {
        if (touchLayout == enabled) return;
        touchLayout = enabled;
        preferences.edit().putBoolean("touchLayout", enabled).apply();
        browser.getSettings().setUseWideViewPort(!enabled);
        browser.getSettings().setLoadWithOverviewMode(!enabled);
        browser.getSettings().setSupportZoom(!enabled);
        browser.getSettings().setBuiltInZoomControls(!enabled);
        browser.setInitialScale(0);
        updateBrowserLayout();
        updateDocumentScript();
        reloadPage();
    }

    private void updateBrowserLayout() {
        // Settings must remain reachable in the original desktop layout too.
        boolean visible = !isFullscreenActive();
        navigation.setVisibility(visible ? View.VISIBLE : View.GONE);
        int margin = visible ? dp(56) : 0;
        FrameLayout.LayoutParams content = (FrameLayout.LayoutParams) browser.getLayoutParams();
        content.bottomMargin = margin;
        browser.setLayoutParams(content);
        FrameLayout.LayoutParams error = (FrameLayout.LayoutParams) errorPanel.getLayoutParams();
        error.bottomMargin = margin;
        errorPanel.setLayoutParams(error);
        root.post(this::positionFloating);
    }

    private void updateNavigation(String url) {
        Uri uri = Uri.parse(url == null ? HOME : url);
        String host = uri.getHost(), path = uri.getPath();
        int selected = -1;
        if ("search.bilibili.com".equals(host)) selected = 2;
        else if ("t.bilibili.com".equals(host)) selected = 3;
        else if ("account.bilibili.com".equals(host) || "passport.bilibili.com".equals(host)
                || "space.bilibili.com".equals(host) || ("www.bilibili.com".equals(host) && path != null
                && (path.startsWith("/account/") || path.equals("/history") || path.startsWith("/history/") || path.startsWith("/watchlater")
                || path.equals("/__bilispeed__/me")))) selected = 4;
        else if ("www.bilibili.com".equals(host) && path != null) {
            if (path.startsWith("/v/popular")) selected = 1;
            else if (path.equals("/") || path.isEmpty()) selected = 0;
        }
        if (settingsShown()) selected = 5;
        for (int index = 0; index < navigationItems.size(); index++) {
            TextView tab = navigationItems.get(index);
            tab.setSelected(index == selected);
            tab.setTextColor(index == selected ? PINK : MUTED);
            tab.getCompoundDrawables()[1].setTint(index == selected ? PINK : MUTED);
        }
    }

    static String searchUrl(String keyword) {
        return Uri.parse("https://search.bilibili.com/all").buildUpon()
                .appendQueryParameter("keyword", keyword.trim()).build().toString();
    }

    private void hideInputKeyboard(View field) {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        android.os.IBinder token = field.getWindowToken();
        if (token == null) token = root.getWindowToken();
        if (keyboard != null && token != null) keyboard.hideSoftInputFromWindow(token, 0);
        field.clearFocus();
    }

    private void finishTextInput(View field) {
        if (destroyed) return;
        hideInputKeyboard(field);
        if (!settingsShown()) browser.requestFocus();
    }

    private void showSearch() {
        EditText input = new EditText(this);
        input.setSingleLine();
        input.setHint("搜索视频、UP主或番剧");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(20), dp(10), dp(20), 0);
        holder.addView(input, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("搜索B站").setView(holder)
                .setNegativeButton("取消", (ignored, which) -> hideInputKeyboard(input)).setPositiveButton("搜索", null).create();
        dialog.setOnDismissListener(ignored -> finishTextInput(input));
        Runnable search = () -> {
            String keyword = input.getText().toString().trim();
            if (keyword.isEmpty()) { input.setError("请输入搜索内容"); return; }
            hideInputKeyboard(input);
            browser.loadUrl(searchUrl(keyword));
            dialog.dismiss();
        };
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> search.run());
            input.requestFocus();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        input.setOnEditorActionListener((view, action, event) -> {
            boolean enter = event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER;
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    || enter && event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                search.run(); return true;
            }
            return enter;
        });
        dialog.show();
    }

    private void openExternal(String url) {
        if (!isHttps(url)) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch (ActivityNotFoundException exception) { toast("没有可用的系统浏览器"); }
    }

    private String sharedUrl(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return null;
        return resolveUrlInput(intent.getStringExtra(Intent.EXTRA_TEXT));
    }

    static String resolveUrlInput(String text) {
        if (text == null || text.length() > 16384) return null;
        String value = text.trim();
        if (value.isEmpty()) return null;
        Matcher link = Pattern.compile("https?://[^\\s<>\"，。！？【】]+", Pattern.CASE_INSENSITIVE).matcher(value);
        if (link.find()) {
            // A complete URL may legitimately end in a bracket or punctuation.
            if (link.start() == 0 && link.end() == value.length() && isHttps(value)) return value;
            String url = link.group();
            while (!url.isEmpty() && ")）]}，。,;；!！?？".indexOf(url.charAt(url.length() - 1)) >= 0) {
                url = url.substring(0, url.length() - 1);
            }
            if (url.regionMatches(true, 0, "http://", 0, 7)) url = "https://" + url.substring(7);
            return isHttps(url) ? url : null;
        }
        Matcher bv = Pattern.compile("(?<![0-9A-Za-z])BV[0-9A-Za-z]{10}(?![0-9A-Za-z])").matcher(value);
        if (bv.find()) return HOME + "video/" + bv.group();
        if (!value.contains(":") && isHttps("https://" + value)) return "https://" + value;
        return null;
    }

    private void showPageError(String message) {
        if (isFullscreenActive()) exitFullscreen();
        failedNavigation = true;
        errorText.setText(message);
        errorPanel.setVisibility(View.VISIBLE);
        progress.setVisibility(View.GONE);
    }

    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        closeSettings();
        if (fullscreenView != null) { callback.onCustomViewHidden(); return; }
        if (!isFullscreenActive()) previousOrientation = getRequestedOrientation();
        fullscreenView = view;
        fullscreenCallback = callback;
        root.setBackgroundColor(Color.BLACK);
        fullscreenHost.addView(view, new FrameLayout.LayoutParams(-1, -1));
        fullscreenHost.setVisibility(View.VISIBLE);
        browser.setVisibility(View.INVISIBLE);
        updateBrowserLayout();
        setSystemBarsFullscreen(true);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        notifyTouchFullscreen();
        root.post(this::positionFloating);
    }

    private boolean isFullscreenActive() { return fullscreenView != null || episodeFullscreen; }

    private void removeCustomFullscreenView() {
        if (fullscreenView != null) fullscreenHost.removeView(fullscreenView);
        fullscreenView = null;
        fullscreenHost.setVisibility(View.GONE);
        browser.setVisibility(View.VISIBLE);
        WebChromeClient.CustomViewCallback callback = fullscreenCallback;
        fullscreenCallback = null;
        if (callback != null) callback.onCustomViewHidden();
    }

    private void hideCustomFullscreen() {
        if (!episodeFullscreen) { exitFullscreen(); return; }
        removeCustomFullscreenView();
        updateBrowserLayout();
        notifyTouchFullscreen();
    }

    private void exitFullscreen() {
        if (!isFullscreenActive()) return;
        episodeFullscreen = false;
        fullscreenEpisodeUrl = null;
        removeCustomFullscreenView();
        root.setBackgroundColor(Color.WHITE);
        updateBrowserLayout();
        setSystemBarsFullscreen(false);
        setRequestedOrientation(previousOrientation);
        notifyTouchFullscreen();
        root.post(this::positionFloating);
    }

    private void notifyTouchFullscreen() {
        if (destroyed) return;
        // Keep future documents and the current one in sync with Android's
        // presentation, including the immersive WebView used between episodes.
        updateDocumentScript();
        if (!isBiliHttps(browser.getUrl())) return;
        browser.evaluateJavascript("window.__BILI_TOUCH_FULLSCREEN__=" + isFullscreenActive()
                + ";window.__BiliTouchPlayer && window.__BiliTouchPlayer.setFullscreen("
                + isFullscreenActive() + ");", null);
    }

    private void setSystemBarsFullscreen(boolean fullscreen) {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller == null) return;
            controller.setSystemBarsAppearance(fullscreen ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            if (fullscreen) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            } else controller.show(WindowInsets.Type.systemBars());
        } else {
            window.getDecorView().setSystemUiVisibility(fullscreen
                    ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private float maxFloatX() { return Math.max(dp(8), root.getWidth() - root.getPaddingLeft() - root.getPaddingRight()
            - floating.getWidth() - dp(8)); }
    private float maxFloatY() { return Math.max(dp(8), root.getHeight() - root.getPaddingTop() - root.getPaddingBottom()
            - floating.getHeight() - dp(8) - (navigation != null && navigation.getVisibility() == View.VISIBLE ? dp(56) : 0)); }
    private String positionPrefix() { return isFullscreenActive() ? "fullscreen_" : "normal_"; }
    private void positionFloating() {
        if (floating == null || root.getWidth() == 0) return;
        String prefix = positionPrefix();
        floating.setTranslationX(dp(8) + preferences.getFloat(prefix + "x", 1) * (maxFloatX() - dp(8)));
        floating.setTranslationY(dp(8) + preferences.getFloat(prefix + "y", isFullscreenActive() ? 0.14f : 0.84f) * (maxFloatY() - dp(8)));
    }
    private void saveFloatingPosition() {
        String prefix = positionPrefix();
        preferences.edit().putFloat(prefix + "x", (floating.getTranslationX() - dp(8)) / Math.max(1, maxFloatX() - dp(8)))
                .putFloat(prefix + "y", (floating.getTranslationY() - dp(8)) / Math.max(1, maxFloatY() - dp(8))).apply();
    }

    private void goBack() {
        if (speedDialog != null && speedDialog.isShowing()) speedDialog.dismiss();
        else if (appearanceDialog != null && appearanceDialog.isShowing()) appearanceDialog.dismiss();
        else if (settingsShown()) closeSettings();
        else if (isFullscreenActive()) exitFullscreen();
        else if (MY_PAGE.equals(browser.getUrl())) {
            WebView owner = browser;
            int generation = pageGeneration;
            owner.evaluateJavascript("(function(){var dialog=document.querySelector('dialog[open]');"
                    + "if(!dialog)return false;dialog.close();return true;})()", closed -> {
                if (destroyed || owner != browser || generation != pageGeneration) return;
                if (!"true".equals(closed)) goBackInHistory();
            });
        } else goBackInHistory();
    }
    private void goBackInHistory() {
        if (browser.canGoBack()) browser.goBack();
        else finish();
    }
    @Override public void onBackPressed() { goBack(); }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (speedDialog != null) speedDialog.dismiss();
        if (appearanceDialog != null) appearanceDialog.dismiss();
        root.post(this::positionFloating);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String shared = sharedUrl(intent);
        if (shared != null) browser.loadUrl(desktopUrl(shared));
    }
    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        if (browser != null) {
            updateDocumentScript();
            browser.onResume();
            injectIntoPage();
            configureFrames();
        }
        handler.removeCallbacks(heartbeat);
        handler.post(heartbeat);
        if (updater != null) updater.onResume();
    }
    @Override protected void onPause() {
        if (updater != null) updater.onPause();
        foreground = false;
        handler.removeCallbacks(heartbeat);
        if (browser != null) {
            updateDocumentScript();
            injectIntoPage();
            configureFrames();
            browser.onPause();
            CookieManager.getInstance().flush();
        }
        setKeepingScreenOn(false);
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        Bundle browserState = new Bundle();
        browser.saveState(browserState);
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(browserState);
            // Android shares a 1 MB Binder buffer across activity transactions.
            // Large URLs or histories must not crash the app when it stops.
            if (parcel.dataSize() <= MAX_BROWSER_STATE_BYTES) state.putBundle("browserState", browserState);
        } finally { parcel.recycle(); }
        String url = recoveryUrl != null ? recoveryUrl : browser.getUrl();
        if (url != null && url.length() <= 8192 && isHttps(url)) state.putString("currentUrl", url);
        state.putFloat("selectedRate", selectedRate);
        state.putBoolean("settingsOpen", settingsShown());
        if (settingsPanel != null) state.putInt("settingsScroll", settingsPanel.getScrollY());
        super.onSaveInstanceState(state);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == FILE_PICKER && uploadCallback != null) {
            uploadCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            uploadCallback = null;
        }
    }
    @Override protected void onDestroy() {
        destroyed = true;
        if (updater != null) updater.close();
        handler.removeCallbacksAndMessages(null);
        if (speedDialog != null) speedDialog.dismiss();
        if (appearanceDialog != null) appearanceDialog.dismiss();
        exitFullscreen();
        if (uploadCallback != null) uploadCallback.onReceiveValue(null);
        if (documentScript != null && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) documentScript.remove();
        for (WebView popup : popupWindows) popup.destroy();
        popupWindows.clear();
        frameStates.clear();
        root.removeView(browser);
        browser.stopLoading();
        browser.destroy();
        super.onDestroy();
    }

    WebView browserForTesting() { return browser; }
    Dialog speedDialogForTesting() { return speedDialog; }
    Dialog appearanceDialogForTesting() { return appearanceDialog; }
    View settingsForTesting() { return settingsPanel; }
    boolean fullscreenForTesting() { return isFullscreenActive(); }
    View fullscreenViewForTesting() { return fullscreenView != null ? fullscreenView : episodeFullscreen ? browser : null; }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        return view;
    }
    private Button button(String value, boolean primary) {
        Button view = new Button(this);
        view.setText(value); view.setTextSize(15); view.setAllCaps(false);
        view.setTextColor(primary ? Color.WHITE : INK);
        view.setTypeface(null, Typeface.BOLD);
        view.setMinHeight(0); view.setMinimumHeight(0); view.setMinWidth(0); view.setMinimumWidth(0);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setBackground(surface(primary ? PINK : Color.rgb(247, 247, 249), 12,
                primary ? PINK : Color.rgb(237, 237, 241)));
        return view;
    }
    private GradientDrawable surface(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(1), stroke);
        return drawable;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static float clamp(float value, float minimum, float maximum) { return Math.max(minimum, Math.min(value, maximum)); }
    static String number(float rate) { return new java.math.BigDecimal(Float.toString(Math.round(rate * 100) / 100f)).stripTrailingZeros().toPlainString(); }
    static String formatRate(float rate) { return number(rate) + "x"; }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
}
