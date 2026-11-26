#!/system/bin/sh
MODDIR=${0%/*}
DATA=/data/adb/aiseq-v2
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 5; done
am start-foreground-service --user 0 -n com.noa.clonecontrol/.ControlService >/dev/null 2>&1 || true
# Keep the watcher alive even when paused: notification buttons only change
# the persistent enabled flag, including after boot with automation disabled.
mkdir -p "$DATA"
PYTHON=/data/data/com.termux/files/usr/bin/python3
[ -x "$PYTHON" ] || exit 1
export HOME=/data/data/com.termux/files/home
export PATH=/data/data/com.termux/files/usr/bin:/system/bin
exec "$PYTHON" "$MODDIR/rotate.py" --execute --watch --resume --kind both >> "$DATA/controller.log" 2>&1
