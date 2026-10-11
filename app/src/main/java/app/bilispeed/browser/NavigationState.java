package app.bilispeed.browser;

import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebBackForwardList;
import java.util.LinkedHashMap;
import org.json.JSONObject;

/** Bounded route/position bookmarks for one WebView; never retains documents. */
final class NavigationState {
    static final String[] ROOTS = {MainActivity.HOME, MainActivity.HOME + "v/popular/all",
            "https://t.bilibili.com/", MainActivity.MY_PAGE};
    final String[] tabs = ROOTS.clone();
    private final LinkedHashMap<String, JSONObject> positions = new LinkedHashMap<>();
    int active;

    static int tab(String url) {
        if (!MainActivity.isBiliHttps(url)) return -1;
        Uri uri = Uri.parse(url);
        String host = uri.getHost(), path = uri.getPath() == null ? "/" : uri.getPath();
        if ("t.bilibili.com".equals(host)) return 2;
        if ("account.bilibili.com".equals(host) || "passport.bilibili.com".equals(host)
                || "space.bilibili.com".equals(host)) return 3;
        if (!"www.bilibili.com".equals(host) && !"bilibili.com".equals(host)) return -1;
        if (path.startsWith("/v/popular")) return 1;
        if (path.equals("/") || path.isEmpty()) return 0;
        if (path.startsWith("/__bilispeed__/") || path.startsWith("/history")
                || path.startsWith("/watchlater") || path.startsWith("/account/")) return 3;
        return -1;
    }

    static boolean refreshable(String url) {
        int tab = tab(url);
        String path = url == null ? "" : Uri.parse(url).getPath();
        return tab == 0 || tab == 1 || tab == 2 && (path == null || path.isEmpty() || path.equals("/"))
                || MainActivity.MY_PAGE.equals(url);
    }

    void visit(String url) {
        int index = tab(url);
        if (index >= 0 && url.length() <= 2048) { active = index; tabs[index] = url; }
    }

    void position(JSONObject data) {
        String url = data.optString("url");
        if (!MainActivity.isBiliHttps(url) || url.length() > 2048) return;
        try {
            JSONObject value = new JSONObject().put("y", Math.max(0, Math.min(1000000, data.optDouble("y", 0))))
                    .put("anchor", data.optString("anchor").substring(0, Math.min(512, data.optString("anchor").length())))
                    .put("offset", Math.max(-10000, Math.min(10000, data.optDouble("offset", 0))));
            positions.remove(url); positions.put(url, value);
            while (positions.size() > 32) positions.remove(positions.keySet().iterator().next());
        } catch (Exception ignored) { }
    }

    JSONObject position(String url) { return positions.get(url); }
    void forget(String url) { positions.remove(url); }

    int historyOffset(WebBackForwardList history, String url) {
        int current = history.getCurrentIndex(), best = Integer.MAX_VALUE;
        for (int i = 0; i < history.getSize(); i++) {
            if (url.equals(history.getItemAtIndex(i).getUrl()) && Math.abs(i - current) < Math.abs(best)) best = i - current;
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    void save(Bundle state) {
        state.putStringArray("tabUrls", tabs); state.putInt("activeTab", active);
        state.putString("routePositions", new JSONObject(positions).toString());
    }

    void restore(Bundle state) {
        if (state == null) return;
        String[] saved = state.getStringArray("tabUrls");
        if (saved != null && saved.length == 4) for (int i = 0; i < 4; i++) {
            if (tab(saved[i]) == i && saved[i].length() <= 2048) tabs[i] = saved[i];
        }
        active = Math.max(0, Math.min(3, state.getInt("activeTab")));
        String raw = state.getString("routePositions", "{}");
        if (raw.length() > 100 * 1024) return;
        try {
            JSONObject all = new JSONObject(raw);
            for (java.util.Iterator<String> keys = all.keys(); keys.hasNext();) {
                String url = keys.next(); JSONObject value = all.getJSONObject(url);
                position(new JSONObject(value.toString()).put("url", url));
            }
        } catch (Exception ignored) { }
    }
}
