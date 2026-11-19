package app.bilispeed.browser;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.*;

public class ClipboardVideoInstrumentationTest {
    private static final String VIDEO = "https://www.bilibili.com/video/BV17x411w7KC/?p=2";
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    @Test public void extractsVideoFromShareTextAndRejectsNonVideoOrUntrustedHosts() {
        assertEquals(VIDEO, ClipboardVideoLink.find("【分P】先看 https://example.org/info ，再看（http://m.bilibili.com/video/BV17x411w7KC/?p=2）。").url);
        assertEquals("https://www.bilibili.com/video/av123", ClipboardVideoLink.find("bilibili.com/video/av123").url);
        assertEquals("https://www.bilibili.com/bangumi/play/ep42", ClipboardVideoLink.find("https://bilibili.com/bangumi/play/ep42").url);
        ClipboardVideoLink shortLink = ClipboardVideoLink.find("分享短链：https://b23.tv/aB345xZ！");
        assertTrue(shortLink.shortLink);
        assertEquals("https://b23.tv/aB345xZ", shortLink.url);
        for (String invalid : new String[]{"普通文字 BV17x411w7KC", "https://www.bilibili.com/read/cv42",
                "https://www.bilibili.com.evil.example/video/BV17x411w7KC", "https://b23.tv@evil.example/ABC",
                "https://evil.example/?url=https://www.bilibili.com/video/BV17x411w7KC",
                "https://www.bilibili.com:8443/video/BV17x411w7KC", "https://b23.tv/", "https://b23.tv/a/b",
                "ftp://www.bilibili.com/video/BV17x411w7KC", new String(new char[16385]).replace('\0', 'x')}) {
            assertNull(invalid.length() > 100 ? "long text" : invalid, ClipboardVideoLink.find(invalid));
        }
    }

    @Test public void launchPromptsAndForegroundChecksAgainWhenClipboardChanges() throws Exception {
        Context context = instrumentation.getTargetContext();
        ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
        ArrayList<MainActivity> activities = new ArrayList<>();
        MainActivity seed = start(activities, true);
        AtomicReference<ClipData> originalClip = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> originalClip.set(clipboard.getPrimaryClip()));
        MainActivity current = seed;
        try {
            putClip(clipboard, "https://www.bilibili.com.evil.example/video/BV17x411w7KC/");
            MainActivity invalid = start(activities, false);
            current = invalid;
            activityCheck(() -> assertNull(invalid.clipboardDialogForTesting()));

            putClip(clipboard, "【测试分享】 " + VIDEO + " 。");
            MainActivity browser = start(activities, false);
            current = browser;
            awaitUi(() -> browser.clipboardDialogForTesting() != null);
            activityCheck(() -> browser.clipboardDialogForTesting().getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
            awaitUi(browser::hasWindowFocus);
            activityCheck(() -> assertNotEquals(VIDEO, browser.browserForTesting().getUrl()));

            MainActivity share = start(activities, true);
            activityCheck(() -> assertNull("An explicit share takes precedence", share.clipboardDialogForTesting()));
            instrumentation.runOnMainSync(share::finish);
            awaitUi(browser::hasWindowFocus);
            activityCheck(() -> assertNull("Returning with the same clip must not prompt twice", browser.clipboardDialogForTesting()));

            MainActivity shortLinkSource = backgroundWithClip(activities, clipboard, "分享：https://b23.tv/aB345xZ 。");
            instrumentation.runOnMainSync(shortLinkSource::finish);
            awaitUi(() -> browser.clipboardDialogForTesting() != null);
            activityCheck(() -> {
                AlertDialog dialog = browser.clipboardDialogForTesting();
                assertEquals("打开链接", dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
                dialog.cancel();
            });
            awaitUi(browser::hasWindowFocus);

            MainActivity videoSource = backgroundWithClip(activities, clipboard, VIDEO);
            instrumentation.runOnMainSync(videoSource::finish);
            awaitUi(() -> browser.clipboardDialogForTesting() != null);
            activityCheck(() -> browser.clipboardDialogForTesting().getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            awaitUi(() -> VIDEO.equals(browser.browserForTesting().getUrl())
                    || VIDEO.equals(browser.browserForTesting().getOriginalUrl()));
        } finally {
            MainActivity active = current;
            instrumentation.runOnMainSync(() -> {
                AlertDialog open = active.clipboardDialogForTesting();
                if (open != null) open.dismiss();
                if (originalClip.get() != null) clipboard.setPrimaryClip(originalClip.get());
                else if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip();
                else clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
                for (int i = activities.size() - 1; i >= 0; i--) activities.get(i).finish();
            });
            instrumentation.waitForIdleSync();
        }
    }

    private MainActivity start(ArrayList<MainActivity> activities, boolean share) throws Exception {
        Intent launch = new Intent(instrumentation.getTargetContext(), MainActivity.class)
                .setAction(share ? Intent.ACTION_SEND : Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        if (share) launch.setType("text/plain").putExtra(Intent.EXTRA_TEXT, MainActivity.MY_PAGE);
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(launch);
        activities.add(activity);
        awaitUi(() -> activity.hasWindowFocus() || activity.clipboardDialogForTesting() != null);
        return activity;
    }

    private MainActivity backgroundWithClip(ArrayList<MainActivity> activities, ClipboardManager clipboard, String text) throws Exception {
        MainActivity activity = start(activities, true);
        putClip(clipboard, text);
        return activity;
    }

    private void putClip(ClipboardManager clipboard, String text) {
        instrumentation.runOnMainSync(() -> clipboard.setPrimaryClip(ClipData.newPlainText("BiliSpeed test", text)));
    }

    private void activityCheck(Runnable action) {
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(action);
    }

    private void awaitUi(BooleanSupplier predicate) throws Exception {
        AtomicReference<Boolean> ready = new AtomicReference<>(false);
        long deadline = SystemClock.elapsedRealtime() + 12000;
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync(() -> ready.set(predicate.getAsBoolean()));
            if (ready.get()) { instrumentation.waitForIdleSync(); return; }
            SystemClock.sleep(100);
        }
        fail("Clipboard activity state did not arrive");
    }
}
