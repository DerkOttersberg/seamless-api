# Packaged client smoke

This isolated Loom build has no gameplay source mod of its own. CI copies the five canonical
production jars and the persisted packaged-server world into a clean run directory, then launches
the real Minecraft 26.2 client under Xvfb. NeoForge's development launcher requires the otherwise
empty main source set to carry a descriptor, so it sees one clearly named, metadata-only harness
mod in addition to the five packaged application mods. The smoke gate requires loader discovery
of every application mod, LWJGL initialization, and a quick-play integrated-server start. Fabric
and NeoForge also have an optional JEI 30.29.0.199 startup lane.

This is deliberately named **smoke**, not interaction acceptance. Packaged
two-client automation is now available in [runtime-acceptance](../runtime-acceptance/README.md);
isolated dependency/initialization checks are in
[standalone-acceptance](../standalone-acceptance/README.md). The two-client gate
drives production keys, observes actual packets and rendered models, and checks
menu-close/disconnect/reconnect item conservation. These checks remain manual:

1. Human aesthetic review of remote throwing poses and arbitrary custom skins/armor;
2. Workbench mouse hitboxes and shift-click behavior;
3. Crafting/JEI search, scrolling, hotkeys, and clickable ingredients (automated
   registered JEI exclusion rectangles cover non-overlap separately);
4. Visual confirmation that Meteor pause/resume has no particle burst.
