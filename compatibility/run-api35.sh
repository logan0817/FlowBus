#!/usr/bin/env bash
# API 35 UI 门禁显式准备屏幕状态，并在模拟器退出前留下焦点诊断。
set -euo pipefail
mkdir -p build/api35-environment
preserve_device_state() {
  local test_status=$?
  # 诊断失败不能覆盖原测试退出码。
  adb -s emulator-5554 shell dumpsys window > build/api35-environment/window.txt || true
  adb -s emulator-5554 shell dumpsys power > build/api35-environment/power.txt || true
  exit "$test_status"
}
trap preserve_device_state EXIT
adb -s emulator-5554 shell input keyevent 224
adb -s emulator-5554 shell wm dismiss-keyguard
adb -s emulator-5554 shell settings put global window_animation_scale 0
adb -s emulator-5554 shell settings put global transition_animation_scale 0
adb -s emulator-5554 shell settings put global animator_duration_scale 0
adb -s emulator-5554 shell settings get global window_animation_scale
adb -s emulator-5554 shell settings get global transition_animation_scale
adb -s emulator-5554 shell settings get global animator_duration_scale
ANDROID_SERIAL=emulator-5554 ./gradlew :library-android:connectedDebugAndroidTest :app:connectedReleaseAndroidTest --rerun-tasks --warning-mode=all
