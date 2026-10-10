"""实际执行 CI Shell 入口，检查复合命令与前置失败传播；不启动设备。"""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "run-api21.sh"


class VerifyCiScriptTest(unittest.TestCase):
    def run_script(self, api="21", boot="1", gradle_exit="42"):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tools = root / "tools"
            tools.mkdir()
            # 使用当前 Python，避免依赖测试机器的 PATH 中是否存在 python3。
            (tools / "python3").symlink_to(sys.executable)
            adb = tools / "adb"
            adb.write_text('''#!/bin/sh
test "$1" = "-s" && test "$2" = "emulator-5554" || exit 9
case "$5" in
  ro.build.version.sdk) echo "$TEST_DEVICE_API" ;;
  ro.build.version.release) echo 5.0 ;;
  ro.product.cpu.abi) echo x86 ;;
  sys.boot_completed) echo "$TEST_DEVICE_BOOT" ;;
  *) exit 8 ;;
esac
''')
            adb.chmod(0o755)
            gradle = root / "gradlew"
            gradle.write_text('''#!/bin/sh
test "$ANDROID_SERIAL" = "emulator-5554" || exit 9
echo "$*" > gradle-invocation.txt
exit "$TEST_GRADLE_EXIT"
''')
            gradle.chmod(0o755)
            (tools / "bin").mkdir()
            keytool = tools / "bin/keytool"
            keytool.write_text('''#!/bin/sh
exit 43
''')
            keytool.chmod(0o755)
            environment = dict(os.environ, PATH=str(tools) + os.pathsep + os.environ["PATH"],
                               TEST_DEVICE_API=api, TEST_DEVICE_BOOT=boot,
                               TEST_GRADLE_EXIT=gradle_exit, JAVA_HOME=str(tools))
            result = subprocess.run(["bash", str(SCRIPT)], cwd=root, env=environment,
                                    text=True, capture_output=True, timeout=10)
            properties = json.loads((root / "build/api21-environment/device.json").read_text())
            invocation = root / "gradle-invocation.txt"
            return result, properties, invocation.read_text() if invocation.exists() else None

    def test_valid_device_runs_heredoc_then_propagates_gradle_failure(self):
        result, properties, invocation = self.run_script()
        self.assertEqual(result.returncode, 42, result.stderr)
        self.assertEqual(properties["ro.build.version.sdk"], "21")
        self.assertEqual(properties["sys.boot_completed"], "1")
        self.assertEqual(invocation.strip(), ":library-android:connectedDebugAndroidTest --warning-mode=all")
        # Gradle 失败后不应继续调用签名或消费者命令。
        self.assertNotIn("apksigner", result.stderr)
        self.assertNotIn("ANDROID_HOME", result.stderr)

    def test_key_generation_failure_stops_before_signing(self):
        result, _, invocation = self.run_script(gradle_exit="0")
        self.assertEqual(result.returncode, 43, result.stderr)
        self.assertIsNotNone(invocation)
        self.assertNotIn("ANDROID_HOME", result.stderr)

    def test_wrong_api_stops_before_gradle_but_preserves_identity(self):
        result, properties, invocation = self.run_script(api="35")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("AssertionError", result.stderr)
        self.assertEqual(properties["ro.build.version.sdk"], "35")
        self.assertIsNone(invocation)

    def test_incomplete_boot_stops_before_gradle_but_preserves_identity(self):
        result, properties, invocation = self.run_script(boot="0")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("AssertionError", result.stderr)
        self.assertEqual(properties["sys.boot_completed"], "0")
        self.assertIsNone(invocation)


if __name__ == "__main__":
    unittest.main()
