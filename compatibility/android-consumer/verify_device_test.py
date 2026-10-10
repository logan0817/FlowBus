"""验证设备 driver 不会把旧日志、错误设备或其他前台应用误判为通过。"""

import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("consumer_device", Path(__file__).with_name("verify-device.py"))
consumer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(consumer)


class VerifyDeviceTest(unittest.TestCase):
    def run_driver(self, scenario):
        commands = []
        run_id = ""

        def fake_adb(command, **kwargs):
            nonlocal run_id
            commands.append(command)
            args = command[3:]
            if args == ["shell", "getprop", "ro.build.version.sdk"]:
                return "37" if scenario == "wrong-api" else "21"
            if args == ["shell", "getprop", "sys.boot_completed"]:
                return "1"
            if args[0] == "install":
                return "Success"
            if args[:3] == ["shell", "am", "force-stop"]:
                return ""
            if args[:3] == ["shell", "am", "start"]:
                run_id = args[-1]
                return "Status: ok"
            if args[0] == "logcat":
                self.assertFalse(any(value.startswith("--pid") for value in args))
                if scenario == "stale":
                    return "PASS API=21 checks=4 runId=previous-run"
                if scenario == "failed":
                    return f"FAIL runId={run_id} completed=legacy-android"
                return f"PASS API=21 checks=4 runId={run_id}"
            if args[:3] == ["shell", "uiautomator", "dump"]:
                self.assertEqual(args[-1], f"/data/local/tmp/flowbus-flowbusconsumer-{run_id}.xml")
                if scenario == "dump-command-failed":
                    raise consumer.verification.subprocess.CalledProcessError(1, command, output="dump command failed")
                if scenario == "dump-failed":
                    return "ERROR: could not get idle state."
                return "UI hierarchy dumped"
            if args[:2] == ["shell", "cat"]:
                self.assertEqual(args[-1], f"/data/local/tmp/flowbus-flowbusconsumer-{run_id}.xml")
                if scenario == "missing-ui":
                    return "cat: No such file or directory"
                root = ET.Element("hierarchy")
                text = chr(10).join([
                    f"PASS API=21 checks=4 runId={run_id}", "normal-sticky-scope",
                    "legacy-android", "owner-suspending", "explicit-lifecycle",
                ])
                package = "another.application" if scenario == "wrong-ui" else "com.logan.flowbus.consumer"
                ET.SubElement(root, "node", package=package, text=text)
                return ET.tostring(root, encoding="unicode")
            raise AssertionError(command)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "signed.apk"
            apk.write_bytes(b"test-fixture")
            argv = [str(consumer.__file__), "--adb", "fake-adb", "--serial", "test-device",
                    "--apk", str(apk), "--expected-api", "21", "--report-dir", str(root / "reports")]
            with patch.object(sys, "argv", argv), patch.object(consumer.verification.subprocess, "check_output", fake_adb), \
                    patch.object(consumer.verification.time, "monotonic", side_effect=[0, 0, 31]), \
                    patch.object(consumer.verification.time, "sleep"), patch("builtins.print"):
                if scenario == "passed":
                    consumer.main()
                    self.assertEqual(len(list((root / "reports").glob("*/result.txt"))), 1)
                else:
                    expected = (consumer.verification.subprocess.CalledProcessError if scenario == "dump-command-failed"
                                else TimeoutError if scenario == "stale" else AssertionError)
                    with self.assertRaises(expected):
                        consumer.main()
                    self.assertEqual(list((root / "reports").glob("*/result.txt")), [])
            if scenario == "wrong-api":
                self.assertFalse(any("install" in command for command in commands))
            if scenario in ("dump-failed", "dump-command-failed"):
                self.assertFalse(any("cat" in command for command in commands))
                self.assertEqual(len(list((root / "reports").glob("*/ui-dump.txt"))), 1)

    def test_current_pass_requires_matching_ui(self):
        self.run_driver("passed")

    def test_previous_pass_cannot_satisfy_current_run(self):
        self.run_driver("stale")

    def test_current_failure_does_not_create_success_report(self):
        self.run_driver("failed")

    def test_other_foreground_app_cannot_satisfy_ui_check(self):
        self.run_driver("wrong-ui")

    def test_wrong_api_is_rejected_before_install(self):
        self.run_driver("wrong-api")

    def test_ui_dump_error_does_not_read_or_accept_a_hierarchy(self):
        self.run_driver("dump-failed")

    def test_missing_hierarchy_does_not_create_success_report(self):
        self.run_driver("missing-ui")

    def test_ui_dump_command_failure_preserves_output(self):
        self.run_driver("dump-command-failed")


if __name__ == "__main__":
    unittest.main()
