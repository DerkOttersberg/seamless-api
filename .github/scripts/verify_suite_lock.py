#!/usr/bin/env python3
"""Validate the release-lock manifest before cloning or building repositories."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any


# Only the catalog's quoted string version entries are needed. Keep this
# verifier dependency-free on the owner's Python 3.10 as well as CI Python.
_catalog = (Path(__file__).resolve().parents[2] / "gradle/libs.versions.toml").read_text(encoding="utf-8")
_version_section = _catalog.split("[versions]", 1)[1].split("\n[", 1)[0]
_version_pairs = re.findall(r'^([\w-]+)\s*=\s*("[^"\n]*")\s*$', _version_section, re.MULTILINE)
VERSIONS = {key: json.loads(value) for key, value in _version_pairs}
if len(VERSIONS) != len(_version_pairs):
    raise ValueError("Duplicate catalog version entry")
EXPECTED_MINECRAFT = VERSIONS["minecraft"]
EXPECTED_JAVA = int(VERSIONS["java"])

EXPECTED_REPOSITORIES = {
    "seamless-api": {
        "name": "Seamless API",
        "repository": "DerkOttersberg/seamless-api",
        "artifactBase": "seamless-api",
        "artifactVersion": f"2.0.2+mc{EXPECTED_MINECRAFT}",
        "releaseOrder": 1,
    },
    "pretty-meteors-with-trails": {
        "name": "Pretty Meteors with Trails",
        "repository": "DerkOttersberg/pretty-meteors-with-trails",
        "artifactBase": "pretty-meteors-with-trails",
        "artifactVersion": f"2.0.2+mc{EXPECTED_MINECRAFT}",
        "releaseOrder": 2,
    },
    "seamless-deconstructing-workbench": {
        "name": "Seamless Deconstructing Workbench",
        "repository": "DerkOttersberg/seamless-deconstructing-workbench",
        "artifactBase": "seamless-deconstructing-workbench",
        "artifactVersion": f"2.1.1+mc{EXPECTED_MINECRAFT}",
        "releaseOrder": 3,
    },
    "seamless-crafting": {
        "name": "Seamless Crafting",
        "repository": "DerkOttersberg/seamless-crafting",
        "artifactBase": "seamless-crafting",
        "artifactVersion": f"2.1.1+mc{EXPECTED_MINECRAFT}",
        "releaseOrder": 4,
    },
    "sword-throw": {
        "name": "Sword Throw",
        "repository": "DerkOttersberg/sword-throw",
        "artifactBase": "sword-throw",
        "artifactVersion": f"2.1.1+mc{EXPECTED_MINECRAFT}",
        "releaseOrder": 5,
    },
}

EXPECTED_SUITE_VERSION = f"2.1.1+mc{EXPECTED_MINECRAFT}"
EXPECTED_TOOLING = {
    "architecturyPlugin": VERSIONS["architectury-plugin"],
    "architecturyLoom": VERSIONS["architectury-loom"],
}
EXPECTED_LOADERS = {
    "fabricLoader": VERSIONS["fabric-loader"],
    "fabricApi": VERSIONS["fabric-api"],
    "forge": VERSIONS["forge"],
    "neoforge": VERSIONS["neoforge"]
}

SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")


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
    require_exact("sourceState", "frozen")
    require_exact("minecraft", EXPECTED_MINECRAFT)
    require_exact("java", EXPECTED_JAVA)
    require_exact("gradle", "9.6.0")
    require_exact("suiteVersion", EXPECTED_SUITE_VERSION)
    require_exact("tooling", EXPECTED_TOOLING)
    require_exact("loaders", EXPECTED_LOADERS)

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

    print(f"Validated {path} with five pinned Minecraft {EXPECTED_MINECRAFT} source commits (not runtime/release acceptance).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
