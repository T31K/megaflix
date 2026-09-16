#!/usr/bin/env bash
# Push local videos into the running Android TV emulator and index them.
# Usage: ./push-test-media.sh [~/megaflix-test-media]
set -euo pipefail

SRC="${1:-$HOME/megaflix-test-media}"
DEST="/sdcard/Movies"

if ! adb get-state >/dev/null 2>&1; then
  echo "No emulator/device. Boot the AVD first (Task 0)." >&2
  exit 1
fi

adb shell mkdir -p "$DEST"

shopt -s nullglob nocaseglob
count=0
for f in "$SRC"/*.{mp4,mkv,avi,mov,webm}; do
  echo "Pushing $(basename "$f")"
  adb push "$f" "$DEST/"
  # Make MediaStore see the new file immediately.
  adb shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
    -d file://$DEST/$(basename "$f")" >/dev/null
  count=$((count+1))
done

if [ "$count" -eq 0 ]; then
  echo "No videos found in $SRC" >&2
  exit 1
fi
echo "Pushed $count file(s). Trigger a full rescan:"
adb shell "cmd media_scanner scan $DEST" >/dev/null 2>&1 || true
echo "Done. Open Megaflix and it will pick them up on resume."
