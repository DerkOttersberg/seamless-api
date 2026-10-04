# Minecraft 1.20.1 backport verification

Verified locally on 2026-10-04. Fabric and Forge only; no NeoForge backport.
All five active products are included. Block Animations is excluded by request;
the unfinished comfort project and unrelated projects are not part of this suite.

| Product | Version | Unit tests | Fabric / Forge required GameTests |
|---|---|---:|---:|
| Seamless API / SeamlessLib | 2.0.2+mc1.20.1 | 4 | n/a |
| Pretty Meteors with Trails | 2.0.2+mc1.20.1 | 25 | 1 / 1 |
| Seamless Deconstructing Workbench | 2.1.1+mc1.20.1 | 17 | 8 / 8 |
| Seamless Crafting | 2.1.1+mc1.20.1 | 16 | 11 / 12 |
| Throw Weapons / Sword Throw | 2.1.1+mc1.20.1 | 28 | 7 / 7 |

## Completed local checks

- Every repository: final `clean check build`, Java 17 classfile/loader metadata
  isolation, canonical runtime/source jar names and preserved license checks.
  Total: 90 unit tests and 55 required native GameTests.
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
  Runtime jars were byte-identical before/after the final rebuild and packaged QA.

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
[release-artifacts.json](release-artifacts.json) records all ten jar SHA-256 values
and explicitly marks them unpublished.

Forge client QA uses Loom's supported SRG-to-named development remapping;
the native dedicated-server lane uses untouched SRG release jars.
Expected headless audio/narrator and vanilla/loader startup warnings are retained
in logs, not hidden or presented as mod failures.

## Remaining release gates / limitations

These are tested backports, **not an unconditional production-release sign-off**.
No 1.20.1 files have been uploaded to CurseForge and remote CI is not claimed passed.

- Native packaged Forge-launcher client testing (rather than Loom development launch).
- Two-client multiplayer reconnect/disconnect and late-join rendering acceptance.
- Copied real historical player-world upgrades, wider resource-pack/physical-GPU QA.
- Independent GitHub CI completion; earlier account billing failures are not passes.

Never downgrade a 26.x save to 1.20.1. Keep Minecraft lines, loader jars, API
dependencies and profiles separate. No vanilla Vulkan backend exists for 1.20.1.
