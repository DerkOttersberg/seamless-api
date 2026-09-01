#!/usr/bin/env python3

from __future__ import annotations

import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "verify_suite_lock.py"
SPEC = importlib.util.spec_from_file_location("verify_suite_lock", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
VERIFY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFY)


def valid_manifest() -> dict:
    repositories = []
    for index, (directory, expected) in enumerate(VERIFY.EXPECTED_REPOSITORIES.items(), 1):
        repositories.append(
            {
                "name": expected["name"],
                "repository": expected["repository"],
                "directory": directory,
                "artifactBase": expected["artifactBase"],
                "artifactVersion": expected["artifactVersion"],
                "commit": f"{index:x}" * 40,
                "releaseOrder": expected["releaseOrder"],
            }
        )
    return {
        "schemaVersion": 1,
        "suiteVersion": "2.1.0+mc26.2",
        "minecraft": "26.2",
        "java": 25,
        "gradle": "9.5.1",
        "tooling": {"architecturyPlugin": "3.5.169", "architecturyLoom": "1.17.491"},
        "loaders": {
            "fabricLoader": "0.19.3",
            "fabricApi": "0.159.0+26.2",
            "forge": "26.2-65.1.3",
            "neoforge": "26.2.0.75",
        },
        "repositories": repositories,
    }


class SuiteLockVerifierTest(unittest.TestCase):
    def test_accepts_complete_release_lock(self) -> None:
        self.assertEqual([], VERIFY.validate_manifest(valid_manifest()))

    def test_rejects_missing_artifact_version(self) -> None:
        manifest = valid_manifest()
        del manifest["repositories"][0]["artifactVersion"]
        self.assertTrue(VERIFY.validate_manifest(manifest))

    def test_rejects_wrong_per_project_release_version(self) -> None:
        manifest = valid_manifest()
        manifest["repositories"][2]["artifactVersion"] = "2.0.1+mc26.2"
        self.assertTrue(VERIFY.validate_manifest(manifest))

    def test_rejects_duplicate_directory_and_release_order(self) -> None:
        manifest = valid_manifest()
        manifest["repositories"][4] = copy.deepcopy(manifest["repositories"][3])
        errors = VERIFY.validate_manifest(manifest)
        self.assertTrue(any("duplicate repository directory" in error for error in errors))
        self.assertTrue(any("duplicate releaseOrder" in error for error in errors))

    def test_rejects_malformed_commit(self) -> None:
        manifest = valid_manifest()
        manifest["repositories"][0]["commit"] = "not-a-commit"
        self.assertTrue(any("Git SHA" in error for error in VERIFY.validate_manifest(manifest)))

    def test_loader_values_must_be_non_empty(self) -> None:
        manifest = valid_manifest()
        manifest["loaders"]["forge"] = ""
        self.assertTrue(any("loaders.forge" in error for error in VERIFY.validate_manifest(manifest)))

    def test_loader_rejects_duplicate_json_keys(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "duplicate.json"
            path.write_text('{"schemaVersion": 1, "schemaVersion": 1}', encoding="utf-8")
            with self.assertRaises(VERIFY.DuplicateKeyError):
                VERIFY.load_manifest(path)

    def test_json_round_trip_fixture_is_valid(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "valid.json"
            path.write_text(json.dumps(valid_manifest()), encoding="utf-8")
            self.assertEqual([], VERIFY.validate_manifest(VERIFY.load_manifest(path)))


if __name__ == "__main__":
    unittest.main()
