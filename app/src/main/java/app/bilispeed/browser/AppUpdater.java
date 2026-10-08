package app.bilispeed.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native-only update flow. Web pages cannot request a download or installation. */
final class AppUpdater {
    private static final long CHECK_INTERVAL = 24L * 60 * 60 * 1000;
    private static final long RETRY_INTERVAL = 15L * 60 * 1000;
    private final Activity activity;
    private final SharedPreferences preferences;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final UpdateClient client = new UpdateClient(BuildConfig.UPDATE_REPOSITORY);
    private final File folder;
    private ReleaseInfo available;
    private ReleaseInfo pending;
    private AlertDialog operationDialog;
    private boolean busy;
    private boolean manualCheck;
    private boolean resumed;
    private volatile boolean cancelled;
    private volatile boolean closed;
    private final Runnable automaticCheck = this::checkAutomatically;

    private void checkAutomatically() {
        if (resumed && !closed && !BuildConfig.DEBUG && automaticEnabled() && pending == null
                && automaticCheckDue(System.currentTimeMillis(), preferences.getLong("last_check", 0),
                preferences.getLong("last_attempt", 0))) check(false);
    }

    AppUpdater(Activity activity) {
        this.activity = activity;
        preferences = activity.getSharedPreferences("updates", Context.MODE_PRIVATE);
        folder = new File(activity.getFilesDir(), "updates");
        String cached = preferences.getString("pending", null);
        if (cached != null) {
            try {
                ReleaseInfo release = ReleaseInfo.parse(cached, BuildConfig.UPDATE_REPOSITORY);
                if (release.newerThan(BuildConfig.VERSION_CODE) && apk(release).isFile()) {
                    pending = release;
                    available = release;
                } else {
                    apk(release).delete();
                    preferences.edit().remove("pending").remove("awaiting_permission").apply();
                }
            } catch (IOException exception) {
                preferences.edit().remove("pending").remove("awaiting_permission").apply();
            }
        }
        File retained = pending == null ? null : apk(pending);
        worker.execute(() -> cleanupFiles(folder, retained));
    }

    String menuLabel() {
        if (busy) return "正在处理更新…";
        if (pending != null) return "安装已下载的 " + pending.versionName;
        if (available != null) return "更新到 " + available.versionName;
        return "检查更新";
    }

    boolean automaticEnabled() { return preferences.getBoolean("automatic", true); }

    void toggleAutomatic() {
        boolean enabled = !automaticEnabled();
        preferences.edit().putBoolean("automatic", enabled).apply();
        toast(enabled ? "启动时会检查更新，成功后间隔 24 小时" : "已关闭启动时检查，可在菜单手动检查");
    }

    void onResume() {
        resumed = true;
        main.removeCallbacks(automaticCheck);
        if (preferences.getBoolean("awaiting_permission", false)) {
            preferences.edit().remove("awaiting_permission").apply();
            if (activity.getPackageManager().canRequestPackageInstalls() && pending != null) {
                showRelease(pending);
            } else {
                toast("尚未允许安装更新，可从菜单继续");
            }
            return;
        }
        main.postDelayed(automaticCheck, 5000);
    }

    void onPause() {
        resumed = false;
        main.removeCallbacks(automaticCheck);
    }

    static boolean automaticCheckDue(long now, long lastSuccess, long lastAttempt) {
        return (lastSuccess == 0 || now < lastSuccess || now - lastSuccess >= CHECK_INTERVAL)
                && (lastAttempt == 0 || now < lastAttempt || now - lastAttempt >= RETRY_INTERVAL);
    }

    void checkManually() {
        if (busy) {
            if (manualCheck && operationDialog != null) operationDialog.show();
            else toast("正在处理更新，请稍候");
            return;
        }
        if (pending != null && apk(pending).isFile()) showRelease(pending);
        else check(true);
    }

    private void check(boolean manual) {
        if (busy || closed) return;
        busy = true;
        manualCheck = manual;
        cancelled = false;
        preferences.edit().putLong("last_attempt", System.currentTimeMillis()).apply();
        if (manual) {
            operationDialog = new AlertDialog.Builder(activity).setTitle("检查更新")
                    .setMessage("正在连接 GitHub…").setNegativeButton("取消", (dialog, which) -> cancelCheck())
                    .setOnCancelListener(dialog -> cancelCheck()).show();
        }
        worker.execute(() -> {
            try {
                ReleaseInfo release = client.latest();
                deliver(() -> {
                    busy = false;
                    dismissOperation();
                    if (cancelled) { manualCheck = false; return; }
                    preferences.edit().putLong("last_check", System.currentTimeMillis()).apply();
                    available = release.newerThan(BuildConfig.VERSION_CODE) ? release : null;
                    if (manualCheck && resumed) {
                        if (available != null) showRelease(release);
                        else new AlertDialog.Builder(activity).setTitle("已是最新版本")
                                .setMessage("当前版本 " + BuildConfig.VERSION_NAME).setPositiveButton("知道了", null).show();
                    } else if (available != null && resumed
                            && preferences.getInt("notified_code", 0) != release.versionCode) {
                        preferences.edit().putInt("notified_code", release.versionCode).apply();
                        toast("发现新版本 " + release.versionName + "，可在菜单更新");
                    }
                    manualCheck = false;
                });
            } catch (Exception exception) {
                deliver(() -> {
                    busy = false;
                    dismissOperation();
                    if (manualCheck && resumed) failure("检查更新失败", exception);
                    manualCheck = false;
                });
            }
        });
    }

    private void cancelCheck() {
        manualCheck = false;
        cancelled = true;
        client.cancel();
    }

    private void showRelease(ReleaseInfo release) {
        if (closed || !resumed) return;
        if (release.minSdk > Build.VERSION.SDK_INT) {
            new AlertDialog.Builder(activity).setTitle("此更新需要更高版本的 Android")
                    .setMessage("新版本 " + release.versionName + " 需要 Android API " + release.minSdk)
                    .setPositiveButton("知道了", null).show();
            return;
        }
        boolean downloaded = pending != null && pending.versionCode == release.versionCode && apk(release).isFile();
        String message = "当前 " + BuildConfig.VERSION_NAME + " → 新版 " + release.versionName
                + "\n下载大小 " + String.format(Locale.CHINA, "%.2f MB", release.size / 1048576.0)
                + (release.notes.isEmpty() ? "" : "\n\n" + release.notes)
                + "\n\n覆盖安装会保留倍速设置和网页登录状态。";
        new AlertDialog.Builder(activity).setTitle("发现新版本").setMessage(message)
                .setNegativeButton("稍后", null)
                .setNeutralButton("发布页", (dialog, which) -> openReleases())
                .setPositiveButton(downloaded ? "安装已下载版本" : "下载并更新", (dialog, which) -> {
                    if (downloaded) verifyAndInstall(release); else download(release);
                }).show();
    }

    private void download(ReleaseInfo release) {
        if (busy || closed) return;
        busy = true;
        cancelled = false;
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, padding);
        TextView label = new TextView(activity);
        label.setText("正在下载 " + release.versionName + "…");
        ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        layout.addView(label);
        layout.addView(progress, new LinearLayout.LayoutParams(-1, -2));
        operationDialog = new AlertDialog.Builder(activity).setTitle("下载更新").setView(layout)
                .setNegativeButton("取消下载", (dialog, which) -> cancelDownload())
                .setOnCancelListener(dialog -> cancelDownload()).show();
        File partial = new File(folder, "downloading.apk");
        worker.execute(() -> {
            try {
                if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("无法创建更新目录");
                client.download(release, partial, new UpdateClient.Progress() {
                    private long last;
                    @Override public boolean cancelled() { return cancelled || closed; }
                    @Override public void received(long bytes, long total) {
                        long now = android.os.SystemClock.elapsedRealtime();
                        if (now - last < 150 && bytes != total) return;
                        last = now;
                        deliver(() -> {
                            progress.setProgress((int) (bytes * 100 / total));
                            label.setText(bytes == total ? "下载完成，正在校验…"
                                    : "已下载 " + (bytes * 100 / total) + "%");
                        });
                    }
                });
                if (cancelled || closed) throw new InterruptedIOException("下载已取消");
                UpdateVerifier.verify(activity, partial, release, BuildConfig.VERSION_CODE);
                if (cancelled || closed) throw new InterruptedIOException("下载已取消");
                File destination = apk(release);
                if (destination.exists() && !destination.delete()) throw new IOException("无法替换旧的更新包");
                if (!partial.renameTo(destination)) throw new IOException("无法保存更新包");
                preferences.edit().putString("pending", release.json).apply();
                cleanupFiles(folder, destination);
                deliver(() -> {
                    busy = false;
                    pending = release;
                    dismissOperation();
                    if (cancelled) return;
                    if (resumed) offerInstall(release);
                    else toast("更新已下载，可从菜单安装");
                });
            } catch (Exception exception) {
                partial.delete();
                deliver(() -> {
                    busy = false;
                    dismissOperation();
                    if (!cancelled && resumed) failure("下载更新失败", exception);
                });
            }
        });
    }

    private void cancelDownload() {
        cancelled = true;
        client.cancel();
        toast("下载已取消");
    }

    private void verifyAndInstall(ReleaseInfo release) {
        if (busy || closed) return;
        busy = true;
        operationDialog = new AlertDialog.Builder(activity).setTitle("校验更新包")
                .setMessage("正在检查文件和应用签名…").setCancelable(false).show();
        worker.execute(() -> {
            try {
                UpdateVerifier.verify(activity, apk(release), release, BuildConfig.VERSION_CODE);
                deliver(() -> {
                    busy = false;
                    dismissOperation();
                    if (resumed) offerInstall(release);
                });
            } catch (Exception exception) {
                apk(release).delete();
                preferences.edit().remove("pending").remove("awaiting_permission").apply();
                deliver(() -> {
                    pending = null;
                    busy = false;
                    dismissOperation();
                    if (resumed) failure("更新包校验失败", exception);
                });
            }
        });
    }

    private void offerInstall(ReleaseInfo release) {
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity).setTitle("允许安装应用更新")
                    .setMessage("首次更新需要在系统设置中允许本应用安装更新包。设置完成后返回，即可继续安装。")
                    .setNegativeButton("稍后", null).setPositiveButton("去设置", (dialog, which) -> {
                        try {
                            preferences.edit().putBoolean("awaiting_permission", true).apply();
                            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName())));
                        } catch (ActivityNotFoundException exception) {
                            preferences.edit().remove("awaiting_permission").apply();
                            failure("无法打开安装设置", exception);
                        }
                    }).show();
            return;
        }
        try {
            activity.startActivity(installIntent(activity, apk(release)));
        } catch (ActivityNotFoundException | IllegalArgumentException | SecurityException exception) {
            failure("无法打开系统安装器", exception);
        }
    }

    static Intent installIntent(Context context, File file) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".updates", file);
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("APK更新", uri));
        return intent;
    }

    private File apk(ReleaseInfo release) { return new File(folder, "update-" + release.versionCode + ".apk"); }

    static void cleanupFiles(File folder, File retained) {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile() && !file.equals(retained)
                    && (file.getName().equals("downloading.apk") || file.getName().matches("update-[0-9]+\\.apk"))) {
                file.delete();
            }
        }
    }

    private void failure(String title, Exception exception) {
        String detail = exception.getMessage();
        if (!(exception instanceof IOException) || detail == null || !detail.matches("(?s).*[\\u4e00-\\u9fff].*")) {
            detail = "网络连接失败或系统暂不可用，请确认能够访问 GitHub 后重试。";
        }
        new AlertDialog.Builder(activity).setTitle(title).setMessage(detail)
                .setNegativeButton("关闭", null).setPositiveButton("打开发布页", (dialog, which) -> openReleases()).show();
    }

    private void openReleases() {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/" + BuildConfig.UPDATE_REPOSITORY + "/releases")));
        } catch (ActivityNotFoundException exception) { toast("没有可用的系统浏览器"); }
    }

    private void dismissOperation() {
        if (operationDialog != null) { operationDialog.dismiss(); operationDialog = null; }
    }

    private void deliver(Runnable action) {
        main.post(() -> { if (!closed && !activity.isFinishing()) action.run(); });
    }

    private void toast(String message) { Toast.makeText(activity, message, Toast.LENGTH_LONG).show(); }

    void close() {
        closed = true;
        cancelled = true;
        client.cancel();
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        dismissOperation();
    }
}
