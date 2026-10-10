"""模拟 CI 设备，验证屏幕准备、诊断留存与原始测试失败传播。"""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class VerifyApi35Test(unittest.TestCase):
    def test_preparation_and_diagnostics_preserve_test_failure(self):
        script = Path(__file__).with_name("run-api35.sh").resolve()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tools = root / "tools"
            tools.mkdir()
            adb = tools / "adb"
            adb.write_text('''#!/bin/sh
echo "$*" >> commands.txt
echo diagnostic
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
                env=dict(os.environ, PATH=str(tools) + os.pathsep + os.environ["PATH"]),
                capture_output=True, text=True, timeout=10,
            )
            self.assertEqual(result.returncode, 42, result.stderr)
            commands = (root / "commands.txt").read_text()
            self.assertLess(commands.index("keyevent 224"), commands.index("wm dismiss-keyguard"))
            self.assertLess(commands.index("wm dismiss-keyguard"), commands.index(":app:connectedReleaseAndroidTest"))
            for filename in ("window.txt", "power.txt"):
                self.assertEqual((root / "build/api35-environment" / filename).read_text().strip(), "diagnostic")


if __name__ == "__main__":
    unittest.main()
