# Minecraft 1.20.1 backport verification

Local verification on 2026-10-04/05. Fabric and Forge only; no NeoForge backport.
All five active products are included. Block Animations is excluded by request;
the unfinished comfort project and unrelated projects are not part of this suite.

| Product | Version | Unit tests | Fabric / Forge required GameTests |
|---|---|---:|---:|
| Seamless API / SeamlessLib | 2.0.2+mc1.20.1 | 4 | n/a |
| Pretty Meteors with Trails | 2.0.2+mc1.20.1 | 25 | 2 / 2 |
| Seamless Deconstructing Workbench | 2.1.1+mc1.20.1 | 17 | 8 / 8 |
| Seamless Crafting | 2.1.1+mc1.20.1 | 16 | 11 / 12 |
| Throw Weapons / Sword Throw | 2.1.1+mc1.20.1 | 28 | 7 / 7 |

## Completed local checks

- Every repository: final `clean check build`, Java 17 classfile/loader metadata
  isolation, canonical runtime/source jar names and preserved license checks.
  Total: 90 unit tests and 57 required native GameTests. Meteors was rebuilt after
  restricting server commands to operator permission; both loaders also passed
  the new native command-authorization regression scenario.
- Each mod with Seamless API in an independent real Fabric and Forge development
  client: world load and configuration-screen smoke checks (library has no settings).
- Actual partial-charge throws on both loaders: real client key-binding flow,
  C2S release, retained name/damage/enchantment NBT, and rendered arm/sleeve pose equality.
- Actual crafting inventory/table screens with JEI 15.62.0.219 and MezzConfig 0.6.8
  on both loaders: nearby enchanted-item sync, four-item recipe autofill, exact
  item return to storage. Screenshots show JEI and nearby panel exclusions.
- Combined five-mod clients on both loaders: partial throws, workbench menu/
  empty-book-slot tooltip and deterministic salvage, nearby crafting and return,
  synchronized meteors and visible rendered sky trails. Fresh pass markers,
  screenshots and successful exit are required; failed attempts are not counted.
- Untouched production jars installed with official Fabric/Forge server installers:
  both five-mod dedicated suites booted, saved, stopped, restarted, and preserved
  blocked workbench pending results plus exact thrown-weapon NBT. Historical
  workbench config migration retained the original and backup.
- Suite validator/RCON harness: 23 tests. Manifest pins compatible game-line commits.
  Meteors has the post-hardening operator-only command fix. Crafting's Fabric
  metadata now derives the correct optional Mod Menu 7.x range from its catalog;
  its fresh clean/check/build passed all units and 11/12 loader GameTests. Archive
  comparison proves only fabric.mod.json changed; Crafting Forge is byte-identical.
  Three other products remain byte-identical to the earlier acceptance inputs.
- Final native packaged multiplayer on both loaders, without and with JEI:
  dedicated server plus two real clients, actual late join, reconnect/disconnect
  item conservation, workbench salvage, partial/full throws and exact NBT,
  remote-player rendered arm/sleeve equality, dimensions, respawn, resource reload
  and settings GUI scales 2/3/4. A QA-only renderer observer checks immutable
  per-actor samples during the actual render, not a subsequently reused model.
  Screenshots show framed actors, clear book hints and JEI panel exclusions.
- Final packaged servers with these exact bytes restart successfully, preserving
  exact weapon/workbench state and legacy config backups. The harness waits for
  vanilla asynchronous entity IO without re-creating saved entities. A replay of
  the earlier failed fixture copy restored its original weapon and exact NBT.

## Desktop isolation and evidence

Java 25 hosts Gradle 9.6; Minecraft, compilation and tests use Java 17.
All client QA ran on private WSL Xvfb displays with software OpenGL, two CPU
affinity slots, 1 GiB client heap, low priority and a bounded one-client lock.
No Windows Minecraft window, desktop focus action or OS input injection was used.
Fixtures modify only disposable copies of generated QA worlds/configs.

Reproducible harness: [.github/legacy-client-qa](.github/legacy-client-qa/README.md).
Local evidence is retained in workspace `qa-artifacts/mc1.20.1`:
`final-build-<repository>.log`, individual client logs/screenshots,
`client-combined-fabric-third.log`, `client-combined-forge-fourth.log`,
`packaged-suite-fabric-second.log`, `packaged-suite-forge-first.log`,
and `packaged-evidence/<loader>/qa-logs`.
Final designated native runs (all successful exits/fresh PASS markers):
`native-paired-forge-r6.log`, `native-paired-forge-jei-r4.log`,
`native-paired-fabric-r2.log`, `native-paired-fabric-jei-r2.log`,
`production-packaged-forge-r2.log`, `production-packaged-fabric-r4.log`,
`failed-fixture-replay.log`. Intermediate failures remain retained and are not
accepted runs. `PRODUCTION-HARDENING.md` explains code and harness corrections.
[release-artifacts.json](release-artifacts.json) records all ten jar SHA-256 values
and explicitly marks them unpublished.

Earlier Forge development QA used Loom's supported SRG-to-named remapping.
The final native client and dedicated-server lanes use untouched SRG release jars
and the official Forge installer profile, with no development launcher/remapping.
Expected headless audio/narrator and vanilla/loader startup warnings are retained
in logs, not hidden or presented as mod failures.

## Acceptance and limitations

Local release acceptance passed for the declared Minecraft 1.20.1 / Java 17 /
Fabric + Forge matrix. This is not a guarantee for every modpack, resource pack,
historical save or physical driver. CurseForge submission/approval/publication is
recorded separately in the workspace upload receipts; remote CI is not passed.

- Copied real historical player-world upgrades, wider resource-pack/physical-GPU QA.
- Independent GitHub CI completion; earlier account billing failures are not passes.
- Optional Forge JEI 15.62.0.219 / MezzConfig 0.6.8 requires matching MezzConfig
  on clients and server because of that upstream optional-channel predicate.
  The Seamless suite itself needs neither and also passed without them.

Never downgrade a 26.x save to 1.20.1. Keep Minecraft lines, loader jars, API
dependencies and profiles separate. No vanilla Vulkan backend exists for 1.20.1.
