#!/usr/bin/env bash
# Builds the signed release APK to attach to the GitHub release of the current version tag.
# F-Droid rebuilds the app from that tag and publishes it with this signature only if its own
# unsigned build is byte-identical, so the APK must come from a clean checkout of the tag.
set -euo pipefail
cd "$(dirname "$0")/.."

[[ -f keystore.properties ]] || { echo "keystore.properties is missing" >&2; exit 1; }
[[ -z "$(git status --porcelain)" ]] || { echo "Working tree is not clean" >&2; exit 1; }

version=$(sed -n 's/^ *versionName = "\(.*\)"$/\1/p' app/build.gradle.kts)
tag="v$version"
[[ "$(git rev-parse HEAD)" == "$(git rev-parse -q --verify "$tag^{commit}" || true)" ]] \
    || echo "Warning: HEAD is not tagged $tag; F-Droid builds from the tag." >&2

./gradlew --no-daemon clean :app:assembleRelease

sdk=$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null || true)
sdk=${sdk:-${ANDROID_HOME:-$HOME/Android/Sdk}}
apksigner=$(ls -d "$sdk"/build-tools/*/ | sort -V | tail -n 1)apksigner

out="build/release/lighttimeclouds-$version.apk"
mkdir -p build/release
cp app/build/outputs/apk/release/app-release.apk "$out"
"$apksigner" verify --print-certs "$out" 2>/dev/null | grep 'certificate SHA-256'
echo "$out"
