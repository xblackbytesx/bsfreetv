#!/usr/bin/env bash
# Runs Gradle in the container (as root) for the given task(s), copies APKs to
# build-output/, then restores host ownership of everything it created.
set -uo pipefail
cd /src/bsfreetv

if [ ! -f keystore/debug.keystore ]; then
    echo ">> generating stable debug keystore (keystore/debug.keystore)"
    mkdir -p keystore
    keytool -genkeypair -keystore keystore/debug.keystore -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi

echo ">> gradle $*"
gradle --no-daemon "$@"
status=$?

if [[ "$*" == *assembleDebug* && -f app/build/outputs/apk/debug/app-debug.apk ]]; then
    mkdir -p build-output
    cp -fv app/build/outputs/apk/debug/app-debug.apk build-output/bsfreetv-debug.apk
fi
if [[ "$*" == *assembleRelease* ]]; then
    for f in app/build/outputs/apk/release/app-release.apk app/build/outputs/apk/release/app-release-unsigned.apk; do
        [ -f "$f" ] && mkdir -p build-output && cp -fv "$f" "build-output/bsfreetv-$(basename "$f" | sed 's/app-//')"
    done
fi

owner="$(stat -c '%u:%g' . 2>/dev/null || echo '')"
if [[ -n "$owner" ]]; then
    for d in .gradle .kotlin build build-output app/build app/.gradle keystore; do
        [ -e "$d" ] && chown -R "$owner" "$d" 2>/dev/null || true
    done
fi
exit $status
