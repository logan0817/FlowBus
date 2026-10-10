#!/usr/bin/env python3
"""验证实际 navigation-compose NavHost 的 owner、转场阈值及 Activity 重建。"""

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import device_verification as verification

PHASES = (
    "home-active", "transition-owner-thresholds", "covered-home-does-not-receive",
    "pop-restores-sticky-without-normal-replay", "popped-owner-does-not-receive",
    "new-detail-owner-receives-retained-source", "activity-recreation-restores-detail-owner",
    "restored-back-stack-returns-home",
)


def main():
    verification.run_self_check(
        __doc__, package="com.logan.flowbus.composeconsumer", activity="NavHostConsumerActivity",
        tag="FlowBusNavHost", phases=PHASES, report_dir="build/navhost-device", timeout=70,
    )


if __name__ == "__main__":
    main()
