#!/usr/bin/env python3
"""验证指定设备上的真实 Maven/R8 consumer；不安装 SDK 或创建模拟器。"""

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import device_verification as verification


def main():
    verification.run_self_check(
        __doc__, package="com.logan.flowbus.consumer", activity="ConsumerActivity",
        tag="FlowBusConsumer", timeout=30, report_dir="build/android-consumer-device",
        phases=("normal-sticky-scope", "legacy-android", "owner-suspending", "explicit-lifecycle", "channel-config", "binary-1.1.0-android"),
    )


if __name__ == "__main__":
    main()
