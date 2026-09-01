# Packaged client smoke

This isolated Loom build has no gameplay source mod of its own. CI copies the five canonical
production jars and the persisted packaged-server world into a clean run directory, then launches
the real Minecraft 26.2 client under Xvfb. NeoForge's development launcher requires the otherwise
empty main source set to carry a descriptor, so it sees one clearly named, metadata-only harness
mod in addition to the five packaged application mods. The smoke gate requires loader discovery
of every application mod, LWJGL initialization, and a quick-play integrated-server start. Fabric
and NeoForge also have an optional JEI 30.29.0.199 startup lane.

This is deliberately named **smoke**, not interaction acceptance. These checks remain manual
until dedicated input/render automation exists:

1. two simultaneous clients judging Sword Throw remote charge/release/cancel poses;
2. Workbench screen rendering, mouse hitboxes, and shift-click behavior;
3. Crafting/JEI search, scrolling, hotkeys, clickable ingredients, and visual non-overlap;
4. visual confirmation that Meteor pause/resume has no particle burst.
