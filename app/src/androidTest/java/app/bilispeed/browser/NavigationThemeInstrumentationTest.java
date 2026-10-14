package app.bilispeed.browser;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
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
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Production callbacks and a single real WebView with stable HTTPS list documents. */
public class NavigationThemeInstrumentationTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final String suffix = "?navigationFixture=" + System.nanoTime();
    private final String home = MainActivity.HOME + suffix;
    private final String popular = NavigationState.ROOTS[1] + suffix;
    private final String video = MainActivity.HOME + "video/BV17x411w7KC" + suffix;
    private final AtomicInteger requests = new AtomicInteger();
    private MainActivity activity;
    private volatile CountDownLatch committed;
    private volatile String expected;
    private final Object loadedBridge = new Object() {
        @android.webkit.JavascriptInterface public void ready(String url) {
            if (url.equals(expected) && committed != null) committed.countDown();
        }
    };

    @Before public void start() throws Exception {
        Context context = instrumentation.getTargetContext();
        context.getSharedPreferences("updates",0).edit().putBoolean("automatic",false).commit();
        context.getSharedPreferences("playback",0).edit().putBoolean("touchLayout",true).putString("theme","light").commit();
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(context,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        expected=home;committed=new CountDownLatch(1);
        instrumentation.runOnMainSync(() -> {
            WebView browser=activity.browserForTesting(); browser.stopLoading();
            browser.addJavascriptInterface(loadedBridge,"NavigationFixtureLoaded");
            WebViewClient original=browser.getWebViewClient();
            browser.setWebViewClient(new WebViewClient() {
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) {
                    String url=request.getUrl().toString();
                    if (!url.endsWith(suffix)) return original.shouldInterceptRequest(view,request);
                    int id=requests.incrementAndGet();
                    String html="<!doctype html><meta name='viewport' content='width=device-width'><style>body{margin:0}"
                            + ".feed-card{height:140px}.feed-card:nth-child(n+5){margin-top:40px}.bili-video-card{height:140px}"
                            + "a{display:block}input{height:44px}</style><div id='app'><input id='edit'><dialog id='modal'><button>弹窗</button></dialog>"
                            + "<div id='inner' style='height:100px;overflow:auto'><div style='height:500px'>内部滚动</div></div>"
                            + "<div class='bili-feed4-layout'><div class='recommended-container_floor-aside'><div class='container'>";
                    for(int i=0;i<18;i++) html+="<a class='feed-card' href='"+video+"#item"+i+"'>视频 "+i+"</a>";
                    html+="</div></div></div><script>window.fixtureSerial="+id+";window.fixtureIdentity={};addEventListener('pageshow',()=>NavigationFixtureLoaded.ready(location.href));</script>";
                    return new WebResourceResponse("text/html","UTF-8",200,"OK",Collections.singletonMap("Cache-Control","no-store"),
                            new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                }
                @Override public void onPageStarted(WebView view,String url,Bitmap icon) { original.onPageStarted(view,url,icon); }
                @Override public void onPageFinished(WebView view,String url) { original.onPageFinished(view,url); }
                @Override public void doUpdateVisitedHistory(WebView view,String url,boolean reload) { original.doUpdateVisitedHistory(view,url,reload); }
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) { return original.shouldOverrideUrlLoading(view,request); }
            });
            browser.loadUrl(home);
        });
        assertTrue("Initial fixture document committed",committed.await(15,TimeUnit.SECONDS));
        await("location.href==="+JSONObject.quote(home)+" && !!window.__BiliBrowserState && !!window.__BiliTouch && document.readyState==='complete'");
    }
    @After public void finish() { if(activity!=null) instrumentation.runOnMainSync(activity::finish); instrumentation.waitForIdleSync(); }
    private Object js(String expression) throws Exception {
        CountDownLatch done=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();
        instrumentation.runOnMainSync(()->activity.browserForTesting().evaluateJavascript(expression,r->{value.set(r);done.countDown();}));
        assertTrue("Current document callback",done.await(5,TimeUnit.SECONDS)); return new JSONTokener(value.get()).nextValue();
    }
    private void await(String expression) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+15000;
        while(SystemClock.elapsedRealtime()<deadline) {
            if(Boolean.TRUE.equals(js("Boolean("+expression+")")))return;
            SystemClock.sleep(100);
        }
        fail("Timed out: "+expression);
    }
    private void scroll(int y) throws Exception {js("scrollTo(0,"+y+");window.__BiliBrowserState.position();true");SystemClock.sleep(200);}
    private void load(String url) throws Exception {
        expected=url;committed=new CountDownLatch(1);
        instrumentation.runOnMainSync(()->activity.browserForTesting().loadUrl(url));
        assertTrue("New fixture document committed",committed.await(15,TimeUnit.SECONDS));
        await("location.href==="+JSONObject.quote(url)+" && !!window.fixtureSerial && document.readyState==='complete'");
    }
    private void returnTo(String url,Runnable navigation) throws Exception {
        expected=url;committed=new CountDownLatch(1);
        instrumentation.runOnMainSync(navigation);
        assertTrue("History document committed",committed.await(15,TimeUnit.SECONDS));
    }
    @Test public void deepListRestoresEscapedAnchorAfterContentInsertion() throws Exception {
        js("var list=document.createElement('main');"
                + "for(var i=0;i<600;i++){var a=document.createElement('a');a.href='/video/BVfixture'+i;"
                + "a.textContent='Video '+i;a.style.cssText='display:block;height:70px';list.append(a);}"
                + "document.body.replaceChildren(list);scrollTo(0,35000);true");
        SystemClock.sleep(200);
        js("window._anchor=document.elementFromPoint(innerWidth/4,Math.min(120,innerHeight/4)).closest('a');"
                + "_anchor.setAttribute('href','/video/BVfixture?label=\"quoted\"&part=[2]');"
                + "window._savedPosition={y:scrollY,anchor:_anchor.getAttribute('href'),offset:_anchor.getBoundingClientRect().top};"
                + "var banner=document.createElement('div');banner.style.height='180px';document.querySelector('main').prepend(banner);"
                + "scrollTo(0,0);window.__BiliBrowserState.restore(_savedPosition);true");
        await("Math.abs(_anchor.getBoundingClientRect().top-_savedPosition.offset)<2 && Math.abs(scrollY-35180)<2");
    }

    @Test public void returningFromVideoRestoresStableListWithin32Pixels() throws Exception {
        scroll(620); load(video);
        returnTo(home,activity::onBackPressed);
        await("location.href==="+JSONObject.quote(home)+" && Math.abs(scrollY-620)<=32");
    }
    @Test public void switchingTabsUsesHistoryAndRestoresEachPosition() throws Exception {
        scroll(620); load(popular);scroll(410);
        returnTo(home,()->activity.selectTab(0));
        await("location.href==="+JSONObject.quote(home)+" && Math.abs(scrollY-620)<=32");
        returnTo(popular,()->activity.selectTab(1));
        await("location.href==="+JSONObject.quote(popular)+" && Math.abs(scrollY-410)<=32");
    }
    @Test public void repeatedTabScrollsToTopWithoutNewNavigation() throws Exception {
        scroll(620);js("window.oldIdentity=fixtureIdentity;true");int before=requests.get();
        instrumentation.runOnMainSync(()->activity.selectTab(0));await("scrollY===0");
        assertEquals(before,requests.get());assertEquals(true,js("fixtureIdentity===oldIdentity"));
    }
    @Test public void rapidTabChangesHonorTheFinalChoice() throws Exception {
        expected=home;committed=new CountDownLatch(1);
        instrumentation.runOnMainSync(()->{
            for(int i=0;i<10;i++){activity.selectTab(1);activity.selectTab(0);}
        });
        assertTrue("Final navigation committed",committed.await(15,TimeUnit.SECONDS));
        await("location.href==="+JSONObject.quote(home)+" && !!window.fixtureSerial");
        SystemClock.sleep(800);
        assertEquals(home,js("location.href"));
    }
    @Test public void cancelledTabNavigationDoesNotBlockTheNextTabSelection() throws Exception {
        load(popular);
        returnTo(video,()->{
            activity.selectTab(0);
            activity.browserForTesting().loadUrl(video);
        });
        await("location.href==="+JSONObject.quote(video)+" && document.readyState==='complete'");
        returnTo(home,()->activity.selectTab(0));
        await("location.href==="+JSONObject.quote(home)+" && document.readyState==='complete'");
    }
    @Test public void sameContentHas18PixelCardGapsAtFourWidths() throws Exception {
        for(int width:new int[]{320,360,392,430}) {
            instrumentation.runOnMainSync(()->{
                android.view.View parent=(android.view.View)activity.browserForTesting().getParent();
                android.view.ViewGroup.LayoutParams layout=parent.getLayoutParams();
                layout.width=Math.round(width*activity.getResources().getDisplayMetrics().density);parent.setLayoutParams(layout);
            });
            await("Math.abs(innerWidth-"+width+")<2");
            assertEquals(true,js("(()=>{const c=[...document.querySelectorAll('.feed-card')].map(e=>e.getBoundingClientRect());return Math.abs(c[2].top-c[0].bottom-18)<2 && Math.abs(c[4].top-c[2].bottom-18)<2 && Math.abs(c[0].top-c[1].top)<2 && document.documentElement.scrollWidth<=innerWidth+2})()"));
        }
    }
    @Test public void refreshGuardsEditingDialogsAndNestedScrollers() throws Exception {
        assertEquals(true,js("__BiliBrowserState.blocked(document.getElementById('inner'))"));
        assertEquals(false,js("__BiliBrowserState.blocked(document.querySelector('.feed-card'))"));
        js("document.getElementById('edit').focus();true");
        assertEquals(true,js("__BiliBrowserState.blocked(document.querySelector('.feed-card'))"));
        js("document.getElementById('edit').blur();document.getElementById('modal').showModal();true");
        assertEquals(true,js("__BiliBrowserState.blocked(document.querySelector('.feed-card'))"));
    }
    @Test public void pageHideCancelsPositionRestorationRetries() throws Exception {
        js("__BiliBrowserState.restore({y:620,anchor:'missing-until-lazy-load',offset:0});"
                + "dispatchEvent(new PageTransitionEvent('pagehide'));scrollTo({top:0,behavior:'instant'});true");
        SystemClock.sleep(450);
        assertEquals("A departed document must stop restoring its old position",0,((Number)js("scrollY")).intValue());
    }
    @Test public void themeChangesWithoutReplacingDocumentOrResettingScroll() throws Exception {
        scroll(620);js("window.oldIdentity=fixtureIdentity;true");
        instrumentation.runOnMainSync(()->activity.setThemeMode("dark"));
        await("document.documentElement.dataset.bilispeedTheme==='dark'");
        assertEquals(true,js("fixtureIdentity===oldIdentity && Math.abs(scrollY-620)<=2 && getComputedStyle(document.body).backgroundColor==='rgb(25, 26, 31)'"));
        instrumentation.runOnMainSync(()->activity.setThemeMode("light"));
        await("document.documentElement.dataset.bilispeedTheme==='light'");
        assertEquals(true,js("fixtureIdentity===oldIdentity && Math.abs(scrollY-620)<=2"));
        // Repeated appearance changes must still bootstrap a new document with
        // the latest configuration, without duplicate controls or stale state.
        instrumentation.runOnMainSync(()->{
            for(int i=0;i<40;i++)activity.setThemeMode(i%2==0?"light":"dark");
            activity.selectRate(2.5f);
        });
        load(popular);
        await("document.documentElement.dataset.bilispeedTheme==='dark' && window.__BiliSpeed?.snapshot().selected===2.5");
        assertEquals(1,((Number)js("document.querySelectorAll('#bilispeed-touch-style').length")).intValue());
        instrumentation.runOnMainSync(()->activity.setThemeMode("system"));
        assertEquals("system",activity.getSharedPreferences("playback",0).getString("theme",""));
    }
    @Test public void routeSnapshotsAreBoundedAndRejectUntrustedUrls() throws Exception {
        NavigationState state=new NavigationState();
        for(int i=0;i<100;i++)state.position(new JSONObject().put("url",MainActivity.HOME+"?i="+i).put("y",500));
        state.position(new JSONObject().put("url","https://evil.example/").put("y",600));
        assertNull(state.position("https://evil.example/"));assertNull(state.position(MainActivity.HOME+"?i=0"));
        Bundle saved=new Bundle();state.save(saved);assertTrue(saved.getString("routePositions").length()<100*1024);
        NavigationState restored=new NavigationState();restored.restore(saved);
        assertEquals(500,restored.position(MainActivity.HOME+"?i=99").getInt("y"));
        assertTrue(NavigationState.refreshable(popular));assertTrue(NavigationState.refreshable("https://t.bilibili.com/"));
        assertTrue(NavigationState.refreshable(MainActivity.MY_PAGE));assertFalse(NavigationState.refreshable(video));
    }
    @Test public void followingSystemUpdatesBothThemesThroughConfigurationCallback() throws Exception {
        android.content.res.Configuration original=new android.content.res.Configuration(activity.getResources().getConfiguration());
        try {
            instrumentation.runOnMainSync(()->activity.setThemeMode("system"));
            for(boolean dark:new boolean[]{true,false}) {
                instrumentation.runOnMainSync(()->{
                    android.content.res.Configuration config=new android.content.res.Configuration(original);
                    config.uiMode=(config.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                            | (dark?android.content.res.Configuration.UI_MODE_NIGHT_YES:android.content.res.Configuration.UI_MODE_NIGHT_NO);
                    activity.getResources().updateConfiguration(config,activity.getResources().getDisplayMetrics());
                    activity.onConfigurationChanged(config);
                });
                await("document.documentElement.dataset.bilispeedTheme==='"+(dark?"dark":"light")+"'");
                assertEquals(dark,new ThemeColors(activity).dark);
            }
        } finally {
            instrumentation.runOnMainSync(()->{
                activity.getResources().updateConfiguration(original,activity.getResources().getDisplayMetrics());
                activity.onConfigurationChanged(original);
            });
        }
    }
    @Test public void openNativeDialogChangesPaletteWithoutLosingInputOrListeners() {
        android.app.AlertDialog[] dialog={null};android.widget.EditText[] input={null};java.util.concurrent.atomic.AtomicInteger clicks=new java.util.concurrent.atomic.AtomicInteger();
        instrumentation.runOnMainSync(()->{
            input[0]=new android.widget.EditText(activity);input[0].setText("BV17x411w7KC");
            dialog[0]=activity.dialogBuilder().setTitle("保持打开的弹窗").setView(input[0]).setPositiveButton("完成",(d,w)->clicks.incrementAndGet()).create();
            dialog[0].show();activity.setThemeMode("dark");
            android.graphics.drawable.GradientDrawable background=(android.graphics.drawable.GradientDrawable)dialog[0].getWindow().getDecorView().getBackground();
            assertEquals(new ThemeColors(activity).surface,background.getColor().getDefaultColor());
            assertEquals(new ThemeColors(activity).ink,input[0].getCurrentTextColor());
            assertEquals("BV17x411w7KC",input[0].getText().toString());
            activity.setThemeMode("light");
            assertEquals(new ThemeColors(activity).ink,input[0].getCurrentTextColor());
            dialog[0].getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        });
        instrumentation.waitForIdleSync();
        assertEquals(1,clicks.get());
    }
}
