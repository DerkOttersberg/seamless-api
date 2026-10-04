# Packaged two-client acceptance tests

Test-only project: never include `qaruntime` in a release or a user's profile.
It drives production input and real network connections; it does not inject
throw poses or replace crafting transactions. Both clients inspect actual
server throw-state packets and rendered arm/sleeve transforms.

On Windows with Java 25, use a **new, empty** run directory:

```powershell
./.github/scripts/run-runtime-acceptance.ps1 -Loader neoforge `
  -ServerTemplateDirectory C:/qa/installed-neoforge-server `
  -RuntimeJarsDirectory C:/qa/locked-jars/neoforge `
  -RunDirectory C:/qa/new-neoforge-opengl -Port 25590 -RconPort 25591
```

Repeat for Fabric, Forge, and NeoForge with `-Backend opengl` and `-Backend vulkan`.
Run serially: the Loom launch configuration is shared. The installed server
template supplies bootstrap libraries only; its world/config/player data is
never copied. Tests use a fresh flat world, loopback-only offline server,
test players, bounded waits, and owned-process cleanup. RCON is local QA only.
The script verifies all five locked jar names and the renderer actually used.
NeoForge Vulkan profiles disable `earlyWindowControl` in their **new QA-only**
`config/fml.toml`; its OpenGL early window otherwise fails Vulkan surface creation
before Minecraft can start (upstream [issue #3230](https://github.com/neoforged/NeoForge/issues/3230)).
This is an explicit loader configuration workaround, not a mod compatibility fix.

Scenarios: charge/release/cancel, simultaneous independent poses, hidden skin
layers, leaving/re-entering tracking range, inventory/table close conservation,
real disconnect/reconnect with exact item components, charged dimension change,
death/respawn, and client/server cleanup. Model assertions compare the actual
vanilla baseline with the expected arm delta; sleeves must retain their local
transforms and visibility. Screenshots, logs, and explicit PASS/FAIL files are
written to the isolated run directory. A process merely starting is not a pass.

This is not an exhaustive gameplay, resource-pack, or visual-appearance test.
Unit tests additionally cover standard/slim models and both arms. Loader
GameTests cover registration, storage, recipes, codecs, persistence, and other
server-side contracts. Human visual review remains useful before publication.

Forge resources must contain valid `pack.mcmeta`: a loading warning in Forge
65.1.3 can shut down the global event bus while QuickPlay continues into a world.
Do not hide that failure by freezing the server or replacing loot resources.
