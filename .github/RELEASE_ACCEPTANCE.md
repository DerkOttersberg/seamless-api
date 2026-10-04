# Minecraft 26.3 release acceptance

The maintained suite is SeamlessLib, Pretty Meteors, Deconstructing Workbench,
Seamless Crafting, and Throw Weapons. Block Animations and the unfinished comfort
project are excluded. Java 25 is required; all three loaders have separate jars.

## Completed local gates (October 4, 2026)

- Five `clean check build` runs and all 15 production loader jars.
- 91 common unit tests, zero failures/errors.
- 82 mod-specific native loader GameTest scenarios plus 12 vanilla built-ins.
- All six paired-client loader/backend profiles: real dedicated server and two
  clients, authoritative crafting/returns, partial/full throws, vanilla drops,
  rendered arm/sleeve poses, reconnect, respawn, and dimension changes.
- All 15 standalone integrated-world initialization/dependency profiles.
- All three packaged dedicated-server persistence/restart profiles, using
  copied legacy schema/config fixtures and retained migration backups.
- All three settings profiles at GUI scales 2/3/4, including icons, empty
  book-slot tooltips, draft/validation/resize checks; actual JEI exclusions on
  Fabric and NeoForge.
- Active meteor rendering in all three additional Vulkan combined profiles.
- Final production SHA-256 matched the jars used in every designated profile.

The source ports are recorded in version branches and `suite-lock.json`;
`release-artifacts.json` identifies the tested/CurseForge-submitted binaries.
Detailed logs, screenshots, and PASS markers are retained in the owner's workspace
under `qa-artifacts/mc26.3`. Exploratory failed runs are not accepted evidence.
This records local tests, not a claim that remote GitHub Actions has passed.

## Limits

Minecraft metadata intentionally permits only 26.3. Existing 26.2 jars are
separate; 26.1 is not ported. Forge 66.0.9 and NeoForge 26.3.0.48-beta are upstream
beta loaders, and their CurseForge files are Beta. JEI 31.9.0.57 is optional on
Fabric/NeoForge; there is no corresponding pinned Forge runtime. Mod Menu 21 is
optional on Fabric. Crafting was additionally tested without SeamlessLib, although
the published suite dependency relation requests SeamlessLib.

NeoForge Vulkan profiles set `earlyWindowControl = false` in their new
`config/fml.toml`; do not advertise default-config Vulkan support. Offline QA
clients log unavailable Realms/user services and this Windows machine logs
missing Perflib counters; neither was hidden by changing OS/security settings.

Copied legacy fixtures do not prove every actual historical world upgrade.
Tests do not guarantee every mod pack, skin, resource pack, driver, or storage
provider. Preserve backups. Do not distribute QA helpers, Minecraft jars or worlds.

## Publication

All 15 runtime files were submitted to the existing CurseForge pages with
26.3/Java 25/one-loader metadata and automatic publication after approval.
Moderation status is external and may change. Existing licenses and registry IDs
were preserved; old 26.2 files were not relabeled or overwritten.
