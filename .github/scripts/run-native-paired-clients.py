#!/usr/bin/env python3
"""Two genuine packaged clients + production server, private Xvfb only.

Uses generated copied fixtures and loopback offline identities. No desktop input,
no QA server packets replacing gameplay; clients exercise the shipped adapters.
"""
import argparse
import importlib.util
import json
import math
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import subprocess
import sys
import time

SCRIPT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("qa_rcon", SCRIPT / "rcon-command.py")
rcon = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rcon)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("loader", choices=["fabric", "forge"])
    parser.add_argument("staging", type=Path)
    parser.add_argument("run", type=Path)
    parser.add_argument("--jei", action="store_true", help="Include optional JEI + matching MezzConfig on both sides")
    args = parser.parse_args()
    if not re.fullmatch(r":[1-9][0-9]*", os.environ.get("DISPLAY", "")) or os.environ.get("WAYLAND_DISPLAY"):
        raise RuntimeError("Paired clients must run on private Xvfb")
    root = args.run.resolve()
    root.mkdir(parents=True, exist_ok=True)
    if list(root.iterdir()):
        raise RuntimeError("Paired suite requires a fresh empty profile root")
    stage = args.staging.resolve()
    signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(KeyboardInterrupt()))
    for port in (25680, 25675):
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", port))
    server_root = root / "server"
    packaged = stage / f"packaged-{args.loader}-r1"
    server_root.mkdir()
    shutil.copytree(packaged / "suite-world", server_root / "suite-world")
    (server_root / "mods").mkdir()
    runtime = sorted((stage / "runtime-jars" / args.loader).glob(f"*-{args.loader}.jar"))
    if len(runtime) != 5 or any("sources" in jar.name for jar in runtime):
        raise RuntimeError("Expected exactly the five current production jars")
    for jar in runtime:
        shutil.copy2(jar, server_root / "mods" / jar.name)
    if args.loader == "fabric":
        fabric_api = next((packaged / "mods").glob("fabric-api-*.jar"))
        shutil.copy2(fabric_api, server_root / "mods" / fabric_api.name)
    shutil.copytree(packaged / "config", server_root / "config")
    template = stage / ("combined-fabric-r3" if args.loader == "fabric" else "combined-forge-r4")
    input_directory = template / ("mods" if args.loader == "fabric" else "artifact-inputs")
    if args.jei:
        # MezzConfig 0.6.8's Forge ABSENT predicate compares a ChannelData object
        # to a String, so matching installation is needed on both endpoints.
        # This is not a Seamless runtime dependency or a patch to third-party code.
        shutil.copy2(input_directory / "mezz-config.jar", server_root / "mods/mezz-config.jar")
    (server_root / "eula.txt").write_text("eula=true\n")
    properties = {
        "server-ip":"127.0.0.1", "server-port":25680, "online-mode":"false", "enforce-secure-profile":"false",
        "enable-rcon":"true", "rcon.port":25675, "rcon.password":"release-hardening-local-only", "level-name":"suite-world",
        "view-distance":2, "simulation-distance":5, "spawn-protection":0, "allow-flight":"true", "max-tick-time":120000,
    }
    (server_root / "server.properties").write_text("\n".join(f"{k}={v}" for k,v in properties.items()) + "\n")
    java = os.environ["QA_JAVA17"]
    if args.loader == "forge":
        (server_root / "libraries").symlink_to(packaged / "libraries", target_is_directory=True)
        launch = [java, "-Xmx1G", "-XX:ActiveProcessorCount=2", "@libraries/net/minecraftforge/forge/1.20.1-47.4.26/unix_args.txt", "nogui"]
    else:
        for name in ("fabric-server-launch.jar", "fabric-server-launcher.properties", "server.jar"):
            shutil.copy2(packaged / name, server_root / name)
        (server_root / "libraries").symlink_to(packaged / "libraries", target_is_directory=True)
        launch = [java, "-Xmx1G", "-XX:ActiveProcessorCount=2", "-jar", "fabric-server-launch.jar", "nogui"]
    processes, handles = [], []
    def start(command, cwd, log):
        handle = log.open("w"); handles.append(handle)
        process = subprocess.Popen(command, cwd=cwd, stdout=handle, stderr=subprocess.STDOUT, start_new_session=True)
        processes.append(process)
        return process
    server = start(launch, server_root, root / "server-console.log")
    request = 1000
    def cmd(command):
        nonlocal request
        request += 2
        answer = rcon.run_command_with_retries("127.0.0.1",25675,"release-hardening-local-only",request,command,30,2)
        with (root / "rcon.log").open("a") as evidence:
            evidence.write(command + "\n" + answer + "\n")
        return answer
    def condition(value):
        cmd("scoreboard players set check qa_native 0")
        cmd("execute " + value + " run scoreboard players set check qa_native 1")
        answer = cmd("scoreboard players get check qa_native")
        if not re.search(r"has 1 \[", answer):
            raise RuntimeError("Server assertion failed: " + value + " -> " + answer)
    def wait_until(predicate, seconds=120):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            for process in processes:
                if process.poll() is not None:
                    raise RuntimeError("Owned server/client exited prematurely: " + str(process.returncode))
            try:
                if predicate(): return
            except (ConnectionError, OSError):
                pass
            time.sleep(0.25)
        raise RuntimeError("Timed out waiting for packaged acceptance state")
    clients, sequences = {}, {}
    def client(name, menu_only=False):
        profile = root / name
        profile.mkdir(); (profile / "mods").mkdir(); (profile / "config").mkdir()
        template = stage / ("combined-fabric-r3" if args.loader == "fabric" else "combined-forge-r4")
        inputs = template / ("mods" if args.loader == "fabric" else "artifact-inputs")
        for jar in inputs.glob("*.jar"):
            if jar.name in ("fabric-api.jar", "modmenu.jar") or (args.jei and jar.name in ("jei.jar", "mezz-config.jar")):
                shutil.copy2(jar, profile / "mods" / jar.name)
        for jar in runtime:
            shutil.copy2(jar, profile / "mods" / jar.name)
        shutil.copy2(stage / "native-launcher" / f"qa-{args.loader}.jar", profile / "mods/qa-native-client.jar")
        shutil.copy2(template / "options.txt", profile / "options.txt")
        # Test bounded nearby storage, excluding unrelated old generated fixtures.
        shutil.copy2(template / "config/seamless-crafting.json", profile / "config/seamless-crafting.json")
        sequences[name] = 0
        command = [sys.executable,str(SCRIPT / "native-client-launch.py"),args.loader,str(stage / "native-launcher"),str(profile),
                   "--name",name,"--server","127.0.0.1:25680","--qa-property","qa.multiplayer=true","--qa-property","qa.server=127.0.0.1:25680"]
        if menu_only: command.append("--menu-only")
        clients[name] = start(command, profile, root / f"{name}-console.log")
        return profile
    def begin(name, action, **data):
        sequences[name] += 1
        seq = sequences[name]
        profile = root / name
        temporary = profile / "qa-command.tmp"
        temporary.write_text(json.dumps({"sequence":seq,"action":action,**data}))
        temporary.replace(profile / "qa-command.json")
        return profile / f"qa-event-{seq}.json"
    def result(path, seconds=90):
        wait_until(path.is_file, seconds)
        data = json.loads(path.read_text())
        print("PAIRED_ACTION_PASS", path.parent.name, data, flush=True)
    def action(name, verb, **data):
        result(begin(name, verb, **data), 180 if verb in ("ready", "menu-ready", "connect") else 90)
    def setup_items():
        cmd("clear QA_A")
        cmd('data merge block 2 201 0 {Items:[{Slot:0b,id:"minecraft:oak_planks",Count:12b,tag:{Enchantments:[{id:"minecraft:unbreaking",lvl:1s}]}}]}')
    try:
        wait_until(lambda: 'players' in cmd('list'), 150)
        cmd("scoreboard objectives add qa_native dummy")
        cmd("gamerule doMobSpawning false"); cmd("gamerule doDaylightCycle false"); cmd("gamerule keepInventory true")
        cmd("kill @e[type=!minecraft:player]")
        cmd("forceload add -16 -16 16 64")
        time.sleep(2)
        cmd("fill -10 200 -10 10 200 60 minecraft:stone")
        cmd("fill -10 201 -10 10 220 60 minecraft:air")
        condition("if block 0 200 0 minecraft:stone")
        # Generate the dimension fixture before rendering two software clients.
        # This avoids mistaking slow first-time chunk generation for packet failure.
        cmd("execute in minecraft:the_nether run forceload add -16 -16 16 16")
        def nether_ready():
            cmd("scoreboard players set check qa_native 0")
            cmd("execute in minecraft:the_nether if loaded -4 80 -4 if loaded 4 80 -4 if loaded -4 80 4 if loaded 4 80 4 run scoreboard players set check qa_native 1")
            return bool(re.search(r"has 1 \[",cmd("scoreboard players get check qa_native")))
        wait_until(nether_ready,120)
        cmd("execute in minecraft:the_nether run fill -4 79 -4 4 79 4 minecraft:stone")
        cmd("execute in minecraft:the_nether run fill -4 80 -4 4 90 4 minecraft:air")
        condition("in minecraft:the_nether if block 0 79 0 minecraft:stone")
        cmd("setworldspawn 0 201 0"); cmd("setblock 2 201 0 minecraft:barrel"); cmd("setblock 1 201 0 minecraft:crafting_table")
        cmd("setblock -1 201 0 seamlessdeconstructor:reverse_deconstructor")
        client("QA_A"); action("QA_A","ready")
        # Warm B at the title screen, not in this server/world. Native Forge
        # startup can exceed a 90-second shower on two software-rendering CPUs.
        # It still genuinely joins only after the server shower has started.
        client("QA_B", menu_only=True); action("QA_B","menu-ready")
        cmd("gamemode survival QA_A"); cmd("tp QA_A 0.5 201 0.5 0 -35")
        cmd("time set night"); cmd("execute as QA_A at @s run prettymeteors start large")
        action("QA_A","meteor",active=True)
        # B joins a running shower: this is a real late join, not injected state.
        action("QA_B","connect")
        cmd("gamemode survival QA_B"); cmd("tp QA_B 0.5 201 6.5 180 0")
        action("QA_B","meteor",active=True)
        setup_items(); action("QA_A","inventory",count=12)
        action("QA_A","craft")
        condition('if data block 2 201 0 Items[{Slot:0b,Count:8b}]')
        action("QA_A","return")
        condition('if data block 2 201 0 Items[{Slot:0b,Count:12b,tag:{Enchantments:[{id:"minecraft:unbreaking",lvl:1s}]}}]')
        action("QA_A","close")
        cmd("execute as QA_A at @s run prettymeteors start large")
        setup_items(); action("QA_A","craft"); action("QA_A","reconnect")
        # The shipped menu close hook returns outstanding withdrawals to their
        # original storage before vanilla returns any remaining grid items.
        condition('if data block 2 201 0 Items[{Slot:0b,Count:12b,tag:{Enchantments:[{id:"minecraft:unbreaking",lvl:1s}]}}]')
        condition('unless entity @a[name=QA_A,nbt={Inventory:[{id:"minecraft:oak_planks"}]}]')
        print("PAIRED_DISCONNECT_ITEM_CONSERVATION_PASSED", flush=True)
        action("QA_A","meteor",active=True)
        action("QA_A","workbench")
        cmd("item replace block -1 201 0 container.0 with minecraft:iron_pickaxe")
        time.sleep(4)
        condition('if data block -1 201 0 Items[{id:"minecraft:iron_ingot",Count:3b}]')
        condition('if data block -1 201 0 Items[{id:"minecraft:stick",Count:2b}]')
        action("QA_A","close")
        speeds = []
        for hold in (12,45):
            cmd("kill @e[type=swordthrow:thrown_sword]"); cmd("kill @e[type=minecraft:item]"); cmd("clear QA_A")
            cmd('item replace entity QA_A hotbar.0 with minecraft:diamond_sword{Damage:7,display:{Name:\'{"text":"Multiplayer QA"}\'},Enchantments:[{id:"minecraft:unbreaking",lvl:1s}]}')
            cmd("tp QA_A 0.5 201 0.5 0 -25"); cmd("tp QA_B 0.5 201 -6.5 0 0")
            observer = begin("QA_B","observe")
            thrown = begin("QA_A","throw",hold=hold)
            # Sample as soon as the real server projectile exists, not after it lands.
            motion = []
            def sample():
                response = cmd('data get entity @e[type=swordthrow:thrown_sword,limit=1] Motion')
                numbers = re.findall(r'(-?\d+\.\d+(?:E[+-]?\d+)?)[dD]',response)
                if len(numbers) == 3:
                    motion.extend(float(value) for value in numbers); return True
                return False
            wait_until(sample,30)
            speed = math.sqrt(sum(value*value for value in motion)); speeds.append(speed)
            condition('if entity @e[type=swordthrow:thrown_sword,limit=1,nbt={Item:{id:"minecraft:diamond_sword",Count:1b,tag:{Damage:7,Enchantments:[{id:"minecraft:unbreaking",lvl:1s}]}}}]')
            result(observer); result(thrown)
        if speeds[1] <= speeds[0] * 1.15:
            raise RuntimeError("Full charge did not throw harder: " + str(speeds))
        print("PAIRED_PARTIAL_FULL_AND_REMOTE_SLEEVE_PASSED", speeds, flush=True)
        cmd("execute as QA_A at @s run prettymeteors start large")
        cmd("execute in minecraft:the_nether run tp QA_A 0 80 0")
        action("QA_A","dimension",dimension="minecraft:the_nether"); action("QA_A","meteor",active=False)
        cmd("execute in minecraft:overworld run tp QA_A 0.5 201 0.5")
        action("QA_A","dimension",dimension="minecraft:overworld"); action("QA_A","meteor",active=True)
        death = begin("QA_A","respawn"); cmd("kill QA_A"); result(death)
        action("QA_A","reload"); action("QA_A","meteor",active=True)
        cmd("prettymeteors stop"); action("QA_A","meteor",active=False); action("QA_B","meteor",active=False)
        settings = ["com.derko.prettymeteors.client.PrettyMeteorsConfigScreen","com.seamlessdeconstructor.client.SeamlessDeconstructorConfigScreen",
                    "com.derk.easyinventorycrafter.client.EasyInventoryCrafterConfigScreen","io.github.derkottersberg.swordthrow.client.config.SwordThrowConfigScreen"]
        for scale in (2,3,4):
            for screen in settings: action("QA_A","settings",scale=scale,**{"class":screen})
        # Completion markers are only emitted after every server assertion.
        for name in ("QA_A","QA_B"):
            begin(name,"finish")
        for name in ("QA_A","QA_B"):
            clients[name].wait(timeout=45)
            if clients[name].returncode or not (root / name / "client-qa-passed.txt").is_file():
                raise RuntimeError("Packaged client exited without PASS: " + name)
        cmd("save-all flush"); cmd("stop"); server.wait(timeout=45)
        if server.returncode: raise RuntimeError("Server did not stop cleanly")
        (root / "paired-qa-passed.txt").write_text("Two native packaged clients and server passed\n")
        print("NATIVE_PAIRED_SUITE_PASSED", args.loader, flush=True)
    finally:
        for process in reversed(processes):
            if process.poll() is None:
                # Only process groups created by this owned test, never global Java/Xvfb.
                os.killpg(process.pid, signal.SIGTERM)
                try: process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL); process.wait()
        for handle in handles: handle.close()


if __name__ == "__main__":
    main()
