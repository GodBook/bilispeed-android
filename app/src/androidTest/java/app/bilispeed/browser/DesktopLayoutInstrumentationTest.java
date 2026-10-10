package app.bilispeed.browser;

import android.app.Instrumentation;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.SeekBar;
import android.graphics.Bitmap;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONTokener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;

public class DesktopLayoutInstrumentationTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;
    private int fixtureId;

    @Before public void launch() {
        Context target = instrumentation.getTargetContext();
        target.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("desktop", false).putFloat("rate", 3.5f).commit();
        target.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().putBoolean("automatic", false).commit();
        activity = start();
    }

    private MainActivity start() {
        return (MainActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), MainActivity.class)
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
        assertTrue("No response from WebView", latch.await(6, TimeUnit.SECONDS));
        return new JSONTokener(result.get()).nextValue();
    }

    private void await(String condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (Boolean.TRUE.equals(js("Boolean(" + condition + ")"))) return;
            SystemClock.sleep(100);
        }
        fail("Condition timed out: " + condition);
    }

    private void fixture(boolean video) throws Exception {
        int identity = ++fixtureId;
        CountDownLatch committed = new CountDownLatch(1);
        Object bridge = new Object() { @android.webkit.JavascriptInterface public void ready() { committed.countDown(); } };
        // Reproduce the desktop site's minimum widths and actual container hierarchy.
        String content = video
                ? "<div id='mirror-vdcon'><div class='left-container'><div class='video-info-container'><h1>测试视频</h1></div>"
                    + "<div id='playerWrap'><div id='bilibili-player'><video id='v'></video></div></div>"
                    + "<div class='video-toolbar-container'><div class='video-toolbar-left'>"
                    + "<button id='like' onclick='window.likes=(window.likes||0)+1'>点赞</button></div></div>"
                    + "<div id='v_desc' class='video-desc-container'>简介</div><div id='commentapp'>评论</div></div>"
                    + "<div class='right-container'><div class='right-container-inner'><div class='video-pod-above-modules'>选集</div></div></div></div>"
                : "<div class='bili-feed4-layout'><div class='feed2'><div class='recommended-container_floor-aside'><div class='container is-version8'>"
                    + "<div class='feed-card'>视频一</div><div class='feed-card'>视频二</div>"
                    + "<div class='feed-card'>视频三</div><div class='feed-card'>视频四</div></div></div></div></div>";
        String html = "<!doctype html><meta name='viewport' content='width=1100'>"
                + "<style>body{min-width:1100px;margin:0}#app{min-width:1080px}.bili-feed4-layout{width:1100px;padding:60px}"
                + ".container{display:grid;grid-template-columns:repeat(4,1fr);gap:20px}.feed-card{height:100px}"
                + "#mirror-vdcon{display:flex;width:1100px}.left-container{width:750px}.right-container{width:350px}"
                + "#playerWrap{height:450px}#v{width:100%;height:100%}</style>"
                + "<script>window.fixture=" + identity + ";addEventListener('load',()=>LayoutFixtureLoaded.ready());</script><div id='app'>" + content + "</div>";
        String origin = MainActivity.HOME + (video ? "video/__bilispeed_layout__/" : "");
        instrumentation.runOnMainSync(() -> {
            activity.browserForTesting().addJavascriptInterface(bridge, "LayoutFixtureLoaded");
            activity.browserForTesting().stopLoading();
            activity.browserForTesting().loadDataWithBaseURL(origin, html, "text/html", "UTF-8", null);
        });
        // evaluateJavascript callbacks can be lost during document replacement.
        // Wait for this specific fixture to commit before querying its behavior.
        try { assertTrue("Layout fixture commit", committed.await(15, TimeUnit.SECONDS)); }
        finally { instrumentation.runOnMainSync(() -> activity.browserForTesting().removeJavascriptInterface("LayoutFixtureLoaded")); }
        await("window.fixture === " + identity + " && window.__BiliSpeed");
    }

    private TextView label(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView result = label(group.getChildAt(index), text);
                if (result != null) return result;
            }
        }
        return null;
    }

    private SeekBar slider(View root, String description) {
        if (root instanceof SeekBar && description.contentEquals(root.getContentDescription())) return (SeekBar) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                SeekBar result = slider(group.getChildAt(index), description);
                if (result != null) return result;
            }
        }
        return null;
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

    @Test public void mobileLinksAndSearchStayOnDesktopWithQueryAndFragment() {
        assertEquals(MainActivity.HOME, MainActivity.desktopUrl("https://m.bilibili.com/"));
        assertEquals("https://www.bilibili.com/video/BV17x411w7KC?p=2&t=15#reply", MainActivity.desktopUrl(
                "https://m.bilibili.com/video/BV17x411w7KC?p=2&t=15#reply"));
        assertEquals("https://www.bilibili.com/bangumi/play/ep123?p=2", MainActivity.desktopUrl("https://m.bilibili.com/bangumi/play/ep123?p=2"));
        assertEquals("https://search.bilibili.com/all?keyword=%E5%B0%8F%E7%8C%AB", MainActivity.desktopUrl(
                "https://m.bilibili.com/search?keyword=%E5%B0%8F%E7%8C%AB"));
        assertEquals("https://t.bilibili.com/123?foo=bar", MainActivity.desktopUrl("https://m.bilibili.com/dynamic/123?foo=bar"));
        assertEquals("https://space.bilibili.com/123", MainActivity.desktopUrl("https://m.bilibili.com/space/123"));
        assertEquals("https://www.bilibili.com/video/av170001", MainActivity.desktopUrl("https://bilibili.com/video/av170001"));
        assertEquals("https://b23.tv/abc", MainActivity.desktopUrl("https://b23.tv/abc"));
        assertEquals("https://example.org/video/?p=2", MainActivity.desktopUrl("https://example.org/video/?p=2"));
        assertEquals("https://m.bilibili.com.attacker.test/", MainActivity.desktopUrl("https://m.bilibili.com.attacker.test/"));
        assertEquals("https://m.bilibili.com:8443/", MainActivity.desktopUrl("https://m.bilibili.com:8443/"));
        assertNull(MainActivity.desktopUrl(null));
        assertEquals("A&B 小猫", android.net.Uri.parse(MainActivity.searchUrl(" A&B 小猫 ")).getQueryParameter("keyword"));
    }

    @Test public void oldMobilePreferenceUsesDesktopUaAndTwoTouchableColumns() throws Exception {
        fixture(false);
        await("document.documentElement.hasAttribute('data-bilispeed-touch')");
        assertFalse((Boolean) js("/Android|Mobile|; wv/.test(navigator.userAgent)"));
        assertEquals(3.5, ((Number) js("window.__BiliSpeed.snapshot().selected")).doubleValue(), 0.001);
        float[] cssWidth = new float[1];
        instrumentation.runOnMainSync(() -> cssWidth[0] = activity.browserForTesting().getWidth()
                / activity.getResources().getDisplayMetrics().density);
        assertEquals(cssWidth[0], ((Number) js("innerWidth")).doubleValue(), 2);
        assertTrue((Boolean) js("document.documentElement.scrollWidth <= innerWidth+2"));
        assertTrue((Boolean) js("(function(){var c=document.querySelectorAll('.feed-card');"
                + "var a=c[0].getBoundingClientRect(),b=c[1].getBoundingClientRect(),d=c[2].getBoundingClientRect();"
                + "return Math.abs(a.top-b.top)<2 && b.left>a.right && d.top>a.bottom && a.width>140;})()"));
        instrumentation.runOnMainSync(() -> {
            for (String tab : new String[]{"首页", "热门", "搜索", "动态", "我的"}) {
                TextView item = label(activity.getWindow().getDecorView(), tab);
                assertNotNull(item);
                assertTrue(item.isShown() && item.isClickable() && item.getHeight() >= 48
                        * activity.getResources().getDisplayMetrics().density);
            }
        });
    }

    @Test public void videoStartsWithFullWidthPlayerAndOfficialButtonsStillWork() throws Exception {
        fixture(true);
        await("document.getElementById('bilispeed-video-tabs')");
        assertTrue((Boolean) js("(function(){var p=document.getElementById('playerWrap').getBoundingClientRect(),"
                + "t=document.querySelector('h1').getBoundingClientRect(),l=document.querySelector('.left-container').getBoundingClientRect(),"
                + "r=document.querySelector('.right-container').getBoundingClientRect();"
                + "return p.width>=innerWidth-2 && p.top<t.top && r.top>=l.bottom;})()"));
        js("document.getElementById('like').click(); true");
        assertEquals(1, ((Number) js("window.likes")).intValue());
        assertEquals("简介评论选集", js("document.getElementById('bilispeed-video-tabs').textContent"));
    }

    @Test public void originalLayoutChoiceSurvivesRestartAndKeepsDesktopUa() throws Exception {
        instrumentation.runOnMainSync(() -> { activity.setTouchLayout(false); activity.finish(); });
        instrumentation.waitForIdleSync();
        activity = start();
        fixture(false);
        assertFalse((Boolean) js("document.documentElement.hasAttribute('data-bilispeed-touch')"));
        assertFalse((Boolean) js("/Android|Mobile/.test(navigator.userAgent)"));
        assertTrue((Boolean) js("innerWidth>=1000"));
        instrumentation.runOnMainSync(() -> assertTrue("Settings navigation must remain reachable in desktop layout",
                label(activity.getWindow().getDecorView(), "设置").isShown()));
        instrumentation.runOnMainSync(() -> activity.setTouchLayout(true));
        fixture(false);
        await("document.documentElement.hasAttribute('data-bilispeed-touch')");
        assertTrue((Boolean) js("innerWidth<600"));
    }

    @Test public void videoSectionButtonsTrackLateContentAndToolbarReplacement() throws Exception {
        fixture(true);
        await("document.getElementById('bilispeed-video-tabs')");
        js("document.querySelector('.video-pod-above-modules').remove();document.getElementById('commentapp').remove();true");
        await("document.querySelector('[data-tab=选集]').disabled && document.querySelector('[data-tab=评论]').disabled");
        js("var comments=document.createElement('div');comments.id='commentapp';document.body.append(comments);"
                + "var collection=document.createElement('div');collection.className='video-pod-above-modules';document.body.append(collection);"
                + "window.sectionViewed='';collection.scrollIntoView=function(){window.sectionViewed='collection';};"
                + "var old=document.querySelector('.video-toolbar-container');var next=old.cloneNode(true);old.replaceWith(next);true");
        await("!document.querySelector('[data-tab=选集]').disabled && !document.querySelector('[data-tab=评论]').disabled && "
                + "document.getElementById('bilispeed-video-tabs').previousElementSibling===document.querySelector('.video-toolbar-container')");
        js("document.querySelector('[data-tab=选集]').click();true");
        assertEquals("collection", js("window.sectionViewed"));
        assertEquals(1, ((Number) js("document.querySelectorAll('#bilispeed-video-tabs').length")).intValue());
    }

    @Test public void buttonAppearancePreviewsPersistsAndRestoresDefaults() throws Exception {
        fixture(true);
        await("document.querySelector('[data-bilispeed-control=subtitles]')");
        instrumentation.runOnMainSync(activity::showButtonAppearance);
        instrumentation.runOnMainSync(() -> {
            View panel = activity.appearanceDialogForTesting().getWindow().getDecorView();
            for (String title : new String[]{"字幕按钮"}) {
                SeekBar size = slider(panel, title + "大小");
                SeekBar opacity = slider(panel, title + "不透明度");
                assertNotNull(size); assertNotNull(opacity);
                size.setProgress(16); // 150%
                opacity.setProgress(6); // 50% opacity
            }
            System.out.println("BILISPEED_APPEARANCE_URL=" + activity.browserForTesting().getUrl());
        });
        System.out.println("BILISPEED_APPEARANCE_STATE=" + js("JSON.stringify({initial:window.__BILI_BUTTON_APPEARANCE__,"
                + "size:document.documentElement.style.getPropertyValue('--bilispeed-subtitle-size'),"
                + "opacity:document.documentElement.style.getPropertyValue('--bilispeed-subtitle-opacity'),"
                + "computed:getComputedStyle(document.querySelector('[data-bilispeed-control=subtitles]')).opacity})"));
        screenshot("BiliSpeed-button-appearance");
        await("getComputedStyle(document.querySelector('[data-bilispeed-control=subtitles]')).opacity==='0.5'");
        assertEquals(19.5, ((Number) js("parseFloat(getComputedStyle(document.querySelector('[data-bilispeed-control=subtitles]')).fontSize)")).doubleValue(), .1);
        instrumentation.runOnMainSync(() -> { activity.appearanceDialogForTesting().dismiss(); activity.finish(); });
        instrumentation.waitForIdleSync();
        activity = start();
        fixture(true);
        await("document.querySelector('[data-bilispeed-control=subtitles]')");
        assertEquals("0.5", js("getComputedStyle(document.querySelector('[data-bilispeed-control=subtitles]')).opacity"));
        assertTrue((Boolean) js("document.querySelector('[data-bilispeed-control=subtitles]').getBoundingClientRect().right<=innerWidth"));
        instrumentation.runOnMainSync(() -> {
            TextView menu = label(activity.getWindow().getDecorView(), "···");
            TextView speed = label(activity.getWindow().getDecorView(), "3.5x  倍速");
            assertNotNull(menu); assertNotNull(speed);
            assertFalse("Legacy menu must remain hidden", menu.isShown());
            assertFalse("Legacy speed button must remain hidden", speed.isShown());
            TextView settings = label(activity.getWindow().getDecorView(), "设置");
            assertNotNull(settings); assertTrue(settings.isShown());
        });
        instrumentation.runOnMainSync(activity::showButtonAppearance);
        instrumentation.runOnMainSync(() -> ((AlertDialog) activity.appearanceDialogForTesting())
                .getButton(AlertDialog.BUTTON_NEUTRAL).performClick());
        await("getComputedStyle(document.querySelector('[data-bilispeed-control=subtitles]')).opacity==='1'");
        instrumentation.runOnMainSync(() -> {
            for (String target : new String[]{"subtitle"}) {
                assertEquals(100, instrumentation.getTargetContext().getSharedPreferences("playback", Context.MODE_PRIVATE).getInt(target + "_size", 0));
                assertEquals(100, instrumentation.getTargetContext().getSharedPreferences("playback", Context.MODE_PRIVATE).getInt(target + "_opacity", 0));
            }
            activity.appearanceDialogForTesting().dismiss();
        });
    }

    @Test public void accountSidebarBecomesScrollableTabsAndProfileFitsPhone() throws Exception {
        String html = "<!doctype html><meta name='viewport' content='width=1100'>"
                + "<style>body{min-width:1100px;margin:0}.security_content{width:980px;display:flex;margin:10px auto 100px;overflow:hidden}"
                + ".top-img{width:980px;height:106px;background:#00a1d7}.security-left{width:150px;height:100%;overflow:hidden}"
                + ".security-list{width:150px;height:48px;line-height:48px}.security-right{flex:1;min-height:890px}"
                + ".secuity-right-home{width:789px;padding:50px 20px 0}.home-right{width:684px;display:inline-block;margin-left:16px}"
                + ".home-head{width:64px;height:64px;display:inline-block;background:#eee}.home-top-level-all{width:684px}"
                + ".home-top-level-up{width:280px}.home-dialy-exp-item{width:186px;display:inline-block}"
                + ".el-form-item__label{float:left;width:95px}.el-form-item__content{margin-left:95px}.el-input{width:225px}"
                + "a{color:#222}ul{padding:0;margin:0;list-style:none}input{box-sizing:border-box;width:100%;height:44px}"
                + "</style><div id='account-app'><div class='top-img'></div><div class='security_content'>"
                + "<div class='security-left'><span class='security-title'>个人中心</span><ul>"
                + "<li class='security-list'><a href='#home'>首页</a></li><li class='security-list'><a href='#vip'>大会员</a></li>"
                + "<li class='security-list'><a href='#info' id='profile-tab' onclick='window.profileViewed=true'>我的信息</a></li>"
                + "<li class='security-list'><a href='#face'>我的头像</a></li><li class='security-list'><a href='#fans'>粉丝勋章</a></li>"
                + "<li class='security-list'><a href='#safe'>账号安全</a></li></ul></div>"
                + "<div class='security-right'><div class='secuity-right-home'><div class='index-info'><div class='home-head'></div>"
                + "<div class='home-right'><h2 class='home-top-msg-name'>bili_32004312345678901234567890</h2>"
                + "<div class='home-top-level-all'>LV0<div class='home-top-level-up'>经验进度</div></div></div></div>"
                + "<div class='home-daily-task-warp'><h3>每日奖励</h3><div class='home-dialy-exp-item'>每日登录</div>"
                + "<div class='home-dialy-exp-item'>观看视频</div></div><form class='user-setting-warp'><div class='el-form-item'>"
                + "<label class='el-form-item__label'>昵称</label><div class='el-form-item__content'><div class='el-input'>"
                + "<input id='nickname' value='测试账号'></div></div></div></form></div></div></div></div>";
        instrumentation.runOnMainSync(() -> {
            activity.browserForTesting().stopLoading();
            activity.browserForTesting().loadDataWithBaseURL("https://account.bilibili.com/account/home", html, "text/html", "UTF-8", null);
        });
        await("document.documentElement.dataset.bilispeedPage==='account' && document.querySelector('.security-left')");
        assertTrue((Boolean) js("['.security_content','.security-right','.home-right','.home-top-msg-name','#nickname'].every(function(s){"
                + "var r=document.querySelector(s).getBoundingClientRect();return r.width>50&&r.left>=0&&r.right<=innerWidth+1;})"));
        assertTrue((Boolean) js("document.querySelector('.security-left').scrollWidth>document.querySelector('.security-left').clientWidth"));
        assertTrue((Boolean) js("document.querySelector('.security-right').getBoundingClientRect().top>=document.querySelector('.security-left').getBoundingClientRect().bottom"));
        js("document.getElementById('profile-tab').click();document.getElementById('nickname').value='新昵称';true");
        assertTrue((Boolean) js("window.profileViewed && document.getElementById('nickname').value==='新昵称'"));
        screenshot("BiliSpeed-account-mobile");
    }
}
