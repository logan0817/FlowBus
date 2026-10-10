#!/usr/bin/env python3
"""在指定设备验收真实 Maven/R8 Compose effect 的替换、取消与异常行为。"""

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import device_verification as verification

PHASES = (
    "initial-replay", "source-replaced", "key-replaced", "owner-replaced",
    "min-state-replaced", "composition-disposed-restored",
    "lifecycle-stopped-restarted", "callback-error-continued", "destroyed-source-retained",
)


def main():
    verification.run_self_check(
        __doc__, package="com.logan.flowbus.composeconsumer", activity="ConsumerActivity",
        tag="FlowBusCompose", report_dir="build/compose-effects-device", phases=PHASES,
        extras=("--ez", "flowbus.compose.verifyEffects", "true"),
    )


if __name__ == "__main__":
    main()
