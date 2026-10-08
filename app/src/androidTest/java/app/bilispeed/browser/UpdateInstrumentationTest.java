package app.bilispeed.browser;

import android.content.Context;
import android.content.Intent;
import android.content.pm.Signature;

import androidx.core.content.FileProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class UpdateInstrumentationTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private JSONObject manifest(File file) throws Exception {
        return new JSONObject().put("schemaVersion", 1).put("packageName", context.getPackageName())
                .put("versionCode", BuildConfig.VERSION_CODE).put("versionName", BuildConfig.VERSION_NAME)
                .put("minSdk", 26).put("size", file.length()).put("sha256", UpdateVerifier.sha256(file))
                .put("apkUrl", "https://github.com/" + BuildConfig.UPDATE_REPOSITORY + "/releases/download/v"
                        + BuildConfig.VERSION_NAME + "/BiliSpeed-" + BuildConfig.VERSION_NAME + "-Android16.apk")
                .put("notes", "Test");
    }

    private ReleaseInfo parse(JSONObject value) throws IOException {
        return ReleaseInfo.parse(value.toString(), BuildConfig.UPDATE_REPOSITORY);
    }

    private File installedApk() { return new File(context.getApplicationInfo().sourceDir); }

    private void rejected(JSONObject value) {
        assertThrows(IOException.class, () -> parse(value));
    }

    @Test public void versionsUseMonotonicCodeAndRejectDowngrade() throws Exception {
        ReleaseInfo release = parse(manifest(installedApk()));
        assertTrue(release.newerThan(BuildConfig.VERSION_CODE - 1L));
        assertFalse(release.newerThan(BuildConfig.VERSION_CODE));
        assertFalse(release.newerThan(BuildConfig.VERSION_CODE + 1L));
        assertThrows(IOException.class, () -> UpdateVerifier.verify(context, installedApk(),
                release, BuildConfig.VERSION_CODE));
    }

    @Test public void rejectsForeignRepositoryInsecureUrlAndInvalidManifest() throws Exception {
        File apk = installedApk();
        String[] urls = {"http://github.com/" + BuildConfig.UPDATE_REPOSITORY + "/releases/download/v1.1.0/app.apk",
                "https://github.com/attacker/bilispeed-android/releases/download/v1.1.0/app.apk",
                "https://github.com.evil.test/app.apk", "https://github.com@evil.test/app.apk"};
        for (String url : urls) rejected(manifest(apk).put("apkUrl", url));
        rejected(manifest(apk).put("packageName", "app.other.browser"));
        rejected(manifest(apk).put("sha256", "incorrect"));
        rejected(manifest(apk).put("size", ReleaseInfo.MAX_APK_BYTES + 1));
        rejected(manifest(apk).put("size", -1));
        rejected(manifest(apk).put("versionCode", 2.5));
        rejected(manifest(apk).put("schemaVersion", 2));
    }

    @Test public void validatesEveryRedirectIncludingDowngradesAndTraversal() {
        String prefix = "https://github.com/" + BuildConfig.UPDATE_REPOSITORY + "/releases/";
        assertTrue(UpdateClient.allowedNetworkUrl(prefix + "latest/download/update.json", BuildConfig.UPDATE_REPOSITORY));
        assertTrue(UpdateClient.allowedNetworkUrl("https://release-assets.githubusercontent.com/github-production-release-asset/id?sig=a",
                BuildConfig.UPDATE_REPOSITORY));
        String[] invalid = {"http://release-assets.githubusercontent.com/a", prefix + "../outside",
                prefix + "%2e%2e/outside", "https://github.com/other/repo/releases/a",
                "https://github.com:8443/" + BuildConfig.UPDATE_REPOSITORY + "/releases/a",
                "https://release-assets.githubusercontent.com.evil.test/a",
                "https://user@release-assets.githubusercontent.com/a", "file:///storage/emulated/0/update.apk"};
        for (String url : invalid) assertFalse(url, UpdateClient.allowedNetworkUrl(url, BuildConfig.UPDATE_REPOSITORY));
    }

    @Test public void acceptsAnIntactApkWithTheCurrentSigningIdentity() throws Exception {
        File apk = installedApk();
        UpdateVerifier.verify(context, apk, parse(manifest(apk)), BuildConfig.VERSION_CODE - 1L);
    }

    @Test public void rejectsTamperedHashAndFalseVersionClaims() throws Exception {
        File apk = installedApk();
        JSONObject badHash = manifest(apk).put("sha256", "0000000000000000000000000000000000000000000000000000000000000000");
        assertThrows(IOException.class, () -> UpdateVerifier.verify(context, apk, parse(badHash),
                BuildConfig.VERSION_CODE - 1L));
        JSONObject badVersion = manifest(apk).put("versionCode", BuildConfig.VERSION_CODE + 1);
        assertThrows(IOException.class, () -> UpdateVerifier.verify(context, apk, parse(badVersion),
                BuildConfig.VERSION_CODE - 1L));
    }

    @Test public void rejectsAnApkForAnotherPackageEvenWithACorrectHash() throws Exception {
        File other = new File(InstrumentationRegistry.getInstrumentation().getContext().getApplicationInfo().sourceDir);
        assertThrows(IOException.class, () -> UpdateVerifier.verify(context, other, parse(manifest(other)),
                BuildConfig.VERSION_CODE - 1L));
    }

    @Test public void rejectsForeignAndMissingSigningIdentities() {
        Signature trusted = new Signature(new byte[] {1, 2, 3});
        Signature foreign = new Signature(new byte[] {4, 5, 6});
        assertFalse(UpdateVerifier.sameSigners(new Signature[] {foreign}, new Signature[] {trusted}));
        assertFalse(UpdateVerifier.sameSigners(null, new Signature[] {trusted}));
        assertFalse(UpdateVerifier.sameSigners(new Signature[0], new Signature[] {trusted}));
        assertFalse(UpdateVerifier.sameSigners(new Signature[] {trusted, foreign}, new Signature[] {trusted}));
    }

    @Test public void installerOnlyReceivesTheScopedContentUri() {
        File file = new File(context.getFilesDir(), "updates/update-2.apk");
        Intent intent = AppUpdater.installIntent(context, file);
        assertEquals("content", intent.getData().getScheme());
        assertEquals(context.getPackageName() + ".updates", intent.getData().getAuthority());
        assertEquals("application/vnd.android.package-archive", intent.getType());
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertEquals(intent.getData(), intent.getClipData().getItemAt(0).getUri());
        assertThrows(IllegalArgumentException.class, () -> FileProvider.getUriForFile(context,
                context.getPackageName() + ".updates", new File(context.getFilesDir(), "private-account.json")));
    }
}
