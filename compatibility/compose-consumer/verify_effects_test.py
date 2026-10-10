"""验证 effect 设备 driver 的本次运行匹配与失败判断；mock 不代替设备验收。"""

import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("compose_device", Path(__file__).with_name("verify-effects-device.py"))
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)

policy_spec = importlib.util.spec_from_file_location("compose_policy_device", Path(__file__).with_name("verify-policies-device.py"))
policy_driver = importlib.util.module_from_spec(policy_spec)
policy_spec.loader.exec_module(policy_driver)


class VerifyEffectsTest(unittest.TestCase):
    def test_device_result_boundaries(self):
        self.verify_driver(driver, "flowbus.compose.verifyEffects", "FlowBusCompose")

    def test_policy_result_boundaries(self):
        self.verify_driver(policy_driver, "flowbus.compose.verifyPolicies", "FlowBusComposePolicy", wrong_count=True)

    def verify_driver(self, selected_driver, mode, tag, wrong_count=False):
        scenarios = ("passed", "stale", "failed", "wrong-ui", "missing-phase", "wrong-api")
        if wrong_count:
            scenarios += ("wrong-count",)
        for scenario in scenarios:
            with self.subTest(scenario=scenario), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                apk = root / "signed.apk"
                apk.write_bytes(b"fixture")
                commands = []
                run_id = ""

                def fake_adb(command, **kwargs):
                    nonlocal run_id
                    commands.append(command)
                    args = command[3:]
                    if args == ["shell", "getprop", "ro.build.version.sdk"]:
                        return "37" if scenario == "wrong-api" else "24"
                    if args == ["shell", "getprop", "sys.boot_completed"]:
                        return "1"
                    if args[0] == "install":
                        return "Success"
                    if args[:3] == ["shell", "am", "start"]:
                        run_id = args[-1]
                        self.assertIn("com.logan.flowbus.composeconsumer/.ConsumerActivity", args)
                        self.assertIn(mode, args)
                        other_mode = "flowbus.compose.verifyEffects" if wrong_count else "flowbus.compose.verifyPolicies"
                        self.assertNotIn(other_mode, args)
                        return "Status: ok"
                    if args[0] == "logcat":
                        self.assertIn(tag + ":I", args)
                        if scenario == "failed":
                            return f"FAIL runId={run_id}"
                        value = "previous" if scenario == "stale" else run_id
                        count = len(selected_driver.PHASES) + int(scenario == "wrong-count")
                        return f"PASS API=24 checks={count} runId={value}"
                    if args[:2] == ["shell", "cat"]:
                        tree = ET.Element("hierarchy")
                        phases = selected_driver.PHASES[:-1] if scenario == "missing-phase" else selected_driver.PHASES
                        text = chr(10).join((f"PASS API=24 checks={len(selected_driver.PHASES)} runId={run_id}", *phases))
                        package = "other.application" if scenario == "wrong-ui" else "com.logan.flowbus.composeconsumer"
                        ET.SubElement(tree, "node", package=package, text=text)
                        return ET.tostring(tree, encoding="unicode")
                    if args[:3] in (["shell", "am", "force-stop"], ["shell", "uiautomator", "dump"]):
                        return ""
                    raise AssertionError(command)

                argv = [str(selected_driver.__file__), "--adb", "fake-adb", "--serial", "test-device",
                        "--apk", str(apk), "--expected-api", "24", "--report-dir", str(root / "reports")]
                with patch.object(sys, "argv", argv), patch.object(selected_driver.verification.subprocess, "check_output", fake_adb), \
                        patch.object(selected_driver.verification.time, "monotonic", side_effect=[0, 0, 41]), \
                        patch.object(selected_driver.verification.time, "sleep"), patch("builtins.print"):
                    if scenario == "passed":
                        selected_driver.main()
                    else:
                        expected = TimeoutError if scenario in ("stale", "wrong-count") else AssertionError
                        with self.assertRaises(expected):
                            selected_driver.main()
                self.assertEqual(len(list((root / "reports").glob("*/result.txt"))), int(scenario == "passed"))
                if scenario == "wrong-api":
                    self.assertFalse(any("install" in command for command in commands))


if __name__ == "__main__":
    unittest.main()
