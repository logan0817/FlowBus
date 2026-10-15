import unittest
from ci_result import REQUIRED, require_success


class CiResultTest(unittest.TestCase):
    def test_all_success_is_required(self):
        needs = {job: {"result": "success"} for job in REQUIRED}
        require_success(needs)
        for job in REQUIRED:
            for result in ("failure", "cancelled", "skipped"):
                with self.subTest(job=job, result=result), self.assertRaises(ValueError):
                    require_success({**needs, job: {"result": result}})
            with self.assertRaises(ValueError):
                require_success({name: result for name, result in needs.items() if name != job})
        with self.assertRaises(ValueError):
            require_success({})


if __name__ == "__main__":
    unittest.main()
