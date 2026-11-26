package com.technosub.aiseqclone;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AIImageAutomation {
    private static final String TAG = "AIImageAutomation";
    private static final String TARGET_PACKAGE = "com.technosub.unlimitedaiimagen";
    private static final String CLONE_PACKAGE = "com.polestar.super.clone";
    private static final int MAX_CLONES_TO_DELETE = 50;

    private final AccessibilityService service;
    private final Utils utils;
    private int currentCloneId = 1;
    private int totalDailyLimit = 100;
    private int remainingDailyLimit = 100;

    public AIImageAutomation(AccessibilityService service) {
        this.service = service;
        this.utils = new Utils();
    }

    public void runAutomation() {
        new Thread(() -> {
            try {
                // Step 1: Launch Super+Clone app
                utils.showToast("Step 1: Opening Super+Clone...");
                utils.launchApp(CLONE_PACKAGE);
                utils.sleep(3000);

                // Step 2: Delete ALL old clones
                utils.showToast("Step 2: Deleting old clones...");
                deleteAllClones();
                utils.sleep(2000);

                // Step 3: Open AI Image Generator app
                utils.showToast("Step 3: Opening AI Image Generator...");
                utils.launchApp(TARGET_PACKAGE);
                utils.sleep(4000);

                // Step 4: Read daily limit from UI
                utils.showToast("Step 4: Reading daily limit...");
                readDailyLimitFromUI();
                utils.sleep(2000);

                // Step 5: Create sequential clone
                utils.showToast("Step 5: Creating sequential clone: AI Image Generator +20261126_01");
                createSequentialClone();
                utils.sleep(3000);

                // Step 6: Verify and repeat if needed
                utils.showToast("Automation complete!");
                utils.sleep(2000);

            } catch (Exception e) {
                e.printStackTrace();
                utils.showToast("Error: " + e.getMessage());
            }
        }).start();
    }

    private void deleteAllClones() {
        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (rootNode == null) {
            utils.showToast("Cannot find root node");
            return;
        }

        int deletedCount = 0;
        for (int i = 0; i < MAX_CLONES_TO_DELETE; i++) {
            String cloneName = "AI Image Generator +20261126_" + String.format("%02d", i + 1);

            if (findAndDeleteClone(rootNode, cloneName)) {
                deletedCount++;
                utils.showToast("Deleted: " + cloneName);
                utils.sleep(500);
            } else {
                // No more clones found
                break;
            }
        }

        rootNode.recycle();
        utils.showToast("Deleted " + deletedCount + " old clones");
    }

    private boolean findAndDeleteClone(AccessibilityNodeInfo node, String cloneName) {
        if (node == null) return false;

        // Check if this node matches the clone name
        CharSequence nodeText = utils.getTextFromNode(node);
        if (nodeText != null && nodeText.toString().equals(cloneName)) {
            // Find delete button
            return clickDeleteButton(node);
        }

        // Recursively search children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (findAndDeleteClone(child, cloneName)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean clickDeleteButton(AccessibilityNodeInfo cloneNode) {
        // Look for delete button in parent
        AccessibilityNodeInfo parent = cloneNode.getParent();
        if (parent == null) return false;

        // Search for delete button (text "Delete", icon, etc.)
        for (int i = 0; i < parent.getChildCount(); i++) {
            AccessibilityNodeInfo child = parent.getChild(i);
            if (child == null) continue;

            CharSequence childText = utils.getTextFromNode(child);
            if (childText != null) {
                if (childText.toString().toLowerCase().contains("delete")) {
                    return child.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }
        }

        return false;
    }

    private void readDailyLimitFromUI() {
        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (rootNode == null) {
            utils.showToast("Cannot find root node");
            return;
        }

        String limitText = utils.readDailyLimitFromUI(rootNode);
        if (limitText != null) {
            // Parse "X/Y" format
            String[] parts = limitText.split("/");
            if (parts.length == 2) {
                try {
                    totalDailyLimit = Integer.parseInt(parts[1]);
                    remainingDailyLimit = Integer.parseInt(parts[0]);
                    utils.showToast("Daily limit: " + limitText);
                } catch (NumberFormatException e) {
                    utils.showToast("Could not parse limit: " + limitText);
                }
            }
        } else {
            utils.showToast("Could not find daily limit in UI");
        }

        rootNode.recycle();
    }

    private void createSequentialClone() {
        String cloneName = "AI Image Generator +20261126_" + String.format("%02d", currentCloneId);

        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (rootNode == null) {
            utils.showToast("Cannot find root node");
            return;
        }

        // Look for input field for clone name
        findAndClickCloneInput(rootNode, cloneName);
        rootNode.recycle();
    }

    private void findAndClickCloneInput(AccessibilityNodeInfo node, String cloneName) {
        if (node == null) return;

        // Look for text field (input) with placeholder or label "Clone Name"
        CharSequence nodeText = utils.getTextFromNode(node);
        if (nodeText == null || nodeText.toString().isEmpty()) {
            // This might be an input field
            AccessibilityNodeInfo parent = node.getParent();
            if (parent != null) {
                CharSequence labelText = utils.getTextFromNode(parent);
                if (labelText != null && labelText.toString().toLowerCase().contains("name")) {
                    if (node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                        // Enter clone name
                        enterText(node, cloneName);
                        utils.sleep(1000);

                        // Click create button
                        clickCreateButton(node);
                    }
                }
            }
        }

        // Recursively search children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findAndClickCloneInput(child, cloneName);
            }
        }
    }

    private void enterText(AccessibilityNodeInfo node, String text) {
        if (node == null) return;

        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private void clickCreateButton(AccessibilityNodeInfo inputNode) {
        AccessibilityNodeInfo parent = inputNode.getParent();
        if (parent == null) return;

        // Look for "Create", "Add", "+" button
        for (int i = 0; i < parent.getChildCount(); i++) {
            AccessibilityNodeInfo child = parent.getChild(i);
            if (child == null) continue;

            CharSequence childText = utils.getTextFromNode(child);
            if (childText != null) {
                String buttonText = childText.toString().toLowerCase();
                if (buttonText.contains("create") || buttonText.contains("add") || buttonText.contains("+")) {
                    child.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    utils.showToast("Created clone: AI Image Generator +20261126_01");
                    return;
                }
            }
        }
    }
}
