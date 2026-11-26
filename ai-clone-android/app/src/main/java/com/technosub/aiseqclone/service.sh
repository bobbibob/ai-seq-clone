#!/system/bin/sh
# Run clone automation script
echo "Starting AI Seq Clone automation..."

# Check if phone is booted
if [ "$(getprop sys.boot_completed)" = "1" ]; then
    echo "Phone is booted, starting automation..."
else
    echo "Phone is not fully booted yet. Waiting..."
    sleep 30
fi

# Ensure ADB is ready
adb wait-for-device
sleep 3

# Run the Python script
echo "Running clone automation..."
python3 /data/local/tmp/ai-clone/ai-clone-script.py 2>&1 | tee /data/local/tmp/ai-clone/output.log

echo "Automation completed. Check /data/local/tmp/ai-clone/output.log for details."
