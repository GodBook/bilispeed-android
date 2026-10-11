package app.bilispeed.browser;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Opt-in real TalkBack service test. Excluded from the normal finger-input suite. */
public class TalkBackSmokeTest {
    private static final String SERVICE="com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService";
    private static final String[] KEYS={"enabled_accessibility_services","accessibility_enabled","touch_exploration_enabled"};
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final Context context=instrumentation.getTargetContext();
    private UiAutomation automation;
    private void connectAutomation() {
        automation=instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();
        info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                |android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                |android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        automation.setServiceInfo(info);
    }
    private File backup(){return new File(context.getFilesDir(),"device-test-talkback.json");}
    private void shell(String command) throws Exception {
        try(ParcelFileDescriptor descriptor=automation.executeShellCommand(command);
            FileInputStream input=new FileInputStream(descriptor.getFileDescriptor())){input.readAllBytes();}
    }
    private void setting(String key,String value) {
        automation.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        try{assertTrue(Settings.Secure.putString(context.getContentResolver(),key,value));}
        finally{automation.dropShellPermissionIdentity();}
    }
    private void restore() throws Exception {
        if(!backup().isFile())return;
        JSONObject saved;
        try(FileInputStream input=new FileInputStream(backup())){saved=new JSONObject(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
        for(String key:KEYS)setting(key,saved.isNull(key)?null:saved.getString(key));
        // accessibility_enabled is also managed by the connected UiAutomation
        // service; verify the user service list and exploration preference here.
        for(String key:new String[]{KEYS[0],KEYS[2]})assertEquals("Restored accessibility setting "+key,saved.isNull(key)?null:saved.getString(key),Settings.Secure.getString(context.getContentResolver(),key));
        assertTrue(backup().delete());
    }
    @Test public void restoreInterruptedSettings() throws Exception {
        connectAutomation();restore();
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo root,String description) {
        if(root==null)return null;
        if(root.isVisibleToUser() && (description.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()) || description.contentEquals(root.getText()==null?"":root.getText())))return root;
        for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo found=find(root.getChild(i),description);if(found!=null)return found;}
        return null;
    }
    private AccessibilityNodeInfo node(String description) {
        long end=SystemClock.elapsedRealtime()+15000;
        while(SystemClock.elapsedRealtime()<end){
            AccessibilityNodeInfo root=automation.getRootInActiveWindow();
            if(root!=null && String.valueOf(root.getPackageName()).contains("permissioncontroller")) {
                // Dismiss the Suite's first-run notification prompt without
                // granting notification permission or changing app preferences.
                automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
                SystemClock.sleep(300);continue;
            }
            AccessibilityNodeInfo found=find(root,description);
            if(found!=null)return found;
            SystemClock.sleep(100);
        }
        try {
            StringBuilder details=new StringBuilder();
            for(android.view.accessibility.AccessibilityWindowInfo window:automation.getWindows()) {
                details.append("WINDOW ").append(window.getTitle()).append(' ').append(window.getType()).append('\n');
                describe(window.getRoot(),details,0);
            }
            try(FileOutputStream output=new FileOutputStream(new File(context.getExternalFilesDir(null),"BiliSpeed-TalkBack-diagnostic.txt"))){output.write(details.toString().getBytes(StandardCharsets.UTF_8));}
            android.graphics.Bitmap shot=automation.takeScreenshot();
            if(shot!=null)try(FileOutputStream output=new FileOutputStream(new File(context.getExternalFilesDir(null),"BiliSpeed-TalkBack-failure.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);shot.recycle();}
        } catch(Exception ignored) { }
        throw new AssertionError("Missing TalkBack target: "+description);
    }
    private void describe(AccessibilityNodeInfo node,StringBuilder result,int depth) {
        if(node==null || depth>5 || result.length()>12000)return;
        result.append(node.getPackageName()).append(' ').append(node.getClassName()).append(" text=").append(node.getText()).append(" desc=").append(node.getContentDescription()).append('\n');
        for(int i=0;i<node.getChildCount();i++)describe(node.getChild(i),result,depth+1);
    }
    private void activate(String description){
        AccessibilityNodeInfo target=node(description);
        assertTrue("Accessibility focus: "+description,target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS));
        assertTrue("Accessibility click: "+description,target.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    }
    @Test public void talkBackCanNavigateSettingsChooseRateAndChangeTheme() throws Exception {
        connectAutomation();
        restore();
        context.getPackageManager().getServiceInfo(android.content.ComponentName.unflattenFromString(SERVICE),0);
        JSONObject saved=new JSONObject();for(String key:KEYS)saved.put(key,Settings.Secure.getString(context.getContentResolver(),key)==null?JSONObject.NULL:Settings.Secure.getString(context.getContentResolver(),key));
        File pending=new File(context.getFilesDir(),"device-test-talkback.pending");
        try(FileOutputStream output=new FileOutputStream(pending)){output.write(saved.toString().getBytes(StandardCharsets.UTF_8));output.getFD().sync();}
        assertTrue(pending.renameTo(backup()));
        MainActivity activity=null;
        try{
            String enabled=saved.isNull(KEYS[0])?"":saved.getString(KEYS[0]);
            if(!enabled.contains(SERVICE))enabled=enabled.isEmpty()?SERVICE:enabled+":"+SERVICE;
            setting(KEYS[0],enabled);setting(KEYS[1],"1");
            AccessibilityManager manager=context.getSystemService(AccessibilityManager.class);
            long end=SystemClock.elapsedRealtime()+15000;
            while(!manager.isTouchExplorationEnabled() && SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);
            assertTrue("Real TalkBack touch exploration is active",manager.isTouchExplorationEnabled());
            assertTrue(manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_SPOKEN).stream().anyMatch(info->info.getId().contains("marvin.talkback")));
            context.getSharedPreferences("updates",0).edit().putBoolean("automatic",false).commit();
            context.getSharedPreferences("playback",0).edit().putBoolean("remember",true).commit();
            activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            DeviceTestLayout.portrait(instrumentation,activity);
            activate("B站设置");activate("选择 2.5x 倍速");
            assertEquals(2.5f,context.getSharedPreferences("playback",0).getFloat("rate",0),.001f);
            AccessibilityNodeInfo themeRow=null;
            for(int attempt=0;attempt<5 && themeRow==null;attempt++) {
                for(String mode:new String[]{"跟随系统","浅色","深色"}) {
                    themeRow=find(automation.getRootInActiveWindow(),"应用主题，"+mode);
                    if(themeRow!=null)break;
                }
                if(themeRow==null){assertTrue(node("设置页面").performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD));SystemClock.sleep(200);}
            }
            assertNotNull("Accessible theme entry",themeRow);
            assertTrue(themeRow.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS));
            assertTrue(themeRow.performAction(AccessibilityNodeInfo.ACTION_CLICK));
            activate("深色");
            end=SystemClock.elapsedRealtime()+10000;
            while(!"dark".equals(context.getSharedPreferences("playback",0).getString("theme","")) && SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);
            assertEquals("Native theme selection through accessibility","dark",context.getSharedPreferences("playback",0).getString("theme",""));
            activate("B站我的");
            node("B站我的");
            SystemClock.sleep(500);
            android.graphics.Bitmap bitmap=automation.takeScreenshot();assertNotNull(bitmap);
            try(FileOutputStream output=new FileOutputStream(new File(context.getExternalFilesDir(null),"BiliSpeed-TalkBack.png"))){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output));}
            finally{bitmap.recycle();}
        } finally {
            if(activity!=null){MainActivity closing=activity;instrumentation.runOnMainSync(closing::finish);instrumentation.waitForIdleSync();}
            restore();
        }
    }
}
