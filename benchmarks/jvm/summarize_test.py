import copy
import unittest
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

if __name__ == "__main__":
    unittest.main()
