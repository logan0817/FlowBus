#!/usr/bin/env bash
# emulator-runner 按行执行 script，复合命令必须由独立 Shell 脚本承载。
# 从仓库根目录调用；任一步骤失败立即停止，报告由 CI 的 always 步骤保存。
set -euo pipefail

mkdir -p build/api21-environment
python3 - <<'PY'
import json
from pathlib import Path
import subprocess
properties = {}
for name in ("ro.build.version.sdk", "ro.build.version.release", "ro.product.cpu.abi", "sys.boot_completed"):
    properties[name] = subprocess.check_output(
        ["adb", "-s", "emulator-5554", "shell", "getprop", name], text=True
    ).strip()
Path("build/api21-environment/device.json").write_text(json.dumps(properties, indent=2))
assert properties["ro.build.version.sdk"] == "21", properties
assert properties["sys.boot_completed"] == "1", properties
PY
ANDROID_SERIAL=emulator-5554 ./gradlew :library-android:connectedDebugAndroidTest --warning-mode=all
# CI 不依赖用户目录中的 debug.keystore；只生成本地、未上传的测试签名。
FLOWBUS_TEST_KEYSTORE="$PWD/build/api21-signing/debug.keystore"
mkdir -p "$(dirname "$FLOWBUS_TEST_KEYSTORE")"
if [ ! -f "$FLOWBUS_TEST_KEYSTORE" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -noprompt -keystore "$FLOWBUS_TEST_KEYSTORE" \
    -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign \
  --ks "$FLOWBUS_TEST_KEYSTORE" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out "$PWD/build/android-consumer-api21.apk" \
  "$PWD/build/maven-local-consumer/app/build/outputs/apk/release/app-release-unsigned.apk"
python3 -B compatibility/android-consumer/verify-device.py \
  --serial emulator-5554 --expected-api 21 --apk "$PWD/build/android-consumer-api21.apk"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign \
  --ks "$FLOWBUS_TEST_KEYSTORE" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out "$PWD/build/compose-effects-api21.apk" \
  "$PWD/build/maven-local-compose-consumer/app/build/outputs/apk/release/app-release-unsigned.apk"
python3 -B compatibility/compose-consumer/verify-effects-device.py \
  --serial emulator-5554 --expected-api 21 --apk "$PWD/build/compose-effects-api21.apk"
python3 -B compatibility/compose-consumer/verify-policies-device.py \
  --serial emulator-5554 --expected-api 21 --apk "$PWD/build/compose-effects-api21.apk"
./gradlew verifyMavenLocalComposeConsumer \
  "-Dmaven.repo.local=$PWD/build/candidate-maven" \
  -Pflowbus.consumer.navigationVersion=2.4.1 --warning-mode=all
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign \
  --ks "$FLOWBUS_TEST_KEYSTORE" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out "$PWD/build/navigation-api21.apk" \
  "$PWD/build/maven-local-compose-consumer/app/build/outputs/apk/release/app-release-unsigned.apk"
python3 -B compatibility/navigation-consumer/verify-navigation-device.py \
  --serial emulator-5554 --expected-api 21 --apk "$PWD/build/navigation-api21.apk"
./gradlew verifyMavenLocalComposeConsumer \
  "-Dmaven.repo.local=$PWD/build/candidate-maven" \
  -Pflowbus.consumer.navigationVersion=2.4.1 -Pflowbus.consumer.navHost=true --warning-mode=all
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign \
  --ks "$FLOWBUS_TEST_KEYSTORE" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out "$PWD/build/navhost-api21.apk" \
  "$PWD/build/maven-local-compose-consumer/app/build/outputs/apk/release/app-release-unsigned.apk"
python3 -B compatibility/navhost-consumer/verify-navhost-device.py \
  --serial emulator-5554 --expected-api 21 --apk "$PWD/build/navhost-api21.apk"
