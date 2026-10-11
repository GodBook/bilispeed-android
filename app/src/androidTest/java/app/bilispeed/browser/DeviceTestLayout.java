package app.bilispeed.browser;

import android.app.Instrumentation;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import static org.junit.Assert.assertTrue;

/** Fix phone-fixture orientation and wait for the native resize, without changing system rotation. */
final class DeviceTestLayout {
    static void portrait(Instrumentation instrumentation, MainActivity activity) {
        instrumentation.runOnMainSync(()->activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        long deadline=SystemClock.elapsedRealtime()+10000;boolean[] ready={false};
        do {
            instrumentation.runOnMainSync(()->ready[0]=activity.browserForTesting().getWidth()>0
                    && activity.browserForTesting().getHeight()>activity.browserForTesting().getWidth());
            if(ready[0])return;
            SystemClock.sleep(50);
        } while(SystemClock.elapsedRealtime()<deadline);
        assertTrue("Portrait native WebView resize",ready[0]);
    }
}
