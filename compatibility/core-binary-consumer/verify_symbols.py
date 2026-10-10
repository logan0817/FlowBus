"""核对 JAR/AAR 稳定具名类的公开/受保护 JVM 成员描述符，不替代完整 ABI 或行为认证。"""
import argparse
from contextlib import contextmanager
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


def class_names(jar):
    with zipfile.ZipFile(jar) as archive:
        return sorted(
            name[:-6].replace("/", ".") for name in archive.namelist()
            if name.endswith(".class") and not re.search(r"\$\d+(?:\$|\.|$)", name)
        )


def parse_members(output):
    members, excluded = {}, []
    current = None
    in_class_body = False
    lines = output.splitlines()
    for index, line in enumerate(lines):
        declaration = re.match(r"^(?:public |protected )?.*?\b(?:class|interface|enum)\s+(\S+)", line)
        if declaration and not line.startswith(" "):
            current = declaration.group(1).split("<")[0]
            members[current] = set()
        if line == "{":
            in_class_body = True
        elif line == "}":
            in_class_body = False
        if current is None or not in_class_body or not line.startswith(("  public ", "  protected ")):
            continue
        descriptor_line = lines[index + 1].strip()
        flags = lines[index + 2].strip()
        if not descriptor_line.startswith("descriptor:"):
            raise ValueError("Missing JVM descriptor for " + line)
        member = line.split("(")[0].split()[-1].rstrip(";")
        if member == current:
            member = "<init>"
        # 私有实现的编译器桥接可随实现变化；保留 $default、bridge 与 PublishedApi 符号。
        if member.startswith("access$") and "ACC_SYNTHETIC" in flags:
            excluded.append([current, member])
            continue
        descriptor = descriptor_line.removeprefix("descriptor:").strip()
        members[current].add((member, descriptor, "ACC_STATIC" in flags))
    if not members:
        raise ValueError("No classes found in javap output")
    return members, excluded


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


@contextmanager
def classes_jar(artifact):
    artifact = Path(artifact)
    if artifact.suffix.lower() != ".aar":
        yield artifact
        return
    # 只读取 classes.jar，不按归档内路径解压其他条目。
    with zipfile.ZipFile(artifact) as archive, tempfile.TemporaryDirectory(prefix="flowbus-symbols-") as directory:
        jar = Path(directory) / "classes.jar"
        jar.write_bytes(archive.read("classes.jar"))
        yield jar


def inspect(javap, artifact):
    with classes_jar(artifact) as jar:
        process = subprocess.run(
            [javap, "-protected", "-v", "-classpath", str(jar), *class_names(jar)],
            text=True, capture_output=True, check=True
        )
        members, excluded = parse_members(process.stdout)
        return members, excluded, sha256(jar)


def verify_artifact_hashes(evidence):
    for role in ("baseline", "candidate"):
        if sha256(evidence[role + "Jar"]) != evidence[role + "Sha256"]:
            raise ValueError("Artifact changed since verification: " + role)


def compare(old, new):
    missing = []
    for name, members in old.items():
        if name not in new:
            missing.append([name, "<class>"])
        missing.extend([name, *member] for member in sorted(members - new.get(name, set())))
    return missing


def public_api_members(snapshot):
    """读取仓库 binary-compatibility-validator 快照，再与原版字节码交叉核对。"""
    members = {}
    current = None
    for line in Path(snapshot).read_text().splitlines():
        declaration = re.search(r"\bclass\s+(\S+)", line)
        if declaration and not line.startswith((" ", chr(9))):
            current = declaration.group(1).replace("/", ".")
            members[current] = set()
        elif line == "}":
            current = None
        elif current and line.strip():
            member = re.search(r"\b(?:fun|field)\s+(\S+)\s+(\S+)", line)
            if not member:
                raise ValueError("Unrecognized public API member: " + line)
            members[current].add((member.group(1), member.group(2), " static " in line))
    if not members:
        raise ValueError("No public API classes in snapshot")
    return members


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--runtime-report", type=Path)
    source.add_argument("--baseline-artifact", type=Path)
    parser.add_argument("--candidate-artifact", type=Path)
    parser.add_argument("--baseline-api-snapshot", type=Path)
    parser.add_argument("--javap", default="javap")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.runtime_report:
        if args.candidate_artifact:
            parser.error("--candidate-artifact requires --baseline-artifact")
        evidence = json.loads(args.runtime_report.read_text())
    else:
        if not args.candidate_artifact:
            parser.error("--baseline-artifact requires --candidate-artifact")
        evidence = {}
        for role, artifact in (("baseline", args.baseline_artifact), ("candidate", args.candidate_artifact)):
            evidence[role + "Jar"] = str(artifact.resolve())
            evidence[role + "Sha256"] = sha256(artifact)
    verify_artifact_hashes(evidence)
    old, excluded, baseline_classes_sha = inspect(args.javap, evidence["baselineJar"])
    new, _, candidate_classes_sha = inspect(args.javap, evidence["candidateJar"])
    verify_artifact_hashes(evidence)
    all_changes = compare(old, new)
    supported = public_api_members(args.baseline_api_snapshot) if args.baseline_api_snapshot else None
    if supported is not None:
        mismatches = compare(supported, old)
        if mismatches:
            raise ValueError("Public API snapshot does not match original artifact: " + str(mismatches))
        missing = compare(supported, new)
        # 原始 JVM 差异继续记录；公开快照之外的 internal 成员不作为受支持 API 门禁。
        other_changes = [change for change in all_changes if change not in missing]
    else:
        missing = all_changes
        other_changes = []
    summary = {
        "baselineSha256": evidence["baselineSha256"],
        "candidateSha256": evidence["candidateSha256"],
        "baselineArtifact": evidence["baselineJar"],
        "candidateArtifact": evidence["candidateJar"],
        "baselineClassesSha256": baseline_classes_sha,
        "candidateClassesSha256": candidate_classes_sha,
        "inputMode": "runtime-report" if args.runtime_report else "static-artifacts",
        "classesInspected": len(old),
        "memberDescriptorsChecked": sum(map(len, old.values())),
        "publicApiSnapshot": str(args.baseline_api_snapshot.resolve()) if supported is not None else None,
        "supportedPublicDescriptors": sum(map(len, supported.values())) if supported is not None else None,
        "excludedSyntheticAccessors": excluded,
        "missingDescriptors": missing,
        "otherJvmRemovals": other_changes,
        "scope": "Stable named classes, declared public/protected JVM descriptors and static/instance form. Not full ABI or behavior certification; AAR checks inspect classes.jar only."
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary, indent=2) + chr(10))
    if missing:
        raise SystemExit("FAIL missing JVM descriptors: " + str(missing))
    print("PASS JVM descriptors: " + str(summary["memberDescriptorsChecked"]) +
          "; supported public removals=" + str(len(missing)) +
          "; other JVM removals=" + str(len(other_changes)))


if __name__ == "__main__":
    main()
