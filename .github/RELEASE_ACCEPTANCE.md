# Minecraft 1.20.1 release acceptance

Suite: SeamlessLib, Meteor Showers, Salvage Workbench, Nearby Chest Crafting and
Throw Weapons. Fabric and Forge only (the owner's legacy-line exception); no
NeoForge, Block Animations, Comfort or Dogs. Minecraft/runtime/bytecode: Java 17.
Java 25 hosts Gradle 9.6.0. Exact dependency/source pins: `suite-lock.json`.

## Completed local gates — October 5, 2026

- Five final `clean check build` runs; Meteors was rebuilt after the operator-only
  command fix. Ten correct remapped production jars with metadata, icons, assets,
  refmaps, namespaces, Java classfiles and dependency isolation checked.
- 90 unit tests and 57 required loader-native GameTests, including non-operator
  meteor command denial, crafting accounting, workbench processing and throwing.
- 23 manifest/RCON harness unit tests.
- Independent real development clients on both loaders, with only the selected
  mod and its dependencies; combined development clients with optional JEI.
- Two final native packaged server persistence/restart scenarios using untouched
  production jars, copied legacy-schema/config fixtures and retained backups.
- Final native packaged multiplayer on both loaders without JEI: one dedicated
  server and two actual clients; late join, reconnect/disconnect item conservation,
  actual recipe autofill/return, workbench salvage, partial/full throws with NBT,
  synchronized remote poses, dimensions, respawn, meteor stop and resource reload.
- Actual settings GUI scales 2/3/4, invalid-number rejection and retained drafts
  on resize/page changes. Workbench empty-book tooltip and visible meteor trails.

The optional native multiplayer JEI/MezzConfig lane and stricter framed remote
animation checks also passed on both loaders. The corrected Crafting Fabric jar
was rebuilt and retested without/with JEI and across native server restart.
Actual-render per-actor probes prevent resting shared-model false positives.
Local evidence is retained at
`qa-artifacts/mc1.20.1`; detailed designated runs are in `QA-MC1.20.1.md`.

## Isolation and limitations

Private WSL Xvfb, llvmpipe software OpenGL, bounded heaps/CPU/time and low priority;
no Windows game window, foreground operation or desktop mouse/keyboard input.
The exclusive lock covers each paired process group. No vanilla Vulkan backend
exists in 1.20.1. No downloaded Minecraft libraries, worlds or QA helpers are
release artifacts. Never downgrade 26.x saves or relabel old releases.

Copied legacy fixtures are not every player's historical world. All modpacks,
resource packs, physical drivers, skins and third-party inventories are not
guaranteed. Backup originals and test copies. Forge JEI 15.62.0.219 / MezzConfig
0.6.8 currently needs matching MezzConfig on the server as well as clients because
of an upstream optional-channel predicate; default Seamless installs need neither.
Do not weaken Forge validation to hide that incompatibility.

GitHub Actions has not passed: freshly checked runs 37237040153 / 37237040159
did not start because the account is locked for billing. Local acceptance is
separate; no fabricated check or billing/protection change was made.

## Upload state

`release-artifacts.json` hashes the exact tested runtime inputs. Upload receipts
in workspace `qa-artifacts/mc1.20.1/curseforge` record actual submission/moderation
separately. Submit only these files to the existing five projects, correct
Minecraft/Java/one-loader tags, required SeamlessLib relations on gameplay mods,
and manual publication after approval. Submission is not approval/publication.
