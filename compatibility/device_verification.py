"""消费者设备自检共用协议；每次冷启动必须匹配本次日志、同包 UI 与全部阶段。"""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET


def run_self_check(description, *, package, activity, tag, phases, report_dir, extras=(), timeout=40):
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("--adb", default=str(Path(sdk) / "platform-tools/adb") if sdk else shutil.which("adb"))
    parser.add_argument("--serial", required=True)
    parser.add_argument("--expected-api", required=True, type=int)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--report-dir", type=Path, default=Path(report_dir))
    args = parser.parse_args()
    if not args.adb or not args.apk.is_file():
        parser.error("需要可用的 adb 与已经完成测试签名的 release APK")

    def adb(*command):
        return subprocess.check_output(
            [args.adb, "-s", args.serial, *command], text=True,
            timeout=max(30, timeout), stderr=subprocess.STDOUT,
        )

    api = int(adb("shell", "getprop", "ro.build.version.sdk").strip())
    if api != args.expected_api:
        raise AssertionError(f"期望 API {args.expected_api}，实际 API {api}")
    if adb("shell", "getprop", "sys.boot_completed").strip() != "1":
        raise AssertionError("设备尚未完成启动")
    run_id = str(uuid.uuid4())
    report = args.report_dir / run_id
    report.mkdir(parents=True)
    install = adb("install", "-r", str(args.apk.resolve()))
    if "Success" not in install:
        raise AssertionError(install)
    adb("shell", "am", "force-stop", package)
    start = adb(
        "shell", "am", "start", "-W", "-n", package + "/." + activity,
        *extras, "--es", "flowbus.consumer.runId", run_id,
    )
    (report / "start.txt").write_text(start)
    expected = f"PASS API={api} checks={len(phases)} runId={run_id}"
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        # API 21 支持按 tag 读取；不依赖 --pid，也不清空其他任务的 logcat。
        logs = adb("logcat", "-d", "-v", "brief", "-s", tag + ":I", "AndroidRuntime:E")
        (report / "current.log").write_text(logs)
        if f"FAIL runId={run_id}" in logs:
            raise AssertionError(f"本次设备自检失败，证据见 {report}")
        if expected in logs:
            ui_path = "/sdcard/flowbus-" + tag.lower() + "-current.xml"
            adb("shell", "uiautomator", "dump", ui_path)
            ui = adb("shell", "cat", ui_path)
            (report / "current-ui.xml").write_text(ui)
            texts = [node.get("text", "") for node in ET.fromstring(ui).iter("node") if node.get("package") == package]
            if not all(any(value in text for text in texts) for value in (expected, *phases)):
                raise AssertionError(f"本次日志与前台 UI 不一致，证据见 {report}")
            (report / "result.txt").write_text(expected + os.linesep)
            print(f"{expected}; evidence={report}")
            return
        time.sleep(0.25)
    raise TimeoutError(f"未收到本次完整自检结果，证据见 {report}")
