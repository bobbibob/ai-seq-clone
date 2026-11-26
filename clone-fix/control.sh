#!/system/bin/sh
set -eu
MODDIR=${0%/*}
DATA=/data/adb/aiseq-v2
mkdir -p "$DATA"
case "${1:-status}" in
  on)
    touch "$DATA/enabled"
    nohup sh "$MODDIR/service.sh" >> "$DATA/service.log" 2>&1 </dev/null &
    ;;
  off)
    if [ -f "$DATA/enabled" ]; then mv "$DATA/enabled" "$DATA/disabled"; fi
    ;;
  status) ;;
  *) exit 2 ;;
esac
if [ -f "$DATA/enabled" ]; then echo ON; else echo OFF; fi
if [ -f "$DATA/status.json" ]; then cat "$DATA/status.json"; fi
