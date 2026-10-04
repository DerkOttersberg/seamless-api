# Porting guide

## Version sources

Change Minecraft, Java, loader, Loom, and test versions only in
`gradle/libs.versions.toml`. Keep `gradle.properties` limited to project
coordinates and Gradle behavior.

## Port order

1. Update the official-name Minecraft types used by `common`.
2. Run common unit tests and `verifyCommonIsolation`.
3. Update Fabric and Forge entrypoints and platform adapters.
4. Update each loader's metadata and dependency ranges.
5. Run `gradlew.bat clean check build` on Java 25, with Java 17 toolchains.
6. Update dependent composite pins and the suite lock only after all loader
   jars pass.

## Boundaries

Code in `common` may import Minecraft and Java classes but never Fabric, Forge,
or NeoForge classes. Entrypoints construct `PlatformServices` explicitly and
pass them to common bootstrap code. Do not introduce reflection,
`ServiceLoader`, runtime Architectury API, or shaded API copies.

Preserve public `com.derko.seamlessapi` names whenever Minecraft's changed
types allow it. A source-breaking signature change requires a migration note
and changelog entry.

## Legacy build boundary

This branch uses regular `dev.architectury.loom` and official Mojang mappings.
Compile shared sources into each loader module; do not put a remapped common jar
on a named development runtime classpath. Both loaders need legacy mixin refmaps.
Only loader remapped `build/libs` jars are distributable. Java 25 hosts Gradle;
Java 17 is used for compilation and Minecraft. Keep plural 1.20.1 data directories
and NBT item persistence; newer data components are not interchangeable.
