"""导航设备协议集成回归；模拟 ADB 不代替真实返回栈设备验证。"""

import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("navigation_device", Path(__file__).with_name("verify-navigation-device.py"))
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)

host_spec = importlib.util.spec_from_file_location(
    "navhost_device", Path(__file__).resolve().parents[1] / "navhost-consumer/verify-navhost-device.py"
)
host_driver = importlib.util.module_from_spec(host_spec)
host_spec.loader.exec_module(host_driver)


class VerifyNavigationTest(unittest.TestCase):
    def test_navigation_protocol_cannot_use_other_fixture_result(self):
        self.verify_driver(driver, "NavigationConsumerActivity", "FlowBusNavigation", 40)

    def test_navhost_protocol_with_recreation(self):
        self.verify_driver(host_driver, "NavHostConsumerActivity", "FlowBusNavHost", 70,
                           additional=("stale", "failed", "wrong-ui", "wrong-api"))

    def verify_driver(self, selected_driver, activity, tag, timeout, additional=()):
        for scenario in ("passed", "missing-phase", "wrong-count", *additional):
            with self.subTest(scenario=scenario), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                apk = root / "signed.apk"
                apk.write_bytes(b"fixture")
                run_id = ""
                commands = []

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
                        self.assertIn("com.logan.flowbus.composeconsumer/." + activity, args)
                        self.assertNotIn("flowbus.compose.verifyEffects", args)
                        self.assertNotIn("flowbus.compose.verifyPolicies", args)
                        run_id = args[-1]
                        return "Status: ok"
                    if args[0] == "logcat":
                        self.assertIn(tag + ":I", args)
                        if scenario == "failed":
                            return f"FAIL runId={run_id}"
                        count = len(selected_driver.PHASES) + int(scenario == "wrong-count")
                        value = "previous" if scenario == "stale" else run_id
                        return f"PASS API=24 checks={count} runId={value}"
                    if args[:2] == ["shell", "cat"]:
                        tree = ET.Element("hierarchy")
                        phases = selected_driver.PHASES[:-1] if scenario == "missing-phase" else selected_driver.PHASES
                        package = "other.application" if scenario == "wrong-ui" else "com.logan.flowbus.composeconsumer"
                        ET.SubElement(tree, "node", package=package,
                                      text=chr(10).join((f"PASS API=24 checks={len(selected_driver.PHASES)} runId={run_id}", *phases)))
                        return ET.tostring(tree, encoding="unicode")
                    if args[:3] in (["shell", "am", "force-stop"], ["shell", "uiautomator", "dump"]):
                        return ""
                    raise AssertionError(command)

                argv = [str(selected_driver.__file__), "--adb", "fake-adb", "--serial", "test-device",
                        "--apk", str(apk), "--expected-api", "24", "--report-dir", str(root / "reports")]
                with patch.object(sys, "argv", argv), patch.object(selected_driver.verification.subprocess, "check_output", fake_adb), \
                        patch.object(selected_driver.verification.time, "monotonic", side_effect=[0, 0, timeout + 1]), \
                        patch.object(selected_driver.verification.time, "sleep"), patch("builtins.print"):
                    if scenario == "passed":
                        selected_driver.main()
                    else:
                        expected = TimeoutError if scenario in ("wrong-count", "stale") else AssertionError
                        with self.assertRaises(expected):
                            selected_driver.main()
                self.assertEqual(len(list((root / "reports").glob("*/result.txt"))), int(scenario == "passed"))
                if scenario == "wrong-api":
                    self.assertFalse(any("install" in command for command in commands))


if __name__ == "__main__":
    unittest.main()
