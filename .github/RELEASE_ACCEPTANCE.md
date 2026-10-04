# Minecraft 26.2 hardened release-candidate gates

The maintained suite contains API, Meteors, Workbench, Crafting, and Sword Throw.
Block Animations is excluded. The exact source/version set is in
[`suite-lock.json`](../suite-lock.json); Minecraft 26.2 / Java 25 remain the target.

The October 4, 2026 local candidate passed:

- Every repository's Java 25 `clean check build` and all 15 loader runtime jars.
- 89 common unit tests, including real baked standard/slim player-model sleeve
  inheritance, both arms, hidden layers, charge/release/cancel, model reuse, and
  invalid animation values.
- Every native loader GameTest lane for the four gameplay mods, with test
  discovery guards. API is a library with four unit contract tests, not a separate
  GameTest task. Loader metadata isolation and QA-class exclusion checks passed.
- Six actual paired-client profiles: Fabric/Forge/NeoForge × OpenGL/Vulkan.
  Production key input, actual server state packets and rendered models;
  independent players, charge/release/cancel, skin-layer toggles, late tracking;
  real inventory/table close and disconnect/reconnect with component-aware item
  conservation; charged dimension change and death/respawn cleanup.
- All 15 isolated integrated-world product/dependency profiles. API runs without
  Fabric API; Crafting runs without Seamless API. Each profile boots a pristine
  copied world, initializes its product, executes vanilla block drops, runs 100+
  server ticks, captures a screenshot and exits cleanly. These are smoke tests,
  not exhaustive individual gameplay acceptance.
- Three final packaged dedicated-server boot/stop/restart profiles, preserving
  Workbench pending operations, copied historical inventory/progress/configs,
  embedded Sword stack/count/components, and retained migration backups.
- SHA-256-locked loader and source artifacts; normal playtest profiles contain
  production jars only, not the QA helper mods.

Repeatable Windows runners:

- [Paired runtime acceptance](runtime-acceptance/README.md)
- [Standalone dependency smoke](standalone-acceptance/README.md)
- Historical fixtures: `scripts/run-packaged-suite.sh` (Git Bash supported)

Detailed local evidence is outside the repositories under
`qa-artifacts/release-hardening-v7/AUDIT.md`, with logs, PASS/FAIL markers and
screenshots. Failed exploratory test setups are retained, not counted as passes.
Artifacts are in `qa-artifacts/final-runtime-jars-v7/` and per-product
`qa-artifacts/release-candidates-v7/` folders.

## Known loader configuration requirement

NeoForge 26.2.0.75's OpenGL early loading window prevents Vulkan surface creation
with the default configuration. The passing Vulkan profile explicitly sets
`earlyWindowControl = false` in its new `config/fml.toml`:
[upstream issue #3230](https://github.com/neoforged/NeoForge/issues/3230).
This is a loader workaround, not a production mod patch. OpenGL works with the
default configuration. Do not advertise default-config NeoForge Vulkan support.

The earlier Forge loot crash was a test-helper pack-metadata defect. Correct
`pack.mcmeta` keeps Forge's event bus active; no frozen-server or substituted
loot-manager workaround is shipped. Final Forge world/dependency/multiplayer
tests ran normally.

## Publication boundary

These are locally tested release candidates, not uploaded/published releases.
Human visual review and copied-world backup testing remain prudent; no test
matrix guarantees every third-party mod, skin, resource pack, armor renderer,
storage provider, or graphics driver. Optional JEI integration remains supported
on Fabric/NeoForge and does not require installing the other suite mods. Licensing
and compatibility registry IDs are unchanged. Do not distribute QA helpers,
Minecraft/bootstrap libraries or test worlds.
