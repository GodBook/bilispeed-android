package app.bilispeed.browser;

import android.content.Context;
import android.webkit.CookieManager;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Run with -e phase prepare on a local code-1 fixture, then -e phase verify after real installation. */
public class UpgradePersistenceProbe {
    @Test public void preservesSettingsAndCookiesAcrossTheActualInstaller() throws Exception {
        String phase = InstrumentationRegistry.getArguments().getString("phase", "");
        assumeTrue(phase.equals("prepare") || phase.equals("verify"));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long code = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
        if (phase.equals("prepare")) {
            assertEquals(1, code);
            assertTrue(context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit()
                    .putBoolean("remember", true).putFloat("rate", 3.5f).commit());
            CookieManager.getInstance().setCookie(MainActivity.HOME,
                    "bilispeed_update_probe=preserved; Path=/; Secure; Max-Age=86400");
            CookieManager.getInstance().flush();
        } else {
            assertEquals(2, code);
            assertEquals(3.5f, context.getSharedPreferences("playback", Context.MODE_PRIVATE).getFloat("rate", 0), 0.001f);
            assertTrue(context.getSharedPreferences("playback", Context.MODE_PRIVATE).getBoolean("remember", false));
            String cookies = CookieManager.getInstance().getCookie(MainActivity.HOME);
            assertNotNull(cookies);
            assertTrue("The synthetic cookie did not survive", cookies.contains("bilispeed_update_probe=preserved"));
            CookieManager.getInstance().setCookie(MainActivity.HOME,
                    "bilispeed_update_probe=; Path=/; Secure; Max-Age=0");
            CookieManager.getInstance().flush();
        }
    }
}
