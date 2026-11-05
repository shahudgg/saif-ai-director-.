#!/usr/bin/env bash
set -euo pipefail
exec > >(tee /tmp/engine-script.log) 2>&1
BASE='https://github.com/termux/termux-app/releases/download/v0.118.3'
curl --fail --location --retry 3 "$BASE/termux-app_v0.118.3%2Bgithub-debug_x86_64.apk" -o /tmp/termux-official.apk
curl --fail --location --retry 3 "$BASE/termux-app_v0.118.3%2Bgithub-debug_sha256sums" -o /tmp/termux-sha256sums
EXPECTED=$(awk '/x86_64.apk/ {print $1}' /tmp/termux-sha256sums)
test "${#EXPECTED}" -eq 64
echo "$EXPECTED  /tmp/termux-official.apk" | sha256sum -c -
adb install -r /tmp/termux-official.apk
adb shell pm list packages com.termux | grep -q 'package:com.termux'
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.example.EmbeddedEngineSmokeTest com.saifai.assistant.test/androidx.test.runner.AndroidJUnitRunner | tee /tmp/engine-test.txt
grep -q 'OK (1 test)' /tmp/engine-test.txt
adb shell pm list packages com.termux | grep -q 'package:com.termux'
