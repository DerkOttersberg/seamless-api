#!/usr/bin/env python3
"""QA-only launcher: official version JSON/installer, untouched production mods.

Offline identities are only for the loopback, offline-mode acceptance server.
The caller MUST run this under the private Xvfb isolation wrapper.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.request
import uuid
import zipfile


def fetch(url, destination, sha1=None):
    destination = Path(destination)
    if destination.is_file() and (sha1 is None or hashlib.sha1(destination.read_bytes()).hexdigest() == sha1):
        return destination
    destination.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url, timeout=90) as response:
        data = response.read()
    if sha1 and hashlib.sha1(data).hexdigest() != sha1:
        raise RuntimeError(f"SHA-1 mismatch: {url}")
    destination.write_bytes(data)
    return destination


def load_url(url):
    with urllib.request.urlopen(url, timeout=90) as response:
        return json.load(response)


def allowed(item):
    result = not item.get("rules")
    for rule in item.get("rules", []):
        if rule.get("features"):
            continue  # no demo/custom resolution/quick-play feature substitutions
        target = rule.get("os", {})
        if target.get("name", "linux") != "linux":
            continue
        if "arch" in target and not re.search(target["arch"], "x86_64"):
            continue
        if "version" in target and not re.search(target["version"], os.uname().release):
            continue
        result = rule["action"] == "allow"
    return result


def coordinate(name):
    group, artifact, version, *classifier = name.split(":")
    suffix = "-" + classifier[0] if classifier else ""
    return f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}{suffix}.jar"


def prepare(root, loader):
    root = Path(root).resolve()
    manifest = load_url("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
    entry = next(v for v in manifest["versions"] if v["id"] == "1.20.1")
    base_path = fetch(entry["url"], root / "versions/1.20.1/1.20.1.json", entry["sha1"])
    base = json.loads(base_path.read_text())
    client = base["downloads"]["client"]
    fetch(client["url"], root / "versions/1.20.1/1.20.1.jar", client["sha1"])
    if loader == "fabric":
        profile = load_url("https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.19.5/profile/json")
    else:
        profile = json.loads((root / "versions/1.20.1-forge-47.4.26/1.20.1-forge-47.4.26.json").read_text())
    libraries = root / "libraries"
    classpath = []
    native_paths = []
    for item in base["libraries"] + profile["libraries"]:
        if not allowed(item):
            continue
        artifact = item.get("downloads", {}).get("artifact")
        relative = artifact["path"] if artifact else coordinate(item["name"])
        path = libraries / relative
        url = artifact["url"] if artifact else item.get("url", "https://libraries.minecraft.net/") + relative
        fetch(url, path, artifact.get("sha1") if artifact else None)
        classpath.append(str(path))
        if "natives-linux" in relative:
            native_paths.append(path)
        classifier = item.get("natives", {}).get("linux")
        if classifier:
            native = item["downloads"]["classifiers"][classifier.replace("${arch}", "64")]
            native_paths.append(fetch(native["url"], libraries / native["path"], native["sha1"]))
    client_jar = root / "versions/1.20.1/1.20.1.jar"
    if loader == "forge":
        # Official launcher inheritance uses the selected profile's jar name.
        # Forge's supplied ignoreList excludes that name from its module layer.
        inherited = root / "versions" / profile["id"] / (profile["id"] + ".jar")
        if not inherited.is_file():
            shutil.copy2(client_jar, inherited)
        client_jar = inherited
    classpath.append(str(client_jar))
    natives = root / ("natives-linux-" + loader)
    natives.mkdir(exist_ok=True)
    for path in native_paths:
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                if name.endswith(".so") and ".." not in Path(name).parts:
                    destination = natives / Path(name).name
                    data = archive.read(name)
                    if destination.is_file():
                        if destination.read_bytes() != data:
                            raise RuntimeError("Native library changed; prepare a fresh launcher root")
                    else:
                        destination.write_bytes(data)
    assets = root / "assets"
    index = base["assetIndex"]
    asset_json = fetch(index["url"], assets / f"indexes/{index['id']}.json", index["sha1"])
    objects = json.loads(asset_json.read_text())["objects"].values()
    def asset(obj):
        key = obj["hash"]
        fetch(f"https://resources.download.minecraft.net/{key[:2]}/{key}", assets / f"objects/{key[:2]}/{key}", key)
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(asset, objects))
    return base, profile, classpath, natives, assets


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("loader", choices=["fabric", "forge"])
    parser.add_argument("root", type=Path)
    parser.add_argument("profile", type=Path)
    parser.add_argument("--prepare-only", action="store_true")
    parser.add_argument("--name", default="NativeQA")
    parser.add_argument("--server")
    parser.add_argument("--menu-only", action="store_true", help="Warm an isolated client before a real late-join connection")
    parser.add_argument("--qa-property", action="append", default=[])
    args = parser.parse_args()
    base, loader, cp, natives, assets = prepare(args.root, args.loader)
    if args.prepare_only:
        print("NATIVE_CLIENT_PREPARED", args.loader)
        return
    display = os.environ.get("DISPLAY", "")
    if not re.fullmatch(r":[1-9][0-9]*", display) or os.environ.get("WAYLAND_DISPLAY"):
        raise RuntimeError("Native client requires a private nonzero Xvfb display")
    profile = args.profile.resolve()
    if any((profile / name).exists() for name in ["client-qa-passed.txt", "client-qa-failed.txt"]):
        raise RuntimeError("Refusing stale acceptance markers: use a fresh QA profile")
    substitutions = {
        "natives_directory": str(natives), "launcher_name": "SeamlessIsolatedQA", "launcher_version": "1",
        "classpath": os.pathsep.join(cp), "library_directory": str(args.root.resolve() / "libraries"),
        "classpath_separator": os.pathsep, "version_name": loader["id"], "game_directory": str(profile),
        "assets_root": str(assets), "assets_index_name": base["assetIndex"]["id"],
        "auth_player_name": args.name, "auth_uuid": str(uuid.uuid3(uuid.NAMESPACE_DNS, args.name)),
        "auth_access_token": "0", "clientid": "", "auth_xuid": "", "user_type": "legacy",
        "version_type": "release", "user_properties": "{}",
    }
    def expand(value):
        return re.sub(r"\$\{([^}]+)\}", lambda match: substitutions[match[1]], value)
    def arguments(kind):
        output = []
        for entry in base["arguments"][kind] + loader.get("arguments", {}).get(kind, []):
            if isinstance(entry, str):
                output.append(expand(entry))
            elif allowed(entry):
                values = entry["value"]
                output.extend(expand(value) for value in ([values] if isinstance(values, str) else values))
        return output
    jvm = arguments("jvm")
    # Required by Forge's client module layer; provided in the official profile.
    game = arguments("game") + ["--width", "854", "--height", "480"]
    if args.menu_only:
        pass
    elif args.server:
        game.extend(["--quickPlayMultiplayer", args.server])
    else:
        game.extend(["--quickPlaySingleplayer", "qa-world"])
    command = [os.environ["QA_JAVA17"], "-Xms256M", "-Xmx1G", "-XX:ActiveProcessorCount=2"]
    command.extend("-D" + value for value in args.qa_property)
    command.extend(jvm + [loader["mainClass"]] + game)
    print("NATIVE_CLIENT_LAUNCH", args.loader, args.name, "untouched production jars", flush=True)
    process = subprocess.run(command, cwd=profile)
    passed = profile / "client-qa-passed.txt"
    failed = profile / "client-qa-failed.txt"
    if process.returncode or not passed.is_file() or failed.exists():
        raise SystemExit("Native client failed or closed without a fresh QA PASS marker")
    print("NATIVE_CLIENT_PASSED", args.loader, args.name)


if __name__ == "__main__":
    main()
