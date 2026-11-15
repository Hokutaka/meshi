#!/usr/bin/env bash
# Verify signed APK upgrade compatibility on a disposable emulator.
set -euo pipefail
old="$1"
new="$2"
aapt="$ANDROID_HOME/build-tools/36.0.0/aapt"
signer="$ANDROID_HOME/build-tools/36.0.0/apksigner"
package_name() { "$aapt" dump badging "$1" | sed -n "1s/.*name='\([^']*\)'.*/\1/p"; }
version_code() { "$aapt" dump badging "$1" | sed -n "1s/.*versionCode='\([0-9]*\)'.*/\1/p"; }
cert() { "$signer" verify --print-certs "$1" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n 1; }
old_pkg="$(package_name "$old")"
new_pkg="$(package_name "$new")"
old_version="$(version_code "$old")"
new_version="$(version_code "$new")"
old_cert="$(cert "$old")"
new_cert="$(cert "$new")"
[[ "$old_pkg" == "dev.hokutaka.meshi" && "$new_pkg" == "$old_pkg" ]] || { echo "App ID mismatch"; exit 1; }
[[ "$old_version" =~ ^[0-9]+$ && "$new_version" =~ ^[0-9]+$ ]] || { echo "Invalid version code"; exit 1; }
(( new_version > old_version )) || { echo "versionCode did not increase"; exit 1; }
[[ -n "$old_cert" && "$old_cert" == "$new_cert" ]] || { echo "Signing certificate mismatch"; exit 1; }
adb install "$old"
adb shell am start -n "$old_pkg/.MainActivity"
adb shell am force-stop "$old_pkg"
adb install -r "$new"
adb shell am start -n "$new_pkg/.MainActivity"
installed="$(adb shell dumpsys package "$new_pkg" | tr -d '\r' | grep -m1 'versionCode=' || true)"
[[ "$installed" == *"versionCode=$new_version"* ]] || { echo "New APK version was not installed"; exit 1; }
echo "Previous signed release successfully updated in place"
