import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import summarize

class ReportValidationTest(unittest.TestCase):
    def rows(self):
        rows = []
        for scenario in ["noSubscribers", "active1", "active8", "slowSuspend", "slowDrop", "stickySend", "stickyReplay", "producers"]:
            for mode in ["thrpt", "sample"]:
                rows.append(dict(params=dict(adapter="shared", scenario=scenario), mode=mode,
                    jmhVersion="1.37", forks=3, warmupIterations=5, measurementIterations=5,
                    warmupTime="1 s", measurementTime="1 s", primaryMetric=dict(score=1.0)))
        return rows

    def test_full_comparison_scenarios_are_accepted(self):
        summarize.validate(self.rows(), "shared")

    def test_missing_duplicate_wrong_adapter_smoke_and_nan_cannot_form_baseline(self):
        original = self.rows()
        missing = original[:-1]
        duplicate = copy.deepcopy(original)
        duplicate[-1] = duplicate[0]
        adapter = copy.deepcopy(original)
        adapter[0]["params"]["adapter"] = "bus"
        smoke = copy.deepcopy(original)
        smoke[0]["forks"] = 1
        invalid = copy.deepcopy(original)
        invalid[0]["primaryMetric"]["score"] = float("nan")
        for rows in [missing, duplicate, adapter, smoke, invalid]:
            with self.subTest(rows=rows), self.assertRaises(ValueError):
                summarize.validate(rows, "shared")

class IdentityValidationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.work = self.root / "benchmarks/jvm"
        self.work.mkdir(parents=True)
        for name, value in {
            "flowbus-core/src/main/Core.kt": "class Core",
            "benchmarks/jvm/src/main/Workload.kt": "class Workload",
            "benchmarks/jvm/build.gradle.kts": "build script",
            "benchmarks/jvm/settings.gradle.kts": "settings script",
            "gradle.properties": "VERSION_NAME=1.3.1\n",
            "candidate.jar": "candidate bytes",
        }.items():
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(value)
        for name, value in [("ROOT", self.root), ("WORK", self.work), ("OUT", self.work / "results/current")]:
            replacement = patch.object(summarize, name, value)
            replacement.start()
            self.addCleanup(replacement.stop)
        self.report = self.work / "build/reports/candidate.json"
        self.report.parent.mkdir(parents=True)
        self.report.write_text('[{"measurement": "current"}]\n')
        self.candidate = self.root / "candidate.jar"
        self.version = "1.3.1"
        self.sidecar = self.report.with_name("candidate.identity.json")
        self.identity = {
            "schemaVersion": 1, "target": "candidate", "reportSha256": summarize.digest(self.report),
            "baselineVersion": summarize.BASELINE_VERSION, "baselineSha256": summarize.BASELINE_SHA256,
            "candidateVersion": self.version, "candidateSha256": summarize.digest(self.candidate),
            "coreJar": {"path": str(self.candidate.resolve()), "sha256": summarize.digest(self.candidate),
                        "version": self.version},
            "sourceSha256": summarize.source_digests(self.root / "flowbus-core/src/main"),
            "benchmarkSourceSha256": summarize.source_digests(self.work / "src"),
            "benchmarkBuildSha256": {name: summarize.digest(self.work / name)
                                     for name in ["build.gradle.kts", "settings.gradle.kts"]},
        }

    def validate(self):
        return summarize.validate_identity(self.report, "candidate", self.version, self.candidate)

    def test_current_identity_is_accepted(self):
        self.sidecar.write_text(json.dumps(self.identity))
        self.assertEqual(self.identity, self.validate())

    def test_missing_identity_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Missing measurement identity"):
            self.validate()

    def test_wrong_hash_target_versions_or_runtime_artifact_are_rejected(self):
        changes = [
            {"reportSha256": "old"}, {"target": "baseline"},
            {"candidateVersion": "1.2.0"}, {"baselineVersion": "1.1.0"},
            {"candidateSha256": "old"},
            {"coreJar": dict(self.identity["coreJar"], sha256="wrong")},
        ]
        for change in changes:
            with self.subTest(change=change):
                self.sidecar.write_text(json.dumps(dict(self.identity, **change)))
                with self.assertRaises(ValueError):
                    self.validate()

    def test_stale_report_source_build_or_artifact_is_rejected(self):
        for path in [self.report, self.root / "flowbus-core/src/main/Core.kt",
                     self.work / "src/main/Workload.kt", self.work / "build.gradle.kts", self.candidate]:
            with self.subTest(path=path):
                self.sidecar.write_text(json.dumps(self.identity))
                original = path.read_bytes()
                try:
                    path.write_bytes(original + b"changed")
                    with self.assertRaises(ValueError):
                        self.validate()
                finally:
                    path.write_bytes(original)

    def test_old_reports_without_identity_cannot_overwrite_archived_results(self):
        summarize.OUT.mkdir(parents=True)
        archived = summarize.OUT / "report.md"
        archived.write_text("preserved historical result\n")
        (self.report.parent / "baseline.json").write_text("[]")
        with self.assertRaisesRegex(ValueError, "Missing measurement identity"):
            summarize.main()
        self.assertEqual("preserved historical result\n", archived.read_text())

if __name__ == "__main__":
    unittest.main()
