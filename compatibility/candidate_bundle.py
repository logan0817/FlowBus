"""封存/校验 CI 候选仓库，绑定 checkout 提交及每一个 Maven 文件。"""

import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys


def snapshot(root):
    repository = root / "build/candidate-maven"
    files = sorted(path for path in repository.rglob("*") if path.is_file())
    if not files:
        raise ValueError("Candidate Maven repository is empty")
    if any(path.is_symlink() for path in repository.rglob("*")):
        raise ValueError("Candidate repository must not contain symlinks")
    return {
        "commit": subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=root, text=True
        ).strip(),
        "files": {
            path.relative_to(repository).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in files
        },
    }


def seal(root):
    manifest = root / "build/candidate-manifest.json"
    manifest.write_text(json.dumps(snapshot(root), indent=2, sort_keys=True) + "\n")


def check(root):
    manifest = root / "build/candidate-manifest.json"
    digest = hashlib.sha256(manifest.read_bytes()).hexdigest()
    producer_digest = os.environ.get("FLOWBUS_CANDIDATE_MANIFEST_SHA256")
    if producer_digest is not None and digest != producer_digest:
        raise ValueError("Candidate manifest SHA-256 differs from the prepare job output")
    expected = json.loads(manifest.read_text())
    if snapshot(root) != expected:
        raise ValueError("Candidate commit, file set or SHA-256 differs from the sealed bundle")
    print(f"PASS candidate {expected['commit']}: {len(expected['files'])} files; "
          f"manifest sha256={digest}")


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("seal", "check"):
        sys.exit("Usage: python3 -B compatibility/candidate_bundle.py seal|check (from repository root)")
    {"seal": seal, "check": check}[sys.argv[1]](Path.cwd())
