# Porting guide

## Version sources

Change Minecraft, Java, loader, Loom, and test versions only in
`gradle/libs.versions.toml`. Keep `gradle.properties` limited to project
coordinates and Gradle behavior.

## Port order

1. Update the official-name Minecraft types used by `common`.
2. Run common unit tests and `verifyCommonIsolation`.
3. Update Fabric, Forge, and NeoForge entrypoints and platform adapters.
4. Update each loader's metadata and dependency ranges.
5. Run `gradlew.bat clean check build` on Java 25 (or the Java version pinned
   by the new Minecraft line).
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
