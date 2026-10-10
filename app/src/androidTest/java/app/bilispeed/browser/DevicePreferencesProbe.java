package app.bilispeed.browser;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

/** Explicit save/restore around legacy fixture suites, including across process restarts. */
public class DevicePreferencesProbe {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private File snapshot() { return new File(context.getFilesDir(), "device-test-preferences.json"); }

    @Test public void savePreferences() throws Exception {
        assertFalse("Restore the previous snapshot before starting another session", snapshot().exists());
        JSONObject saved = new JSONObject();
        for (String name : new String[]{"playback", "updates"}) {
            JSONObject preferences = new JSONObject();
            for (Map.Entry<String, ?> entry : context.getSharedPreferences(name, 0).getAll().entrySet()) {
                Object value = entry.getValue();
                JSONObject item = new JSONObject().put("type", value.getClass().getSimpleName());
                if (value instanceof Set) item.put("value", new JSONArray((Set<?>) value)).put("type", "Set");
                else item.put("value", value);
                preferences.put(entry.getKey(), item);
            }
            saved.put(name, preferences);
        }
        try (FileOutputStream output = new FileOutputStream(snapshot())) {
            output.write(saved.toString().getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        assertTrue(snapshot().isFile());
    }

    @Test public void restorePreferences() throws Exception {
        assertTrue("Missing original preferences", snapshot().isFile());
        JSONObject saved;
        try (FileInputStream input = new FileInputStream(snapshot())) {
            saved = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        for (String name : new String[]{"playback", "updates"}) {
            SharedPreferences.Editor editor = context.getSharedPreferences(name, 0).edit().clear();
            JSONObject preferences = saved.getJSONObject(name);
            for (Iterator<String> keys = preferences.keys(); keys.hasNext();) {
                String key = keys.next(); JSONObject item = preferences.getJSONObject(key);
                switch (item.getString("type")) {
                    case "Boolean": editor.putBoolean(key, item.getBoolean("value")); break;
                    case "Float": editor.putFloat(key, (float) item.getDouble("value")); break;
                    case "Integer": editor.putInt(key, item.getInt("value")); break;
                    case "Long": editor.putLong(key, item.getLong("value")); break;
                    case "String": editor.putString(key, item.getString("value")); break;
                    case "Set":
                        JSONArray array = item.getJSONArray("value"); Set<String> values = new HashSet<>();
                        for (int index = 0; index < array.length(); index++) values.add(array.getString(index));
                        editor.putStringSet(key, values); break;
                    default: throw new IllegalStateException("Unknown preference type");
                }
            }
            assertTrue(editor.commit());
        }
        assertTrue(snapshot().delete());
    }
}
