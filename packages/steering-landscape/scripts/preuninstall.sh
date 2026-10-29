#!/system/bin/sh
set -e
. ./scripts/common.sh
[ -f "$STATE_FILE" ] || { echo 'No backup state; leaving APK untouched' >&2; exit 1; }
state=$(cat "$STATE_FILE")
case "$state" in present) [ -f "$PREVIOUS_APK" ] || exit 1 ;; absent) ;; *) exit 1 ;; esac
same_file "$TARGET" app/SteeringLandscape.apk || { echo 'Installed APK changed; leaving it untouched' >&2; exit 1; }
if [ "$(getprop sys.boot_completed)" = 1 ]; then /system/bin/am force-stop com.codex.steeringlandscape; fi
begin_system_write
trap 'end_system_write' 0
if [ "$state" = present ]; then replace_apk "$PREVIOUS_APK"; else rm -f "$TARGET"; sync; fi
end_system_write
trap - 0
if [ "$state" = present ] && [ "$(getprop sys.boot_completed)" = 1 ]; then prepare_cache; fi
rm -f "$STATE_FILE"
