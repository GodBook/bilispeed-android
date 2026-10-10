package app.bilispeed.browser;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONTokener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class MyPageInstrumentationTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;
    private int fixtureId;

    @Before public void launch() {
        Context target = instrumentation.getTargetContext();
        target.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear().commit();
        target.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().putBoolean("automatic", false).commit();
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(target, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    @After public void finish() {
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }

    private Object js(String script) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> activity.browserForTesting().evaluateJavascript(script, value -> {
            result.set(value); latch.countDown();
        }));
        assertTrue("No response from WebView", latch.await(10, TimeUnit.SECONDS));
        return new JSONTokener(result.get()).nextValue();
    }

    private void await(String condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 15000;
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                if (Boolean.TRUE.equals(js("Boolean(" + condition + ")"))) return;
            } catch (AssertionError error) {
                if (!"No response from WebView".equals(error.getMessage())) throw error;
            }
            SystemClock.sleep(100);
        }
        fail("Condition timed out: " + condition);
    }

    private TextView label(View root, String value) {
        if (root instanceof TextView && value.contentEquals(((TextView) root).getText())) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = label(group.getChildAt(index), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void fixture(String fetch) throws Exception {
        String html;
        try (InputStream input = instrumentation.getTargetContext().getAssets().open("my-page.html")) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096]; int length;
            while ((length = input.read(chunk)) != -1) buffer.write(chunk, 0, length);
            html = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
        int identity = ++fixtureId;
        String page = html.replace("<head>", "<head><script>window.myFixtureId=" + identity + ";" + fetch + "</script>");
        instrumentation.runOnMainSync(() -> {
            activity.browserForTesting().stopLoading();
            activity.browserForTesting().loadDataWithBaseURL(MainActivity.MY_PAGE, page, "text/html", "UTF-8", null);
        });
        await("window.myFixtureId===" + identity + " && document.querySelector('.shortcuts') && document.getElementById('status').textContent!=='正在加载账号信息…'");
    }

    @Test public void myTabOpensPackagedPageDirectlyWithRealAssets() throws Exception {
        instrumentation.runOnMainSync(() -> {
            TextView my = label(activity.getWindow().getDecorView(), "我的");
            assertNotNull(my); my.performClick();
        });
        await("location.pathname==='/__bilispeed__/me' && document.querySelector('.my-page') && document.readyState==='complete'");
        assertTrue((Boolean) js("getComputedStyle(document.querySelector('.shortcuts')).display==='grid'"));
        js("window.nightBefore=document.getElementById('night').getAttribute('aria-pressed');window.nightStored=localStorage.getItem('bilispeed-profile-night');true");
        try {
            js("document.getElementById('night').click();true");
            assertTrue((Boolean) js("document.getElementById('night').getAttribute('aria-pressed')!==nightBefore"));
        } finally {
            js("if(document.getElementById('night').getAttribute('aria-pressed')!==nightBefore)document.getElementById('night').click();"
                    + "if(nightStored===null)localStorage.removeItem('bilispeed-profile-night');else localStorage.setItem('bilispeed-profile-night',nightStored);true");
        }
        js("document.getElementById('offline').click();true");
        assertTrue((Boolean) js("document.getElementById('offline-dialog').open && /暂不支持/.test(document.getElementById('offline-dialog').textContent)"));
        instrumentation.runOnMainSync(() -> assertTrue(label(activity.getWindow().getDecorView(), "我的").isSelected()));
    }

    @Test public void loggedInProfileMatchesReferenceAndUsesAccountDestinations() throws Exception {
        fixture("window.fetch=function(url,options){window.lastCredentials=options.credentials;return Promise.resolve({ok:true,json:function(){return Promise.resolve(url.endsWith('/nav/stat')"
                + "?{code:0,data:{dynamic_count:20,following:148,follower:1}}"
                + ":{code:0,data:{isLogin:true,mid:123,uname:'--小神仙',money:130,wallet:{bcoin_balance:0},level_info:{current_level:4},vipStatus:0,vipType:2}});}});};");
        assertEquals("--小神仙", js("document.getElementById('username').textContent"));
        assertEquals("0.0", js("document.getElementById('bcoin').textContent"));
        assertEquals("130", js("document.getElementById('coins').textContent"));
        assertEquals("20,148,1", js("['dynamics','following','followers'].map(function(id){return document.getElementById(id).textContent;}).join(',')"));
        assertEquals("include", js("window.lastCredentials"));
        assertEquals("https://space.bilibili.com/123/favlist", js("document.getElementById('favorites').href"));
        assertFalse((Boolean) js("!!document.querySelector('.vip-banner') || document.body.textContent.includes('大会员')"));
        assertTrue((Boolean) js("document.documentElement.scrollWidth<=innerWidth+1 && ['.profile','.stats','.shortcuts'].every(function(s){var r=document.querySelector(s).getBoundingClientRect();return r.left>=0&&r.right<=innerWidth+1;})"));
        CountDownLatch painted = new CountDownLatch(1);
        instrumentation.runOnMainSync(() -> activity.browserForTesting().postVisualStateCallback(1,
                new android.webkit.WebView.VisualStateCallback() {
                    @Override public void onComplete(long requestId) { painted.countDown(); }
                }));
        assertTrue("Profile frame did not render", painted.await(6, TimeUnit.SECONDS));
        instrumentation.waitForIdleSync(); SystemClock.sleep(500);
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File file = new File(instrumentation.getTargetContext().getExternalFilesDir(null), "BiliSpeed-my-reference.png");
        try (FileOutputStream output = new FileOutputStream(file)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); }
        finally { bitmap.recycle(); }
        System.out.println("BILISPEED_SCREENSHOT=" + file.getAbsolutePath());
    }

    @Test public void loginAndNetworkFailureNeverInventAccountValuesAndCanRetry() throws Exception {
        fixture("window.fetch=function(){return Promise.resolve({ok:true,json:function(){return Promise.resolve({code:-101,data:{isLogin:false}});}});};");
        assertEquals("登录 / 注册", js("document.getElementById('username').textContent"));
        assertEquals("—", js("document.getElementById('coins').textContent"));
        assertTrue((Boolean) js("document.getElementById('status').textContent.includes('登录后') && document.getElementById('retry').hidden"));
        fixture("window.fetch=function(){return Promise.reject(new Error('offline'));};");
        assertTrue((Boolean) js("!document.getElementById('retry').hidden && document.getElementById('coins').textContent==='—'"));
        js("window.fetch=function(){return Promise.resolve({ok:true,json:function(){return Promise.resolve({code:-101,data:{isLogin:false}});}});};document.getElementById('retry').click();true");
        await("document.getElementById('retry').hidden && document.getElementById('status').textContent.includes('登录后')");
    }

    @Test public void longNicknameIsTextAndExpiredSessionClearsPrivateDetails() throws Exception {
        fixture("window.fetch=function(url){return Promise.resolve({ok:true,json:function(){return Promise.resolve(url.endsWith('/nav/stat')?{code:0,data:{dynamic_count:0,following:0,follower:0}}"
                + ":{code:0,data:{isLogin:true,mid:42,uname:'<img onerror=alert(1)>这是一个很长很长的账号昵称',money:0,wallet:{bcoin_balance:0},level_info:{current_level:0}}});}});};");
        assertTrue((Boolean) js("document.getElementById('username').textContent.startsWith('<img') && !document.getElementById('username').querySelector('img') && document.documentElement.scrollWidth<=innerWidth+1"));
        js("window.fetch=function(){return Promise.resolve({ok:true,json:function(){return Promise.resolve({code:-101,data:{isLogin:false}});}});};document.getElementById('retry').click();true");
        await("document.getElementById('username').textContent==='登录 / 注册' && document.getElementById('coins').textContent==='—'");
        assertEquals("https://account.bilibili.com/account/home", js("document.getElementById('favorites').href"));
    }
}
