import contextlib
import io
import hashlib
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from candidate_bundle import check, seal


class CandidateBundleTest(unittest.TestCase):
    def test_roundtrip_and_reject_changed_commit_content_missing_or_extra_files(self):
        with tempfile.TemporaryDirectory() as directory, patch(
            "candidate_bundle.subprocess.check_output", return_value="commit-a\n"
        ) as git, contextlib.redirect_stdout(io.StringIO()):
            root = Path(directory)
            repository = root / "build/candidate-maven/group/module/1.2.0"
            repository.mkdir(parents=True)
            artifact = repository / "module.jar"
            artifact.write_bytes(b"candidate")
            metadata = repository / "module.pom"
            metadata.write_text("metadata")
            seal(root)
            digest = hashlib.sha256((root / "build/candidate-manifest.json").read_bytes()).hexdigest()
            with patch.dict(os.environ, FLOWBUS_CANDIDATE_MANIFEST_SHA256=digest):
                check(root)
            for invalid_digest in ("", "0" * 64):
                with patch.dict(os.environ, FLOWBUS_CANDIDATE_MANIFEST_SHA256=invalid_digest):
                    with self.assertRaises(ValueError):
                        check(root)
            check(root)
            git.return_value = "commit-b\n"
            with self.assertRaises(ValueError):
                check(root)
            git.return_value = "commit-a\n"
            artifact.write_bytes(b"other")
            with self.assertRaises(ValueError):
                check(root)
            artifact.write_bytes(b"candidate")
            metadata.unlink()
            with self.assertRaises(ValueError):
                check(root)
            metadata.write_text("metadata")
            extra = repository / "extra"
            extra.touch()
            with self.assertRaises(ValueError):
                check(root)
            extra.unlink()
            check(root)

    def test_empty_or_symlink_repository_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(ValueError):
                seal(root)
            repository = root / "build/candidate-maven"
            repository.mkdir(parents=True)
            (repository / "link").symlink_to(__file__)
            with self.assertRaises(ValueError):
                seal(root)


if __name__ == "__main__":
    unittest.main()
