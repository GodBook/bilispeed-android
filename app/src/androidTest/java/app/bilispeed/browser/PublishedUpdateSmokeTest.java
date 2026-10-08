package app.bilispeed.browser;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

/** Optional integration check; requires an already published GitHub Release and internet. */
public class PublishedUpdateSmokeTest {
    @Test public void downloadsThePublishedReleaseAndVerifiesItsRealSignature() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UpdateClient client = new UpdateClient(BuildConfig.UPDATE_REPOSITORY);
        ReleaseInfo release = client.latest();
        assertTrue("Published release is older than the source", release.versionCode >= BuildConfig.VERSION_CODE);
        File partial = new File(context.getCacheDir(), "published-update-test.apk");
        try {
            client.download(release, partial, new UpdateClient.Progress() {
                @Override public boolean cancelled() { return false; }
                @Override public void received(long bytes, long total) { }
            });
            // Model the previous version while verifying against the installed production certificate.
            UpdateVerifier.verify(context, partial, release, release.versionCode - 1L);
        } finally {
            partial.delete();
        }
    }
}
