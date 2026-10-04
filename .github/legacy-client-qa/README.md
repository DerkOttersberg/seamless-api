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
Do not launch their desktop profiles for this backport. No two-human-client
multiplayer, physical-GPU, resource-pack breadth or nausea-mod acceptance is
implied by these automated checks.
