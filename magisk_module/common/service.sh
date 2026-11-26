#!/system/bin/sh

# Create output directory
mkdir -p /data/local_tmp/ai-clone

# Wait for boot
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 5
done

# Wait for system ready
sleep 15

echo "Starting Accessibility Service..." >> /data/local_tmp/ai-clone/service.log

# Install Accessibility Service
if [ -f /system/priv-app/AISeqClone/AISeqClone.apk ]; then
    echo "Accessibility Service already installed" >> /data/local_tmp/ai-clone/service.log
else
    echo "Installing Accessibility Service..." >> /data/local_tmp/ai-clone/service.log
    
    # Copy APK to system
    cp /data/local_tmp/AISeqClone.apk /system/priv-app/AISeqClone/
    
    # Set permissions
    chmod 644 /system/priv-app/AISeqClone/AISeqClone.apk
    chown root:root /system/priv-app/AISeqClone/AISeqClone.apk
    
    # Enable accessibility
    settings put secure enabled_accessibility_services com.technosub.ai.seqclone/com.technosub.ai.seqclone.AccessibilityService
    settings put secure accessibility_enabled 1
fi

# Restart accessibility service
am force-stop com.technosub.ai.seqclone
am start -n com.technosub.ai.seqclone/.MainActivity

echo "Accessibility Service started" >> /data/local_tmp/ai-clone/service.log
