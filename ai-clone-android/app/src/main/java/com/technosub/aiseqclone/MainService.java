package com.technosub.aiseqclone;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

public class MainService extends Service {

    public static android.content.Context context;
    private AccessibilityManager accessibilityManager;
    private AIImageAutomation automation;

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        accessibilityManager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        automation = new AIImageAutomation(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("auto_run", false)) {
            // Run automation automatically
            automation.runAutomation();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = event.getPackageName() != null ?
                    event.getPackageName().toString() : null;

            // When AI Image Generator app opens, start automation
            if (packageName != null && packageName.equals("com.technosub.unlimitedaiimagen")) {
                automation.runAutomation();
            }
        }
    }

    @Override
    public void onInterrupt() {
        showToast("Automation interrupted");
    }

    private void showToast(final String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }
}
