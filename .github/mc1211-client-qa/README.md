# Desktop-safe Minecraft 1.21.1 client QA

Test-only Fabric, Forge and NeoForge helper; never included in release jars.
Java 25 hosts Gradle; the catalog's Java 21 toolchain runs Minecraft.

Run only through `.github/scripts/run-isolated-client.sh` on Linux/WSL. It
removes desktop/WSLg display targets, locks out concurrent clients, uses a
private Xvfb display/software OpenGL, and bounds CPU, heap and duration.
It never sends desktop mouse/keyboard input or creates a Windows game window.
Use fresh profiles and copied generated worlds named `qa-world`, never originals.

Fabric takes matching release jars in `mods`, plus Fabric API and optional
Mod Menu. Forge/NeoForge take release jars in `artifact-inputs`; Loom remaps
them to development names. These are real development clients, not proof of a
packaged-launcher client. All pins come from matching gameplay catalogs.

Menu flags `qa.menuOnly=true`, `qa.modMenu=true`, and comma-separated
`qa.iconIds` verify SHA-256/dimensions of Minecraft-loaded PNGs, open the actual
loader Mods menu, and capture each selected product. Gameplay flags
`qa.expectweapons`, `qa.expectworkbench`, `qa.expectcrafting` and
`qa.expectmeteors` choose integrated-world checks. `qa.expectjei` additionally
queries the installed JEI runtime for visible-overlay exclusion registration
on both inventory and crafting screens. `qa.settings` names an
installed mod's Screen constructor for a rendered capture.

Gameplay entry waits for 100 fully initialized title-screen ticks before opening
the copied world. Do not restore Quick Play: it started a Forge resource reload
before its event bus was ready in failed attempts r1/r2, leaving its loot manager
uninitialized. The title-screen path passed the combined Forge test in r3.
Meteor visual inspection uses a clear spectator viewpoint and aims at actual
synchronized trails; a positive meteor count alone is not visual acceptance.

Require fresh pass/no-failure markers, screenshots, process exit zero and log
review. Compile/title-screen success does not prove gameplay, JEI, multiplayer
or production readiness. Record actual gates/limits separately. Minecraft
1.21.1 has no 26.x experimental Vulkan backend.

Older `legacy-client-qa`, `client-smoke`, `runtime-acceptance` and
`standalone-acceptance` directories are historical references. Do not run their
foreground Windows launchers or count older results toward this port.
