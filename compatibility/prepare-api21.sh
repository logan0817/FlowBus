#!/usr/bin/env bash
# 先完成全部构建，再启动模拟器；每次切换配置后立即复制 APK，避免固定目录互相覆盖。
set -euo pipefail
candidate_args=("-Dmaven.repo.local=$PWD/build/candidate-maven" -Pflowbus.verification.reuseCandidate=true --warning-mode=all)
# 保留原 CI 在 compileSdk 33 检查后交给设备的普通 Android 配置；Compose 仍用自身固定基线。
mkdir -p build/api21-apks
./gradlew :library-android:assembleDebug :library-android:assembleDebugAndroidTest \
  verifyMavenLocalConsumer verifyMavenLocalComposeConsumer "${candidate_args[@]}" \
  -Pflowbus.consumer.kotlinVersion=1.8.22 -Pflowbus.consumer.compileSdk=33
cp build/maven-local-consumer/app/build/outputs/apk/release/app-release-unsigned.apk build/api21-apks/android.apk
cp build/maven-local-compose-consumer/app/build/outputs/apk/release/app-release-unsigned.apk build/api21-apks/compose.apk
./gradlew verifyMavenLocalComposeConsumer "${candidate_args[@]}" -Pflowbus.consumer.navigationVersion=2.4.1
cp build/maven-local-compose-consumer/app/build/outputs/apk/release/app-release-unsigned.apk build/api21-apks/navigation.apk
./gradlew verifyMavenLocalComposeConsumer "${candidate_args[@]}" \
  -Pflowbus.consumer.navigationVersion=2.4.1 -Pflowbus.consumer.navHost=true
cp build/maven-local-compose-consumer/app/build/outputs/apk/release/app-release-unsigned.apk build/api21-apks/navhost.apk
python3 -B compatibility/candidate_bundle.py check
