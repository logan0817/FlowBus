"""模拟 CI 设备，验证身份门禁、屏幕准备、诊断留存与原始失败传播。"""

import os
import json
from pathlib import Path
import subprocess
import tempfile
import unittest


class VerifyApi35Test(unittest.TestCase):
    def run_script(self, *, api="35", boot="1"):
        script = Path(__file__).with_name("run-api35.sh").resolve()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tools = root / "tools"
            tools.mkdir()
            adb = tools / "adb"
            adb.write_text('''#!/bin/sh
echo "$*" >> commands.txt
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
            gradle.write_text('''#!/bin/sh
test "$ANDROID_SERIAL" = emulator-5554 || exit 9
echo "$*" >> commands.txt
exit 42
''')
            gradle.chmod(0o755)
            result = subprocess.run(
                ["bash", str(script)], cwd=root,
                env=dict(os.environ, PATH=str(tools) + os.pathsep + os.environ["PATH"],
                         FLOWBUS_TEST_API=api, FLOWBUS_TEST_BOOT=boot),
                capture_output=True, text=True, timeout=10,
            )
            commands = (root / "commands.txt").read_text()
            device = json.loads((root / "build/api35-environment/device.json").read_text())
            self.assertEqual(device["ro.build.version.sdk"], api)
            self.assertEqual(device["sys.boot_completed"], boot)
            for filename in ("window.txt", "power.txt"):
                self.assertEqual((root / "build/api35-environment" / filename).read_text().strip(), "diagnostic")
            return result, commands

    def test_preparation_and_diagnostics_preserve_test_failure(self):
        result, commands = self.run_script()
        self.assertEqual(result.returncode, 42, result.stderr)
        self.assertLess(commands.index("getprop sys.boot_completed"), commands.index("keyevent 224"))
        self.assertLess(commands.index("keyevent 224"), commands.index("wm dismiss-keyguard"))
        self.assertLess(commands.index("wm dismiss-keyguard"), commands.index(":app:connectedReleaseAndroidTest"))

    def test_wrong_api_is_recorded_and_rejected_before_preparation_or_gradle(self):
        result, commands = self.run_script(api="24")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("keyevent", commands)
        self.assertNotIn("connected", commands)

    def test_incomplete_boot_is_recorded_and_rejected_before_preparation_or_gradle(self):
        result, commands = self.run_script(boot="0")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("keyevent", commands)
        self.assertNotIn("connected", commands)


if __name__ == "__main__":
    unittest.main()
