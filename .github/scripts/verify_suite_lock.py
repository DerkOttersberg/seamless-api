#!/usr/bin/env python3
"""Validate the release-lock manifest before cloning or building repositories."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any


EXPECTED_REPOSITORIES = {
    "seamless-api": {
        "name": "Seamless API",
        "repository": "DerkOttersberg/seamless-api",
        "artifactBase": "seamless-api",
        "artifactVersion": "2.0.1+mc26.2",
        "releaseOrder": 1,
    },
    "pretty-meteors-with-trails": {
        "name": "Pretty Meteors with Trails",
        "repository": "DerkOttersberg/pretty-meteors-with-trails",
        "artifactBase": "pretty-meteors-with-trails",
        "artifactVersion": "2.0.1+mc26.2",
        "releaseOrder": 2,
    },
    "seamless-deconstructing-workbench": {
        "name": "Seamless Deconstructing Workbench",
        "repository": "DerkOttersberg/seamless-deconstructing-workbench",
        "artifactBase": "seamless-deconstructing-workbench",
        "artifactVersion": "2.1.0+mc26.2",
        "releaseOrder": 3,
    },
    "seamless-crafting": {
        "name": "Seamless Crafting",
        "repository": "DerkOttersberg/seamless-crafting",
        "artifactBase": "seamless-crafting",
        "artifactVersion": "2.1.0+mc26.2",
        "releaseOrder": 4,
    },
    "sword-throw": {
        "name": "Sword Throw",
        "repository": "DerkOttersberg/sword-throw",
        "artifactBase": "sword-throw",
        "artifactVersion": "2.1.0+mc26.2",
        "releaseOrder": 5,
    },
}

SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
SUITE_VERSION_PATTERN = re.compile(r"^\d+\.\d+\.\d+\+mc26\.2$")


class DuplicateKeyError(ValueError):
    pass


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateKeyError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def load_manifest(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle, object_pairs_hook=_reject_duplicate_keys)


def validate_manifest(manifest: Any) -> list[str]:
    errors: list[str] = []
    if not isinstance(manifest, dict):
        return ["manifest root must be a JSON object"]

    def require_exact(key: str, expected: Any) -> None:
        actual = manifest.get(key)
        if type(actual) is not type(expected) or actual != expected:
            errors.append(f"{key} must be {expected!r}, got {actual!r}")

    require_exact("schemaVersion", 1)
    require_exact("minecraft", "26.2")
    require_exact("java", 25)
    require_exact("gradle", "9.5.1")

    suite_version = manifest.get("suiteVersion")
    if not isinstance(suite_version, str) or not SUITE_VERSION_PATTERN.fullmatch(suite_version):
        errors.append("suiteVersion must use <semver>+mc26.2")

    tooling = manifest.get("tooling")
    if not isinstance(tooling, dict):
        errors.append("tooling must be an object")
    else:
        for key in ("architecturyPlugin", "architecturyLoom"):
            if not isinstance(tooling.get(key), str) or not tooling[key].strip():
                errors.append(f"tooling.{key} must be a non-empty string")

    loaders = manifest.get("loaders")
    required_loaders = ("fabricLoader", "fabricApi", "forge", "neoforge")
    if not isinstance(loaders, dict):
        errors.append("loaders must be an object")
    else:
        for key in required_loaders:
            if not isinstance(loaders.get(key), str) or not loaders[key].strip():
                errors.append(f"loaders.{key} must be a non-empty string")

    repositories = manifest.get("repositories")
    if not isinstance(repositories, list):
        return errors + ["repositories must be an array"]
    if len(repositories) != len(EXPECTED_REPOSITORIES):
        errors.append(f"repositories must contain exactly {len(EXPECTED_REPOSITORIES)} entries")

    seen_directories: set[str] = set()
    seen_orders: set[int] = set()
    seen_artifacts: set[tuple[str, str]] = set()
    for index, repository in enumerate(repositories):
        prefix = f"repositories[{index}]"
        if not isinstance(repository, dict):
            errors.append(f"{prefix} must be an object")
            continue

        directory = repository.get("directory")
        if not isinstance(directory, str):
            errors.append(f"{prefix}.directory must be a string")
            continue
        if directory in seen_directories:
            errors.append(f"duplicate repository directory: {directory}")
        seen_directories.add(directory)

        expected = EXPECTED_REPOSITORIES.get(directory)
        if expected is None:
            errors.append(f"unexpected repository directory: {directory}")
        else:
            for key, expected_value in expected.items():
                actual = repository.get(key)
                if type(actual) is not type(expected_value) or actual != expected_value:
                    errors.append(
                        f"{prefix}.{key} must be {expected_value!r}, got {actual!r}"
                    )

        commit = repository.get("commit")
        if not isinstance(commit, str) or not SHA_PATTERN.fullmatch(commit):
            errors.append(f"{prefix}.commit must be a lowercase 40-character Git SHA")
        elif commit == "0" * 40:
            errors.append(f"{prefix}.commit must not be the all-zero SHA")

        release_order = repository.get("releaseOrder")
        if type(release_order) is not int:
            errors.append(f"{prefix}.releaseOrder must be an integer")
        elif release_order in seen_orders:
            errors.append(f"duplicate releaseOrder: {release_order}")
        else:
            seen_orders.add(release_order)

        artifact_base = repository.get("artifactBase")
        artifact_version = repository.get("artifactVersion")
        if isinstance(artifact_base, str) and isinstance(artifact_version, str):
            artifact_key = (artifact_base, artifact_version)
            if artifact_key in seen_artifacts:
                errors.append(f"duplicate artifact identity: {artifact_base}-{artifact_version}")
            seen_artifacts.add(artifact_key)

    missing = sorted(set(EXPECTED_REPOSITORIES) - seen_directories)
    if missing:
        errors.append(f"missing repositories: {', '.join(missing)}")
    if seen_orders != set(range(1, len(EXPECTED_REPOSITORIES) + 1)):
        errors.append("releaseOrder values must be exactly 1 through 5")

    return errors


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"usage: {Path(argv[0]).name} <suite-lock.json>", file=sys.stderr)
        return 2

    path = Path(argv[1])
    try:
        manifest = load_manifest(path)
    except (OSError, json.JSONDecodeError, DuplicateKeyError) as error:
        print(f"invalid suite lock {path}: {error}", file=sys.stderr)
        return 1

    errors = validate_manifest(manifest)
    if errors:
        print(f"invalid suite lock {path}:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    print(f"Validated {path} with five pinned Minecraft 26.2 release artifacts.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
