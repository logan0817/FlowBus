#!/usr/bin/env python3
"""验证真实 Navigation 返回栈 owner 的生命周期、取消、普通事件与 sticky。"""

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import device_verification as verification

PHASES = (
    "home-active", "navigate-stops-covered-home", "covered-home-does-not-receive",
    "pop-restores-sticky-without-normal-replay", "popped-owner-does-not-receive",
    "new-detail-owner-receives-retained-source", "back-dispatcher-returns-home",
)


def main():
    verification.run_self_check(
        __doc__, package="com.logan.flowbus.composeconsumer", activity="NavigationConsumerActivity",
        tag="FlowBusNavigation", phases=PHASES, report_dir="build/navigation-device",
    )


if __name__ == "__main__":
    main()
