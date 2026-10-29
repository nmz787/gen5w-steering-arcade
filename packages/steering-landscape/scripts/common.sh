#!/system/bin/sh
TARGET=/system/app/SteeringLandscape.apk
BACKUP=/data/nemesis/backups/steering-landscape
STATE_FILE=$BACKUP/previous-state
PREVIOUS_APK=$BACKUP/SteeringLandscape.previous.apk
REMOUNTED_RW=0

same_file() { [ -f "$1" ] && [ -f "$2" ] && cmp "$1" "$2" >/dev/null 2>&1; }
check_target() {
    product=$(getprop ro.product.device)
    fingerprint=$(getprop ro.build.fingerprint)
    [ "$product" = wp_daudioplus_neev23se_us ] && [ "$fingerprint" = 'ecohmc/wp_daudioplus_ecohmc_us/wp_daudioplus_ecohmc_us:4.4.2/KVT49L/eng.vmadmin.20260507.094910:user/dev-keys' ] || {
        echo 'Unsupported product or firmware fingerprint' >&2; return 1;
    }
    [ -f app/SteeringLandscape.apk ] || { echo 'Missing APK payload' >&2; return 1; }
    if [ -e "$TARGET" ] && ! same_file "$TARGET" app/SteeringLandscape.apk && ! same_file "$TARGET" app/SteeringLandscape-approved-legacy.apk; then
        echo 'Existing APK differs from the approved build; refusing replacement' >&2; return 1
    fi
}
begin_system_write() {
    found=0; writable=0
    while read -r dev mountpoint fs options rest; do
        [ "$mountpoint" = /system ] || continue
        found=1
        case ",$options," in *,rw,*) writable=1 ;; esac
        break
    done < /proc/mounts
    [ "$found" -eq 1 ] || { echo 'Cannot determine /system mount state' >&2; return 1; }
    if [ "$writable" -eq 0 ]; then /system/xbin/nemesis remount rw || return 1; REMOUNTED_RW=1; fi
}
end_system_write() {
    if [ "$REMOUNTED_RW" -eq 1 ]; then /system/xbin/nemesis remount ro || return 1; REMOUNTED_RW=0; fi
}
replace_apk() {
    source_apk=$1
    temp_apk=$TARGET.pending.$$
    [ ! -e "$temp_apk" ] || return 1
    cp "$source_apk" "$temp_apk" || return 1
    chmod 0644 "$temp_apk" || { rm -f "$temp_apk"; return 1; }
    same_file "$source_apk" "$temp_apk" || { rm -f "$temp_apk"; return 1; }
    mv "$temp_apk" "$TARGET" || return 1
    sync
    same_file "$source_apk" "$TARGET"
}
prepare_cache() {
    # An Activity has no command-line main; class loading regenerates its cache.
    /system/xbin/nemesis root 1000 -c '/system/bin/dalvikvm -cp /system/app/SteeringLandscape.apk com.codex.steeringlandscape.MainActivity' >/dev/null 2>&1 || true
}
