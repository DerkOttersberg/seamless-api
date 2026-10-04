# Changelog

## 2.0.2+mc26.3

- Preserve the public `com.derko.seamlessapi` contracts; update the Java 25 multi-loader build and 26.3-only metadata.
- Minecraft 26.3 only, Java 25; Fabric, Forge, and NeoForge.
- Forge 66.0.9 and NeoForge 26.3.0.48-beta are upstream beta loaders.
- Existing 26.2 releases remain separate; no blanket 26.* compatibility.

## 2.0.1+mc26.2

- Added a dedicated library icon for Fabric Mod Menu and native Forge/NeoForge Mods screens.
- Included editable icon source and a reproducible pixel-art renderer.
- Removed the unused Fabric API runtime dependency from the Fabric artifact.
- Added stricter packaged-metadata verification for the 26.2 release line.

## 2.0.0+mc26.2

- Ported the complete API surface to Minecraft Java 26.2 and Java 25.
- Combined Fabric, Forge, and NeoForge in one multi-loader project.
- Retained public `com.derko.seamlessapi` class names and compatibility mod ID.
- Added deconstruction, meteor-shower, and loader-neutral visual contracts to
  the satiation and buff APIs.
- Added explicit platform bootstraps with no runtime Architectury dependency.
- Added common-isolation, unit-test, and loader-metadata release checks.
