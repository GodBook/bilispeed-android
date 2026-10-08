package app.bilispeed.browser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

/** A bounded manifest attached to a stable GitHub Release. */
final class ReleaseInfo {
    static final long MAX_APK_BYTES = 100L * 1024 * 1024;
    final int versionCode;
    final String versionName;
    final int minSdk;
    final String apkUrl;
    final String sha256;
    final long size;
    final String notes;
    final String json;

    private ReleaseInfo(JSONObject value, String repository) throws JSONException, IOException {
        if (integer(value, "schemaVersion") != 1
                || !BuildConfig.APPLICATION_ID.equals(value.getString("packageName"))) {
            throw new IOException("更新信息不适用于本应用");
        }
        versionCode = integer(value, "versionCode");
        versionName = value.getString("versionName");
        minSdk = integer(value, "minSdk");
        size = number(value, "size");
        sha256 = value.getString("sha256").toLowerCase(Locale.ROOT);
        apkUrl = value.getString("apkUrl");
        notes = value.optString("notes", "");
        if (versionCode < 1 || !versionName.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?")
                || minSdk < 26 || minSdk > 1000 || size < 1 || size > MAX_APK_BYTES
                || !sha256.matches("[0-9a-f]{64}") || notes.length() > 6000) {
            throw new IOException("更新信息格式无效");
        }
        String expected = repositoryUrl(repository) + "/releases/download/v" + versionName
                + "/BiliSpeed-" + versionName + "-Android16.apk";
        if (!expected.equals(apkUrl)) throw new IOException("更新包地址不属于本项目的发布版本");
        json = value.toString();
    }

    static ReleaseInfo parse(String json, String repository) throws IOException {
        if (json == null || json.length() > 32 * 1024) throw new IOException("更新信息过大");
        try {
            return new ReleaseInfo(new JSONObject(json), repository);
        } catch (JSONException exception) {
            throw new IOException("无法读取更新信息", exception);
        }
    }

    static String repositoryUrl(String repository) throws IOException {
        if (repository == null || !repository.matches("[A-Za-z0-9-]+/[A-Za-z0-9_.-]+")) {
            throw new IOException("更新仓库配置无效");
        }
        return "https://github.com/" + repository;
    }

    boolean newerThan(long installedCode) { return versionCode > installedCode; }

    private static long number(JSONObject json, String key) throws JSONException, IOException {
        Object value = json.get(key);
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            throw new IOException("更新信息中的数值无效");
        }
        return ((Number) value).longValue();
    }

    private static int integer(JSONObject json, String key) throws JSONException, IOException {
        long value = number(json, key);
        if (value < 0 || value > Integer.MAX_VALUE) throw new IOException("更新版本数值无效");
        return (int) value;
    }
}
