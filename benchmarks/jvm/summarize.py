#!/usr/bin/env python3
"""提取真实 JMH 指标、参数和来源，拒绝以缺失/失败报告生成完整基线。"""
import hashlib
import json
import math
from pathlib import Path
import platform
import subprocess
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "benchmarks/jvm"
OUT = WORK / "results/current"
BASELINE_VERSION = "1.2.0"
BASELINE_SHA256 = "525c10846d00e7a41f932ac0ce45dba47b7b8b1da58bc68266e33df4a4b974f2"

def command(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True).strip()

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def source_digests(directory):
    return {path.relative_to(ROOT).as_posix(): digest(path)
            for path in sorted(directory.rglob("*")) if path.is_file()}

def validate_identity(report, target, candidate_version, candidate):
    sidecar = report.with_name(report.stem + ".identity.json")
    if not sidecar.is_file():
        raise ValueError(f"Missing measurement identity: {sidecar}")
    identity = json.loads(sidecar.read_text())
    expected = {
        "schemaVersion": 1, "target": target, "reportSha256": digest(report),
        "baselineVersion": BASELINE_VERSION, "baselineSha256": BASELINE_SHA256,
        "candidateVersion": candidate_version, "candidateSha256": digest(candidate),
        "sourceSha256": source_digests(ROOT / "flowbus-core/src/main"),
        "benchmarkSourceSha256": source_digests(WORK / "src"),
        "benchmarkBuildSha256": {name: digest(WORK / name)
                                 for name in ["build.gradle.kts", "settings.gradle.kts"]},
    }
    for name, value in expected.items():
        if identity.get(name) != value:
            raise ValueError(f"Stale or mismatched {target} measurement identity: {name}")
    core = identity.get("coreJar", {})
    core_path = Path(core.get("path", ""))
    version = BASELINE_VERSION if target == "baseline" else candidate_version
    core_hash = BASELINE_SHA256 if target == "baseline" else digest(candidate)
    if (core.get("version") != version or core.get("sha256") != core_hash
            or not core_path.is_file() or digest(core_path) != core_hash
            or (target != "baseline" and core_path.resolve() != candidate.resolve())):
        raise ValueError(f"Wrong {target} runtime core artifact")
    return identity

def validate(rows, target):
    common = {"noSubscribers", "active1", "active8", "slowSuspend", "slowDrop", "stickySend", "stickyReplay", "producers"}
    scenarios = common | ({"registration", "scope"} if target != "shared" else set()) | ({"configuredRegistration"} if target == "candidate" else set())
    expected = {(scenario, mode) for scenario in scenarios for mode in ("thrpt", "sample")}
    actual = [(row["params"].get("scenario", "configuredRegistration"), row["mode"]) for row in rows]
    if len(actual) != len(expected) or set(actual) != expected:
        raise ValueError(f"Incomplete or duplicate {target} scenarios")
    for row in rows:
        adapter = "shared" if target == "shared" else "bus"
        if row["params"]["adapter"] != adapter:
            raise ValueError(f"Wrong {target} adapter")
        if (row["jmhVersion"], row["forks"], row["warmupIterations"], row["measurementIterations"], row["warmupTime"], row["measurementTime"]) != ("1.37", 3, 5, 5, "1 s", "1 s"):
            raise ValueError(f"Unsupported {target} measurement settings")
        score = row["primaryMetric"]["score"]
        if not isinstance(score, (int, float)) or not math.isfinite(score):
            raise ValueError(f"Invalid {target} score")

def main():
    metrics = {}
    identities = {}
    properties = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text().splitlines() if "=" in line and not line.startswith("#"))
    candidate_version = properties["VERSION_NAME"]
    candidate = ROOT / f"build/candidate-maven/io/github/logan0817/flowbus-core/{candidate_version}/flowbus-core-{candidate_version}.jar"
    expected_counts = {"baseline": 20, "candidate": 22, "shared": 16}
    for target in expected_counts:
        path = WORK / f"build/reports/{target}.json"
        identities[target] = validate_identity(path, target, candidate_version, candidate)
        rows = json.loads(path.read_text())
        validate(rows, target)
        for row in rows:
            for metric in [row["primaryMetric"], *row["secondaryMetrics"].values()]:
                metric.pop("rawDataHistogram", None)
        metrics[target] = rows
    runtime_settings = {(r["jdkVersion"], r["vmVersion"], tuple(r["jvmArgs"])) for rows in metrics.values() for r in rows}
    if len(runtime_settings) != 1:
        raise ValueError("Compared groups must use the same runtime and JVM arguments")
    environment = {
        "generatedUtc": datetime.now(timezone.utc).isoformat(),
        "commit": command("git", "rev-parse", "HEAD"),
        "trackedPatchSha256": hashlib.sha256(subprocess.check_output(["git", "diff", "HEAD", "--"], cwd=ROOT)).hexdigest(),
        "sourceSha256": {str(p.relative_to(ROOT)): digest(p) for p in sorted((ROOT / "flowbus-core/src/main").rglob("*.kt"))},
        "benchmarkSourceSha256": {str(p.relative_to(ROOT)): digest(p) for p in sorted((WORK / "src").rglob("*")) if p.is_file()},
        "benchmarkBuildSha256": {name: digest(WORK / name) for name in ["build.gradle.kts", "settings.gradle.kts"]},
        "measurementIdentities": identities,
        "candidateVersion": candidate_version, "candidateSha256": digest(candidate),
        "baselineVersion": BASELINE_VERSION, "baselineSha256": BASELINE_SHA256,
        "os": platform.platform(), "architecture": platform.machine(),
        "cpu": command("sysctl", "-n", "machdep.cpu.brand_string") if platform.system() == "Darwin" else platform.processor(),
        "logicalCpuCount": __import__("os").cpu_count(),
        "kotlin": "1.9.25", "coroutines": "1.8.1", "jmh": "1.37",
        "order": ["baseline", "candidate", "shared"],
        "measurementReportUpdatedUtc": {target: datetime.fromtimestamp((WORK / f"build/reports/{target}.json").stat().st_mtime, timezone.utc).isoformat() for target in expected_counts},
        "limitation": "Local workstation, background load not controlled; batches include collection setup and ID validation; JVM only."
    }
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "metrics.json").write_text(json.dumps(metrics, ensure_ascii=False, indent=2) + "\n")
    (OUT / "environment.json").write_text(json.dumps(environment, ensure_ascii=False, indent=2) + "\n")
    def find(target, scenario, mode="thrpt"):
        return next((r for r in metrics[target] if r["params"].get("scenario", "configuredRegistration") == scenario and r["mode"] == mode), None)
    lines = ["# JVM 基线实测报告", "", "本报告由真实 JMH JSON 生成；3 forks、5 × 1s 预热、5 × 1s 测量，包含 Throughput/SampleTime 与 GC profiler。误差为 JMH scoreError（默认 99.9% CI）。", "", f"候选 core SHA-256：`{environment['candidateSha256']}`。", f"环境：{environment['cpu']} / {environment['os']}；JDK {metrics['candidate'][0]['jdkVersion']}。", "", "ops/ms 表示批次/ms；B/op 表示每批次分配。active/slow/producers 的每批次包含订阅与校验，不是单事件延迟。", "", "| 场景 | 1.2.0 ops/ms ± 误差 | 当前候选 ops/ms ± 误差 | SharedFlow ops/ms ± 误差 | 候选/基线 | 候选 B/op |", "| --- | --- | --- | --- | --- | --- |"]
    scenarios = ["noSubscribers", "active1", "active8", "slowSuspend", "slowDrop", "stickySend", "stickyReplay", "producers", "registration", "scope", "configuredRegistration"]
    for scenario in scenarios:
        rows = [find(t, scenario) for t in ["baseline", "candidate", "shared"]]
        def fmt(row):
            return "—" if row is None else f"{row['primaryMetric']['score']:.3f} ± {row['primaryMetric']['scoreError']:.3f}"
        ratio = "—" if not rows[0] else f"{rows[1]['primaryMetric']['score'] / rows[0]['primaryMetric']['score']:.3f}"
        alloc = rows[1]["secondaryMetrics"]["gc.alloc.rate.norm"]["score"]
        lines.append(f"| {scenario} | {' | '.join(map(fmt, rows))} | {ratio} | {alloc:.1f} |")
    lines += ["", "| 场景 | 候选实际接收 ID/批次 | 候选实际收集 ID/ms | 候选整批采样耗时 ms/op ± 误差 | 候选整批 p99 ms |", "| --- | --- | --- | --- | --- |"]
    received_per_batch = {"noSubscribers": 0, "active1": 128, "active8": 1024, "slowSuspend": 128, "slowDrop": 65, "stickySend": 0, "stickyReplay": 1, "producers": 128}
    for scenario, received_count in received_per_batch.items():
        throughput = find("candidate", scenario)["primaryMetric"]["score"]
        latency = find("candidate", scenario, "sample")["primaryMetric"]
        lines.append(f"| {scenario} | {received_count} | {throughput * received_count:.3f} | {latency['score']:.5f} ± {latency['scoreError']:.5f} | {latency['scorePercentiles']['99.0']:.5f} |")
    lines += ["", "原始迭代值、误差区间、采样延迟分位数与实际 sent/received 辅助指标见 metrics.json；制品/源码/环境摘要见 environment.json。scope/配置没有 SharedFlow 等价功能；不能把空白值当成更慢或更快。", "", "本地工作站后台负载未受控，测量按 baseline → candidate → shared 串行完成。接近或超过 10% 的变化须结合误差分析；不能仅凭比值推断稳定回退。测量结果不代表 ART，不提供可靠投递或跨框架性能排名。", ""]
    (OUT / "report.md").write_text("\n".join(lines))
    print(OUT / "report.md")

if __name__ == "__main__":
    main()
