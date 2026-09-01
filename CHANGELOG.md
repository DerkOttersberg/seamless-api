# Changelog

## 2.0.1+mc26.2

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
