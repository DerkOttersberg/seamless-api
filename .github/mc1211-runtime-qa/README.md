# Minecraft 1.21.1 production-namespace QA

Test-only helper, never a release/dependency jar. Uses the branch catalog, Java 21,
regular Loom mappings/remapJar and explicit loader tick/command adapters.

The dedicated-server scenario connects two genuine clients (QA_A/QA_B) to a
loopback-only offline QA server. It observes real charge packets and animated
player models, drives internal key mappings, and tests item conservation through
menu close/disconnect, reconnect, respawn/dimension changes, tap/partial/full
throwing, active meteor dimension/reconnect/stop synchronization and resource
reload. QA_A uses vanilla wide geometry and QA_B slim geometry via a test-only
skin-model override; it does not inject animation poses.

The same helper can drive the actual Mods/settings menus and combined gameplay
with original production jars. `qa.menuOnly` and `qa.individual` select those
scenarios. Screenshots/PASS markers identify scenario completion, not source
compilation. A helper build alone is not a passed release gate.

Workspace tooling prepares native launcher profiles from Mojang/Fabric metadata
and official Forge/NeoForge installers. All runtime inputs are captured by hash.
GUI execution requires the shared exclusive lock and private Xvfb display,
software OpenGL, two CPUs, low priority and bounded duration. Paired clients and
their server stay in one bounded process tree; no desktop input is sent.

Retain failed attempts. Native scripts refuse existing output profiles, use
copied/generated worlds, and terminate only processes they launched. Production
acceptance and CurseForge publication remain separate recorded gates.
