package com.technosub.aiseqclone;

import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Utils {
    private static final String TAG = "Utils";
    private static final long DEFAULT_TIMEOUT = 5000;

    public static void tapNode(AccessibilityNodeInfo root, CharSequence text, long timeout) {
        final CountDownLatch latch = new CountDownLatch(1);
        final int[] result = {0}; // 0 = not found, 1 = tapped

        findAndTapNode(root, text, timeout, result, () -> latch.countDown());

        try {
            if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
                showToast("Timeout tapping: " + text);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void tapNode(AccessibilityNodeInfo root, CharSequence text) {
        tapNode(root, text, DEFAULT_TIMEOUT);
    }

    private static void findAndTapNode(AccessibilityNodeInfo node, CharSequence targetText,
                                       long timeout, int[] result, Runnable callback) {
        if (node == null) return;

        // Check if this node matches
        if (targetText != null && targetText.equals(node.getText())) {
            result[0] = 1;
            boolean tapped = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (tapped) {
                showToast("Tapped: " + targetText);
            } else {
                showToast("Failed to tap: " + targetText);
            }
            callback.run();
            return;
        }

        // Recursively check children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findAndTapNode(child, targetText, timeout, result, callback);
                if (result[0] == 1) {
                    return;
                }
            }
        }
    }

    public static void launchApp(String packageName) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setPackage(packageName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainService.context.startActivity(intent);
    }

    public static String getTextFromNode(AccessibilityNodeInfo node) {
        if (node == null) return null;

        CharSequence text = node.getText();
        if (text != null) {
            return text.toString();
        }

        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null) {
            return contentDesc.toString();
        }

        return null;
    }

    public static boolean findAndClickChildByText(AccessibilityNodeInfo parent, CharSequence text) {
        if (parent == null) return false;

        for (int i = 0; i < parent.getChildCount(); i++) {
            AccessibilityNodeInfo child = parent.getChild(i);
            if (child == null) continue;

            CharSequence childText = getTextFromNode(child);
            if (childText != null && childText.toString().equals(text)) {
                boolean clicked = child.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (clicked) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void showToast(final String message) {
        if (MainService.context != null) {
            MainService.context.runOnUiThread(() -> {
                Toast.makeText(MainService.context, message, Toast.LENGTH_SHORT).show();
            });
        }
    }

    public static String readDailyLimitFromUI(AccessibilityNodeInfo root) {
        if (root == null) return null;

        // Look for text like "10/20" or "5/10" in UI
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("/");
        for (AccessibilityNodeInfo node : nodes) {
            CharSequence text = node.getText();
            if (text != null) {
                String limitText = text.toString();
                if (limitText.contains("/")) {
                    return limitText;
                }
            }
        }

        return null;
    }

    public static boolean tapByCoordinates(int x, int y) {
        if (MainService.context == null) return false;

        try {
            MainService.context.performHapticFeedback(0, HapticFeedbackConstants.LONG_PRESS);
            return MainService.context.performGlobalAction(AccessibilityNodeInfo.ACTION_CLICK);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
