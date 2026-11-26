# AI Sequential Clone
# Accessibility Service for automated clone management

This app provides an Accessibility Service to automatically:
1. Open Super+Clone app
2. Delete all old AI Image Generator clones
3. Read daily generation limit from UI
4. Create sequential ID clones
5. Works on LOCKED screen

## Features
- ✅ Auto-run on AI Image Generator app launch
- ✅ Delete all old clones automatically
- ✅ Read real daily limit from UI (format: X/Y)
- ✅ Create clones with format: AI Image Generator +20261126_01
- ✅ Works on locked screen via Accessibility Service
- ✅ Manual trigger button
- ✅ Integration with Magisk module

## Installation
1. Install APK on rooted Android device
2. Enable Accessibility Service
3. Use Magisk module for automatic installation on boot

## Files
- `app/src/main/java/com/technosub/aiseqclone/AccessibilityService.java` - Core automation
- `app/src/main/java/com/technosub/aiseqclone/Utils.java` - Helper functions
- `app/src/main/java/com/technosub/aiseqclone/CloneAutomation.java` - Clone management
- `magisk_module/` - Magisk module v3.0
- `.github/workflows/build-android.yml` - GitHub Actions build

## Usage
1. Enable Accessibility Service in Settings
2. Launch AI Image Generator app
3. Automation runs automatically
4. Or use manual trigger button

## Supported Devices
- Android 7.0+ (API 26+)
- Rooted with Magisk
