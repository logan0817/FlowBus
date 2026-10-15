"""执行 APK 准备脚本，验证固定生成目录切换后仍保留正确的四个消费者。"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("prepare-api21.sh").resolve()


class PrepareApi21Test(unittest.TestCase):
    def run_preparation(self, root, fail_at="0"):
        gradle = root / "gradlew"
        gradle.write_text('''#!/usr/bin/env python3
import os
from pathlib import Path
import sys
log = Path("calls.txt")
calls = log.read_text().splitlines() if log.exists() else []
calls.append(" ".join(sys.argv[1:]))
log.write_text("\\n".join(calls) + "\\n")
if len(calls) == int(os.environ["FAIL_AT"]):
    sys.exit(42)
variant = "navhost" if "-Pflowbus.consumer.navHost=true" in sys.argv else (
    "navigation" if "-Pflowbus.consumer.navigationVersion=2.4.1" in sys.argv else "compose")
for consumer, content in (("consumer", "android"), ("compose-consumer", variant)):
    apk = Path("build/maven-local-" + consumer + "/app/build/outputs/apk/release/app-release-unsigned.apk")
    apk.parent.mkdir(parents=True, exist_ok=True)
    apk.write_text(content)
''')
        gradle.chmod(0o755)
        compatibility = root / "compatibility"
        compatibility.mkdir()
        (compatibility / "candidate_bundle.py").write_text(
            'import sys\nassert sys.argv[1:] == ["check"]\n'
        )
        return subprocess.run(["bash", str(SCRIPT)], cwd=root,
                              env=dict(os.environ, FAIL_AT=fail_at),
                              text=True, capture_output=True, timeout=10)

    def test_each_apk_matches_its_configuration_and_candidate_is_reused(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            result = self.run_preparation(root)
            self.assertEqual(result.returncode, 0, result.stderr)
            for variant in ("android", "compose", "navigation", "navhost"):
                self.assertEqual((root / f"build/api21-apks/{variant}.apk").read_text(), variant)
            calls = (root / "calls.txt").read_text().splitlines()
            self.assertEqual(len(calls), 3)
            self.assertIn("-Pflowbus.consumer.kotlinVersion=1.8.22", calls[0])
            self.assertIn("-Pflowbus.consumer.compileSdk=33", calls[0])
            for call in calls:
                self.assertIn("-Pflowbus.verification.reuseCandidate=true", call)
                self.assertIn(f"-Dmaven.repo.local={root.resolve()}/build/candidate-maven", call)

    def test_build_failure_stops_before_next_variant(self):
        for stage in (1, 2, 3):
            with self.subTest(stage=stage), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                result = self.run_preparation(root, str(stage))
                self.assertEqual(result.returncode, 42, result.stderr)
                self.assertEqual(len((root / "calls.txt").read_text().splitlines()), stage)
                self.assertFalse((root / "build/api21-apks/navhost.apk").exists())


if __name__ == "__main__":
    unittest.main()
