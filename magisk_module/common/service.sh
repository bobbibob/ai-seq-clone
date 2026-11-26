#!/system/bin/sh

# Magisk Module Service Script
# Run in background as a service
# This script will automatically install the accessibility service APK

# Create installation directory
INSTALL_DIR="/data/local_tmp/ai-clone"
APK_PATH="/magisk/module/AISeqClone.apk"

# Create installation directory if it doesn't exist
mkdir -p "$INSTALL_DIR"

# Check if APK exists
if [ -f "$APK_PATH" ]; then
    echo "AI Sequential Clone service starting..."

    # Install accessibility service APK
    pm install -r "$APK_PATH"

    echo "Accessibility service installed successfully!"

    # Enable accessibility service if not already enabled
    # (This would require additional steps)

    # Keep service running
    while true; do
        sleep 60
    done

else
    echo "APK not found at $APK_PATH"
    # Exit service
    exit 1
fi
