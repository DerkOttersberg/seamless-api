# Desktop-safe Minecraft 1.20.1 client QA

This helper is test-only and is never packaged into a release mod.

Use a generated QA world (for example, the dedicated-suite `suite-world`), not
an original player's save. The runner copies it into a fresh profile and refuses
to overwrite a non-empty one:

```bash
# Java 25 hosts Gradle; Java 17 toolchain runs Minecraft.
bash .github/scripts/run-isolated-suite-client.sh fabric /path/to/jars /path/to/qa-world /path/to/fresh-profile
bash .github/scripts/run-isolated-suite-client.sh forge /path/to/jars /path/to/qa-world /path/to/another-fresh-profile
```

Linux/WSL prerequisites: Xvfb, xvfb-run, timeout, flock, nice, taskset, curl,
OpenGL software rendering libraries, Java 25 and a Java 17 toolchain.
The runner unsets WSLg/Wayland/desktop displays, creates a private X server with
no TCP listener, takes a one-client lock, caps CPU/heap/duration, and never uses
OS mouse or keyboard input. Internal Minecraft QA actions affect only this
disposable profile. No Windows Minecraft window is created.

Checks run against a real client and integrated server: partial weapon charge,
actual C2S throw delivery, exact held-item NBT, rendered sleeve/arm poses,
nearby item synchronization, actual recipe autofill/return packets and item
conservation, the workbench book-slot tooltip and live salvage,
meteor synchronization/rendering, and screenshots. Optional JEI
15.62 with MezzConfig 0.6.8 is installed in the test profile. Success requires a
fresh pass marker, no failure marker, screenshots, and a successful process exit.

Fabric uses original remapped release jars in `mods`. Forge's Loom development
runtime uses named members, so `modRuntimeOnly` converts release SRG jars from
`artifact-inputs` to named development mappings. This is real-client QA, **not
a native packaged Forge-launcher client**. The separate packaged-server lane
boots untouched production jars with the official loader installers.

Historical `client-smoke`, `runtime-acceptance`, and `standalone-acceptance`
helpers are 26.x references. They are not used by this branch's CI or evidence.
Do not launch their desktop profiles for this backport. Physical-GPU,
resource-pack breadth or nausea-mod acceptance is not implied by these tests.

## Packaged clients and two-client multiplayer

`../scripts/native-client-launch.py` reads Mojang's exact 1.20.1 version JSON,
downloads hash-checked game libraries/assets, and launches the production Fabric
profile or Forge client-installer profile on Java 17. There is no Loom launcher
or SRG-to-named conversion in this lane. Compile this helper with `remapJar`
for the matching loader, then install its remapped QA jar beside the five
untouched gameplay/library jars in a disposable profile. Never distribute the
QA jar or downloaded game files.

The Forge client installer needs a disposable launcher root with an empty
`launcher_profiles.json`. Install Forge 1.20.1-47.4.26 there using the official
installer's `--installClient` option. `native-client-launch.py --prepare-only`
can finish libraries/assets without starting a window. The launcher refuses
desktop displays and stale PASS/FAIL markers.

`../scripts/run-native-paired-clients.py <loader> <staging> <fresh-run>` starts a
loopback dedicated server plus QA_A/QA_B clients on that same private display.
The staging root contains `native-launcher` (installed profile and qa-fabric.jar /
qa-forge.jar), `runtime-jars/<loader>` (the current five production jars),
`packaged-<loader>-r1` (generated server/world installation), and the
`combined-fabric-r3` / `combined-forge-r4` template options/configs/optional jars.
Those are fixture labels, not references to original player worlds.

Wrap the paired command with `run-isolated-client.sh`: the global lock is held
for the whole group, all processes share two CPU slots and low priority, each
client/server has at most a 1 GiB heap, and the group has a bounded timeout.
`QA_JAVA17` is the Java 17 executable; `ISOLATED_CLIENT_TIMEOUT_SECONDS=1800`
permits the slower paired scenario. `ISOLATED_CLIENT_LOCK_WAIT_SECONDS` optionally
queues for at most 900 seconds without interfering with another task's client.
No other Java/Xvfb process is terminated by this harness.

Actions use real production key bindings, screens and packets. Host controls
only fixture setup and command sequences; server RCON checks independently
verify item/NBT conservation and partial/full projectile speeds. Late join,
disconnect/reconnect, remote sleeves, dimensions, respawn, resource reload and
settings draft/validation at actual GUI scales 2/3/4 are covered. Remote animation
screenshots aim at the other player. A QA-only PlayerRenderer tail observer stores
immutable transform samples during that actor's actual render, before first-person
hand rendering can reuse the shared model. It requires a fresh non-neutral support
arm and equality of all six arm/sleeve transform fields. A group PASS
requires both fresh client markers, every server assertion, and clean exits.
The default excludes JEI; `--jei` adds JEI/MezzConfig, with matching MezzConfig
on the server for the documented upstream Forge optional-channel regression.

The coordinated runner warms the second client at its native title screen before
starting the ordinary 90-second meteor shower, then invokes a real ConnectScreen
connection. Thus slow software-renderer/Forge startup cannot expire the shower
before the late join. No shower duration or production synchronization is altered.
