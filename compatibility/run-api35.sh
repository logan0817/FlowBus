#!/usr/bin/env bash
# --prepare 在启动模拟器前构建并封存本轮 APK；默认模式串行执行设备门禁。
set -euo pipefail

if [ "${1:-}" = "--prepare" ] && [ "$#" -eq 1 ]; then
  python3 -B compatibility/candidate_bundle.py check
  candidate_args=("-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.verification.reuseCandidate=true --warning-mode=all)
  mkdir -p build/api35-apks
  ./gradlew :app:assembleDebug :app:assembleRelease :app:assembleReleaseAndroidTest \
    :library-android:assembleDebug :library-android:assembleDebugAndroidTest \
    verifyMavenLocalConsumer verifyMavenLocalComposeConsumer "${candidate_args[@]}"
  # 两个生成工程固定使用各自目录；设备阶段只使用此处保存的默认配置 APK。
  cp build/maven-local-consumer/app/build/outputs/apk/release/app-release-unsigned.apk build/api35-apks/android.apk
  cp build/maven-local-compose-consumer/app/build/outputs/apk/release/app-release-unsigned.apk build/api35-apks/compose.apk
  python3 -B compatibility/candidate_bundle.py check
  exit 0
fi
if [ "$#" -ne 0 ]; then
  echo "Usage: bash compatibility/run-api35.sh [--prepare]" >&2
  exit 2
fi

mkdir -p build/api35-environment
preserve_device_state() {
  local test_status=$?
  # 诊断失败不能覆盖原测试退出码。
  adb -s emulator-5554 shell dumpsys window > build/api35-environment/window.txt || true
  adb -s emulator-5554 shell dumpsys power > build/api35-environment/power.txt || true
  if adb -s emulator-5554 shell test -d /data/local/tmp/flowbus-ui-focus; then
    adb -s emulator-5554 pull /data/local/tmp/flowbus-ui-focus build/api35-environment/ || true
  fi
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
ANDROID_SERIAL=emulator-5554 ./gradlew :library-android:connectedDebugAndroidTest --rerun :app:connectedReleaseAndroidTest --rerun --warning-mode=all
# 只使用本轮本地测试签名；不读取或上传生产密钥。
FLOWBUS_TEST_KEYSTORE="$PWD/build/api35-signing/debug.keystore"
mkdir -p "$(dirname "$FLOWBUS_TEST_KEYSTORE")"
if [ ! -f "$FLOWBUS_TEST_KEYSTORE" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -noprompt -keystore "$FLOWBUS_TEST_KEYSTORE" \
    -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign \
  --ks "$FLOWBUS_TEST_KEYSTORE" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out "$PWD/build/android-consumer-api35.apk" \
  "$PWD/build/api35-apks/android.apk"
python3 -B compatibility/android-consumer/verify-device.py \
  --serial emulator-5554 --expected-api 35 --apk "$PWD/build/android-consumer-api35.apk" \
  --report-dir build/api35-consumers/android
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign \
  --ks "$FLOWBUS_TEST_KEYSTORE" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out "$PWD/build/compose-effects-api35.apk" \
  "$PWD/build/api35-apks/compose.apk"
python3 -B compatibility/compose-consumer/verify-effects-device.py \
  --serial emulator-5554 --expected-api 35 --apk "$PWD/build/compose-effects-api35.apk" \
  --report-dir build/api35-consumers/compose-effects
python3 -B compatibility/compose-consumer/verify-policies-device.py \
  --serial emulator-5554 --expected-api 35 --apk "$PWD/build/compose-effects-api35.apk" \
  --report-dir build/api35-consumers/compose-policies
