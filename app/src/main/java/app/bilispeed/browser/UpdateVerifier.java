package app.bilispeed.browser;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class UpdateVerifier {
    static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("设备不支持 SHA-256 校验", exception);
        }
    }

    /** PackageManager verifies the APK signature; Android's installer performs the final check. */
    @SuppressWarnings("deprecation")
    static void verify(Context context, File file, ReleaseInfo release, long installedCode) throws IOException {
        if (!release.newerThan(installedCode)) throw new IOException("拒绝安装旧版或相同版本");
        if (!file.isFile() || file.length() != release.size || !sha256(file).equals(release.sha256)) {
            throw new IOException("更新包校验失败，请重新下载");
        }
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo candidate = manager.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        if (candidate == null || !context.getPackageName().equals(candidate.packageName)) {
            throw new IOException("更新包不是本应用");
        }
        long code = Build.VERSION.SDK_INT >= 28 ? candidate.getLongVersionCode() : candidate.versionCode;
        if (code != release.versionCode || !release.versionName.equals(candidate.versionName)) {
            throw new IOException("更新包版本与发布信息不符");
        }
        if (release.minSdk > Build.VERSION.SDK_INT || candidate.applicationInfo == null
                || candidate.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT) {
            throw new IOException("当前 Android 版本不支持此更新");
        }
        try {
            PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
            Signature[] actual = signatures(candidate);
            Signature[] trusted = signatures(installed);
            if (!sameSigners(actual, trusted)) throw new IOException("更新包签名与当前应用不同，已停止安装");
        } catch (PackageManager.NameNotFoundException exception) {
            throw new IOException("无法检查当前应用签名", exception);
        }
    }

    @SuppressWarnings("deprecation")
    private static Signature[] signatures(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28) {
            return info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        }
        return info.signatures;
    }

    static boolean sameSigners(Signature[] candidate, Signature[] installed) {
        if (candidate == null || installed == null || candidate.length == 0 || installed.length == 0) return false;
        Set<Signature> actual = new HashSet<>(Arrays.asList(candidate));
        Set<Signature> trusted = new HashSet<>(Arrays.asList(installed));
        return actual.equals(trusted);
    }
}
