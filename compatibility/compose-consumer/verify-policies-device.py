#!/usr/bin/env python3
"""验证真实 Compose runtime 的错误策略及受控非协作回调取消边界。"""

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import device_verification as verification

PHASES = (
    "default-rethrow-terminates-source-retained", "ignore-logs-and-continues",
    "custom-handler-context-and-continue", "legacy-handler-continues",
    "handler-failure-propagates", "cancellation-bypasses-error-handler",
    "noncancellable-restart-waits-for-callback", "blocking-worker-restart-waits-for-callback",
)


def main():
    verification.run_self_check(
        __doc__, package="com.logan.flowbus.composeconsumer", activity="ConsumerActivity",
        tag="FlowBusComposePolicy", report_dir="build/compose-policies-device", phases=PHASES,
        extras=("--ez", "flowbus.compose.verifyPolicies", "true"),
    )


if __name__ == "__main__":
    main()
