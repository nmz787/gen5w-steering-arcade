#!/system/bin/sh
set -e
. ./scripts/common.sh
# Check again: a failed preinstall is not assumed to stop the package manager.
check_target
mkdir -p "$BACKUP"
chmod 0700 "$BACKUP"
if [ -f "$STATE_FILE" ]; then
    state=$(cat "$STATE_FILE")
    case "$state" in present) [ -f "$PREVIOUS_APK" ] || exit 1 ;; absent) ;; *) exit 1 ;; esac
else
    state_new=$BACKUP/previous-state.new.$$
    if [ -f "$TARGET" ]; then
        previous_new=$BACKUP/SteeringLandscape.previous.new.$$
        cp "$TARGET" "$previous_new"
        chmod 0600 "$previous_new"
        same_file "$TARGET" "$previous_new" || exit 1
        mv "$previous_new" "$PREVIOUS_APK"
        echo present > "$state_new"
    else
        echo absent > "$state_new"
    fi
    chmod 0600 "$state_new"
    mv "$state_new" "$STATE_FILE"
fi
if [ "$(getprop sys.boot_completed)" = 1 ]; then /system/bin/am force-stop com.codex.steeringlandscape; fi
begin_system_write
trap 'end_system_write' 0
replace_apk app/SteeringLandscape.apk
end_system_write
trap - 0
if [ "$(getprop sys.boot_completed)" = 1 ]; then
    prepare_cache
    count=0
    while [ "$count" -lt 10 ]; do
        if /system/bin/pm path com.codex.steeringlandscape 2>/dev/null | grep -q 'package:/system/app/SteeringLandscape.apk'; then
            echo 'Steering Landscape package registered.'
            break
        fi
        sleep 1
        count=$((count + 1))
    done
    if [ "$count" -ge 10 ]; then
        echo 'APK staged; PackageManager has not registered it yet. A reboot may be required.' >&2
    fi
    if [ -f /data/local/gen5w-home/tiles.json ]; then
        if CLASSPATH="$PWD/home-tiles/installer.jar" /system/bin/app_process \
            /system/bin InstallTiles /data/local/gen5w-home "$PWD/home-tiles" steering_landscape; then
            /system/bin/am force-stop com.hkmc.system.app.homesetting || echo 'Could not stop the home editor' >&2
            /system/bin/am force-stop com.android.launcher3 || echo 'Could not stop the home launcher' >&2
            if ! /system/bin/am start -a android.intent.action.MAIN \
                -c android.intent.category.HOME -n com.android.launcher3/.view.Launcher; then
                echo 'Could not restart the home launcher' >&2
            fi
        else
            echo 'Home tile integration failed; package remains installed' >&2
        fi
    fi
fi
