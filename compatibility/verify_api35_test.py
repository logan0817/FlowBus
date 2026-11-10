"""模拟 CI 准备与设备，验证候选复用、串行消费者、诊断和原始失败传播。"""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class VerifyApi35Test(unittest.TestCase):
    def run_script(self, *, api="35", boot="1", prepare=False, connected_exit="42",
                   prepare_exit="0", failed_driver="", signing_exit="0", candidate_exit="0"):
        script = Path(__file__).with_name("run-api35.sh").resolve()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tools = root / "tools"
            tools.mkdir()
            adb = tools / "adb"
            adb.write_text('''#!/bin/sh
echo "adb $*" >> commands.txt
case "$*" in
  *"getprop ro.build.version.sdk") echo "$FLOWBUS_TEST_API" ;;
  *"getprop sys.boot_completed") echo "$FLOWBUS_TEST_BOOT" ;;
  *"getprop ro.build.version.release") echo 15 ;;
  *"getprop ro.product.cpu.abi") echo x86_64 ;;
  *) echo diagnostic ;;
esac
''')
            adb.chmod(0o755)
            gradle = root / "gradlew"
            gradle.write_text('''#!/usr/bin/env python3
import os
from pathlib import Path
import sys
with Path("commands.txt").open("a") as log:
    log.write("gradle " + " ".join(sys.argv[1:]) + "\\n")
if ":library-android:connectedDebugAndroidTest" in sys.argv:
    assert os.environ["ANDROID_SERIAL"] == "emulator-5554"
    sys.exit(int(os.environ["FLOWBUS_TEST_CONNECTED_EXIT"]))
if os.environ["FLOWBUS_TEST_PREPARE_EXIT"] != "0":
    sys.exit(int(os.environ["FLOWBUS_TEST_PREPARE_EXIT"]))
for consumer, content in (("consumer", "android"), ("compose-consumer", "compose")):
    apk = Path("build/maven-local-" + consumer + "/app/build/outputs/apk/release/app-release-unsigned.apk")
    apk.parent.mkdir(parents=True, exist_ok=True)
    apk.write_text(content)
''')
            gradle.chmod(0o755)
            java_home = root / "jdk"
            (java_home / "bin").mkdir(parents=True)
            keytool = java_home / "bin/keytool"
            keytool.write_text('''#!/usr/bin/env python3
from pathlib import Path
import sys
with Path("commands.txt").open("a") as log:
    log.write("keytool " + " ".join(sys.argv[1:]) + "\\n")
Path(sys.argv[sys.argv.index("-keystore") + 1]).write_text("local test key")
''')
            keytool.chmod(0o755)
            android_home = root / "sdk"
            signer = android_home / "build-tools/35.0.0/apksigner"
            signer.parent.mkdir(parents=True)
            signer.write_text('''#!/usr/bin/env python3
import os
from pathlib import Path
import sys
with Path("commands.txt").open("a") as log:
    log.write("apksigner " + " ".join(sys.argv[1:]) + "\\n")
if os.environ["FLOWBUS_TEST_SIGNING_EXIT"] != "0":
    sys.exit(int(os.environ["FLOWBUS_TEST_SIGNING_EXIT"]))
Path(sys.argv[sys.argv.index("--out") + 1]).write_bytes(Path(sys.argv[-1]).read_bytes())
''')
            signer.chmod(0o755)
            compatibility = root / "compatibility"
            compatibility.mkdir()
            (compatibility / "candidate_bundle.py").write_text('''import os
from pathlib import Path
import sys
assert sys.argv[1:] == ["check"]
with Path("commands.txt").open("a") as log:
    log.write("candidate check\\n")
sys.exit(int(os.environ["FLOWBUS_TEST_CANDIDATE_EXIT"]))
''')
            for consumer, filename, role in (
                ("android-consumer", "verify-device.py", "android"),
                ("compose-consumer", "verify-effects-device.py", "effects"),
                ("compose-consumer", "verify-policies-device.py", "policies"),
            ):
                driver = compatibility / consumer / filename
                driver.parent.mkdir(exist_ok=True)
                driver.write_text(f'''import os
from pathlib import Path
import sys
role = {role!r}
args = sys.argv[1:]
with Path("commands.txt").open("a") as log:
    log.write("driver " + role + " " + " ".join(args) + "\\n")
assert args[args.index("--serial") + 1] == "emulator-5554"
assert args[args.index("--expected-api") + 1] == "35"
apk = Path(args[args.index("--apk") + 1])
assert apk.read_text() == ("android" if role == "android" else "compose")
report = Path(args[args.index("--report-dir") + 1])
report.mkdir(parents=True)
(report / "current.log").write_text("diagnostic")
if os.environ["FLOWBUS_TEST_FAILED_DRIVER"] == role:
    sys.exit(43)
''')
            if not prepare:
                apks = root / "build/api35-apks"
                apks.mkdir(parents=True)
                for variant in ("android", "compose"):
                    (apks / f"{variant}.apk").write_text(variant)
            result = subprocess.run(
                ["bash", str(script), *(["--prepare"] if prepare else [])], cwd=root,
                env=dict(os.environ, PATH=str(tools) + os.pathsep + os.environ["PATH"],
                         JAVA_HOME=str(java_home), ANDROID_HOME=str(android_home),
                         FLOWBUS_TEST_API=api, FLOWBUS_TEST_BOOT=boot,
                         FLOWBUS_TEST_CONNECTED_EXIT=connected_exit,
                         FLOWBUS_TEST_PREPARE_EXIT=prepare_exit,
                         FLOWBUS_TEST_FAILED_DRIVER=failed_driver,
                         FLOWBUS_TEST_SIGNING_EXIT=signing_exit,
                         FLOWBUS_TEST_CANDIDATE_EXIT=candidate_exit),
                capture_output=True, text=True, timeout=10,
            )
            commands = (root / "commands.txt").read_text()
            if not prepare:
                device = json.loads((root / "build/api35-environment/device.json").read_text())
                self.assertEqual(device["ro.build.version.sdk"], api)
                self.assertEqual(device["sys.boot_completed"], boot)
                for filename in ("window.txt", "power.txt"):
                    self.assertEqual((root / "build/api35-environment" / filename).read_text().strip(), "diagnostic")
            files = {str(path.relative_to(root)): path.read_text() for path in (root / "build").rglob("*")
                     if path.is_file()}
            return result, commands, files

    def test_preparation_and_diagnostics_preserve_test_failure(self):
        result, commands, _ = self.run_script()
        self.assertEqual(result.returncode, 42, result.stderr)
        self.assertLess(commands.index("getprop sys.boot_completed"), commands.index("keyevent 224"))
        self.assertLess(commands.index("keyevent 224"), commands.index("wm dismiss-keyguard"))
        self.assertLess(commands.index("wm dismiss-keyguard"), commands.index(":app:connectedReleaseAndroidTest"))
        self.assertNotIn("apksigner", commands)
        self.assertNotIn("driver ", commands)

    def test_wrong_api_is_recorded_and_rejected_before_preparation_or_gradle(self):
        result, commands, _ = self.run_script(api="24")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("keyevent", commands)
        self.assertNotIn("connected", commands)
        self.assertNotIn("driver ", commands)

    def test_incomplete_boot_is_recorded_and_rejected_before_preparation_or_gradle(self):
        result, commands, _ = self.run_script(boot="0")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("keyevent", commands)
        self.assertNotIn("connected", commands)
        self.assertNotIn("driver ", commands)

    def test_prepare_reuses_candidate_and_saves_default_consumers_before_device(self):
        result, commands, files = self.run_script(prepare=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(files["build/api35-apks/android.apk"], "android")
        self.assertEqual(files["build/api35-apks/compose.apk"], "compose")
        calls = commands.splitlines()
        self.assertEqual(len(calls), 3)
        self.assertEqual(calls[0], "candidate check")
        self.assertEqual(calls[2], "candidate check")
        self.assertIn("-Pflowbus.verification.reuseCandidate=true", calls[1])
        self.assertIn("-Dmaven.repo.local=", calls[1])
        self.assertIn("/build/candidate-maven", calls[1])
        self.assertIn(":app:assembleReleaseAndroidTest", calls[1])
        self.assertIn(":library-android:assembleDebugAndroidTest", calls[1])
        self.assertIn("verifyMavenLocalConsumer verifyMavenLocalComposeConsumer", calls[1])
        self.assertNotIn("navigationVersion", calls[1])
        self.assertNotIn("adb ", commands)

    def test_prepare_failure_never_continues_to_device_or_successful_seal(self):
        for options, expected_exit in (({"prepare_exit": "42"}, 42), ({"candidate_exit": "44"}, 44)):
            with self.subTest(options=options):
                result, commands, files = self.run_script(prepare=True, **options)
                self.assertEqual(result.returncode, expected_exit, result.stderr)
                self.assertEqual(commands.count("candidate check"), 1)
                self.assertNotIn("build/api35-apks/android.apk", files)
                self.assertNotIn("adb ", commands)

    def test_consumers_are_signed_and_run_serially_after_instrumentation(self):
        result, commands, files = self.run_script(connected_exit="0")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertLess(commands.index(":app:connectedReleaseAndroidTest"), commands.index("keytool "))
        self.assertLess(commands.index("apksigner "), commands.index("driver android "))
        self.assertLess(commands.index("driver android "), commands.index("driver effects "))
        self.assertLess(commands.index("driver effects "), commands.index("driver policies "))
        self.assertIn("build/api35-signing/debug.keystore", commands)
        for name in ("android", "compose-effects", "compose-policies"):
            self.assertEqual(files[f"build/api35-consumers/{name}/current.log"], "diagnostic")
        self.assertEqual(files["build/android-consumer-api35.apk"], "android")
        self.assertEqual(files["build/compose-effects-api35.apk"], "compose")
        self.assertIn("dumpsys window", commands)
        self.assertIn("dumpsys power", commands)

    def test_consumer_failures_keep_original_status_and_stop_later_drivers(self):
        roles = ("android", "effects", "policies")
        for index, role in enumerate(roles):
            with self.subTest(role=role):
                result, commands, files = self.run_script(connected_exit="0", failed_driver=role)
                self.assertEqual(result.returncode, 43, result.stderr)
                for later in roles[index + 1:]:
                    self.assertNotIn(f"driver {later} ", commands)
                self.assertTrue(any(name.startswith("build/api35-consumers/") for name in files))
                self.assertIn("dumpsys window", commands)
                self.assertIn("dumpsys power", commands)

    def test_signing_failure_is_not_reported_as_success(self):
        result, commands, _ = self.run_script(connected_exit="0", signing_exit="45")
        self.assertEqual(result.returncode, 45, result.stderr)
        self.assertNotIn("driver ", commands)


if __name__ == "__main__":
    unittest.main()
