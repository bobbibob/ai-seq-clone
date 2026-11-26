#!/system/bin/sh

# Magisk Module Post-FS-Data Script
# Run after /data partition is mounted but before data changes are committed

# Create directory for accessibility service installation
INSTALL_DIR="/data/local_tmp/ai-clone"
APK_PATH="$INSTALL_DIR/AISeqClone.apk"

# Create installation directory if it doesn't exist
mkdir -p "$INSTALL_DIR"

# Install accessibility service APK
if [ -f "$APK_PATH" ]; then
    echo "Installing AI Sequential Clone accessibility service..."

    # Install APK
    pm install -r "$APK_PATH"

    echo "Installation complete!"
else
    echo "APK not found at $APK_PATH"
fi
