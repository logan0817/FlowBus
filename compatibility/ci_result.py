"""最终检查必须收到全部必需 job 的成功结果，包括完整 Kotlin 矩阵。"""
import json
import os

REQUIRED = {"prepare", "consumers", "kotlin", "api21", "api35"}


def require_success(needs):
    if set(needs) != REQUIRED or any(job["result"] != "success" for job in needs.values()):
        raise ValueError(f"Required verification jobs did not all succeed: {needs}")


if __name__ == "__main__":
    require_success(json.loads(os.environ["VERIFICATION_NEEDS"]))
    print("PASS all required verification jobs")
