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
            assertEquals(Long.parseLong(InstrumentationRegistry.getArguments().getString("fromCode", "1")), code);
            assertTrue(context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit()
                    .putBoolean("remember", true).putFloat("rate", 3.5f).commit());
            CookieManager.getInstance().setCookie(MainActivity.HOME,
                    "bilispeed_update_probe=preserved; Path=/; Secure; Max-Age=86400");
            CookieManager.getInstance().flush();
        } else {
            assertEquals(Long.parseLong(InstrumentationRegistry.getArguments().getString("toCode", "2")), code);
            assertEquals(3.5f, context.getSharedPreferences("playback", Context.MODE_PRIVATE).getFloat("rate", 0), 0.001f);
            assertTrue(context.getSharedPreferences("playback", Context.MODE_PRIVATE).getBoolean("remember", false));
            String cookies = CookieManager.getInstance().getCookie(MainActivity.HOME);
            assertNotNull(cookies);
            assertTrue("The synthetic cookie did not survive", cookies.contains("bilispeed_update_probe=preserved"));
            if (InstrumentationRegistry.getArguments().containsKey("toCode")) {
                java.io.File snapshot = new java.io.File(context.getFilesDir(), "device-test-preferences.json");
                assertTrue("Original preference snapshot must be available", snapshot.isFile());
                org.json.JSONObject saved;
                try (java.io.FileInputStream input = new java.io.FileInputStream(snapshot)) {
                    saved = new org.json.JSONObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                }
                for (String name : new String[]{"playback","updates"}) {
                    org.json.JSONObject original = saved.getJSONObject(name);
                    java.util.Map<String,?> actual = context.getSharedPreferences(name,0).getAll();
                    for (java.util.Iterator<String> keys=original.keys();keys.hasNext();) {
                        String key=keys.next();
                        if (name.equals("playback") && (key.equals("rate") || key.equals("remember"))) continue;
                        assertTrue("Preserved preference key: " + key,actual.containsKey(key));
                        Object before=original.getJSONObject(key).get("value"), after=actual.get(key);
                        if (after instanceof Number) assertEquals("Preserved preference: " + key,((Number)before).doubleValue(),((Number)after).doubleValue(),.00001);
                        else if (after instanceof java.util.Set) {
                            org.json.JSONArray values=(org.json.JSONArray)before;java.util.Set<String> set=new java.util.HashSet<>();
                            for(int i=0;i<values.length();i++)set.add(values.getString(i));assertEquals(set,after);
                        } else assertEquals("Preserved preference: " + key,before.toString(),after.toString());
                    }
                }
            }
            CookieManager.getInstance().setCookie(MainActivity.HOME,
                    "bilispeed_update_probe=; Path=/; Secure; Max-Age=0");
            CookieManager.getInstance().flush();
        }
    }
}
