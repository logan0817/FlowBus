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
python3 - <<'PY'
import json
from pathlib import Path
import subprocess
properties = {}
for name in ("ro.build.version.sdk", "ro.build.version.release", "ro.product.cpu.abi", "sys.boot_completed"):
    properties[name] = subprocess.check_output(
        ["adb", "-s", "emulator-5554", "shell", "getprop", name], text=True
    ).strip()
Path("build/api35-environment/device.json").write_text(json.dumps(properties, indent=2))
assert properties["ro.build.version.sdk"] == "35", properties
assert properties["sys.boot_completed"] == "1", properties
PY
adb -s emulator-5554 shell input keyevent 224
adb -s emulator-5554 shell wm dismiss-keyguard
adb -s emulator-5554 shell settings put global window_animation_scale 0
adb -s emulator-5554 shell settings put global transition_animation_scale 0
adb -s emulator-5554 shell settings put global animator_duration_scale 0
adb -s emulator-5554 shell settings get global window_animation_scale
adb -s emulator-5554 shell settings get global transition_animation_scale
adb -s emulator-5554 shell settings get global animator_duration_scale
ANDROID_SERIAL=emulator-5554 ./gradlew :library-android:connectedDebugAndroidTest :app:connectedReleaseAndroidTest --rerun-tasks --warning-mode=all
