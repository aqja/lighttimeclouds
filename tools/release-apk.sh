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

# The version is the release date, (year - 2026).month.day-n, and versionCode follows from it.
[[ "$version" =~ ^([0-9]+)\.([0-9]{2})\.([0-9]{2})-([0-9]{1,2})$ ]] \
    || { echo "versionName $version is not (year - 2026).MM.DD-n" >&2; exit 1; }
expected=$((BASH_REMATCH[1] * 1000000 + 10#${BASH_REMATCH[2]} * 10000 + 10#${BASH_REMATCH[3]} * 100 + 10#${BASH_REMATCH[4]}))
code=$(sed -n 's/^ *versionCode = \([0-9]*\)$/\1/p' app/build.gradle.kts)
[[ "$code" == "$expected" ]] || { echo "versionCode is $code, expected $expected for $version" >&2; exit 1; }
[[ "$(git rev-parse HEAD)" == "$(git rev-parse -q --verify "$tag^{commit}" || true)" ]] \
    || echo "Warning: HEAD is not tagged $tag; F-Droid builds from the tag, so the release must target this commit." >&2

./gradlew --no-daemon clean :app:assembleRelease

sdk=$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null || true)
sdk=${sdk:-${ANDROID_HOME:-$HOME/Android/Sdk}}
apksigner=$(ls -d "$sdk"/build-tools/*/ | sort -V | tail -n 1)apksigner

out="build/release/lighttimeclouds-$version.apk"
mkdir -p build/release
cp app/build/outputs/apk/release/app-release.apk "$out"
"$apksigner" verify --print-certs "$out" 2>/dev/null | grep 'certificate SHA-256'
echo "$out"
