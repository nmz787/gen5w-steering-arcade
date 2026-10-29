#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
API="19"
BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-36.0.0}"
PLATFORM="$SDK_DIR/platforms/android-$API"
TOOLS="$SDK_DIR/build-tools/$BUILD_TOOLS"
OUT="$PROJECT_DIR/build"

for tool in "$PLATFORM/android.jar" "$TOOLS/aapt" "$TOOLS/d8" \
            "$TOOLS/zipalign" "$TOOLS/apksigner"; do
    if [[ ! -x "$tool" && ! -f "$tool" ]]; then
        printf 'Missing Android build prerequisite: %s\n' "$tool" >&2
        exit 1
    fi
done

command -v javac >/dev/null
command -v jar >/dev/null
command -v keytool >/dev/null

mkdir -p "$OUT"
rm -rf "$OUT/classes" "$OUT/dex"
rm -f "$OUT/classes.jar" "$OUT/SteeringLandscape-unsigned.apk" \
    "$OUT/SteeringLandscape-aligned.apk" "$OUT/SteeringLandscape.apk" \
    "$OUT/SteeringLandscape.apk.idsig"
mkdir -p "$OUT/classes" "$OUT/dex"

javac -source 8 -target 8 \
    -bootclasspath "$PLATFORM/android.jar" \
    -classpath "$PLATFORM/android.jar" \
    -d "$OUT/classes" "$PROJECT_DIR/MainActivity.java" "$PROJECT_DIR/VehicleReader.java" "$PROJECT_DIR/ArtSurface.java" "$PROJECT_DIR/KartWorld.java"
jar cf "$OUT/classes.jar" -C "$OUT/classes" .
"$TOOLS/d8" --min-api "$API" --lib "$PLATFORM/android.jar" \
    --output "$OUT/dex" "$OUT/classes.jar"

"$TOOLS/aapt" package -f \
    -M "$PROJECT_DIR/AndroidManifest.xml" \
    -I "$PLATFORM/android.jar" \
    -F "$OUT/SteeringLandscape-unsigned.apk"
jar uf "$OUT/SteeringLandscape-unsigned.apk" -C "$OUT/dex" classes.dex
"$TOOLS/zipalign" -f 4 "$OUT/SteeringLandscape-unsigned.apk" \
    "$OUT/SteeringLandscape-aligned.apk"

KEYSTORE="$OUT/debug.keystore"
if [[ ! -f "$KEYSTORE" ]]; then
    keytool -genkeypair -noprompt -keystore "$KEYSTORE" \
        -storepass android -keypass android -alias androiddebugkey \
        -dname "CN=Android Debug,O=Android,C=US" \
        -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi

"$TOOLS/apksigner" sign \
    --ks "$KEYSTORE" --ks-key-alias androiddebugkey \
    --ks-pass pass:android --key-pass pass:android \
    --min-sdk-version "$API" \
    --v1-signing-enabled true --v2-signing-enabled false \
    --v3-signing-enabled false --v4-signing-enabled false \
    --out "$OUT/SteeringLandscape.apk" "$OUT/SteeringLandscape-aligned.apk"

printf 'Built %s\n' "$OUT/SteeringLandscape.apk"
