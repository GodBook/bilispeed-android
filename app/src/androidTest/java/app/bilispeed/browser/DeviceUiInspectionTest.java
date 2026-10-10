package app.bilispeed.browser;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.webkit.WebView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;

import org.junit.Test;

import java.io.File;

/** Opt-in, local USB inspection of the production WebView. Does not clear app data or settings. */
public class DeviceUiInspectionTest {
    @Test public void inspectConnectedDevice() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        File stop = new File(context.getExternalFilesDir(null), "stop-ui-inspection");
        if (stop.exists() && !stop.delete()) throw new IllegalStateException("Stale inspection marker");
        int seconds = Integer.parseInt(InstrumentationRegistry.getArguments().getString("inspectionSeconds", "900"));
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        instrumentation.runOnMainSync(() -> WebView.setWebContentsDebuggingEnabled(true));
        String proxy = InstrumentationRegistry.getArguments().getString("inspectionProxy");
        if (proxy != null) {
            java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
            instrumentation.runOnMainSync(() -> ProxyController.getInstance().setProxyOverride(
                    new ProxyConfig.Builder().addProxyRule(proxy).build(), Runnable::run, ready::countDown));
            try { ready.await(5, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            instrumentation.runOnMainSync(() -> activity.browserForTesting().reload());
        }
        try {
            System.out.println("BILISPEED_USB_INSPECTION_READY");
            long deadline = SystemClock.elapsedRealtime() + seconds * 1000L;
            while (!stop.exists() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(200);
        } finally {
            instrumentation.runOnMainSync(() -> {
                WebView.setWebContentsDebuggingEnabled(false);
                if (proxy != null) ProxyController.getInstance().clearProxyOverride(Runnable::run, () -> {});
                activity.finish();
            });
            if (stop.exists() && !stop.delete()) System.out.println("Inspection marker cleanup deferred");
        }
    }
}
