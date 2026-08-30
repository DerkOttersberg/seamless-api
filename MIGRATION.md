# Migration to 2.0.0 for Minecraft 26.2

## Dependency changes

Use group `io.github.derkottersberg`, version `2.0.0+mc26.2`, and the module
matching the target loader. Seamless API must remain a separate dependency; do
not shade it into another mod. Runtime metadata should require compatible
Seamless API `2.x` releases.

## Source compatibility

Public classes such as `SatiationAPI`, `DeconstructionAPI`, and
`MeteorShowerAPI` remain under `com.derko.seamlessapi`. Registrations and
visual records are retained. Minecraft parameter types now use the official
26.2 names, so integrations compiled for an older Minecraft line must be
recompiled and may need import or signature updates.

Loader classes are no longer part of common-facing contracts. Move any direct
Fabric, Forge, or NeoForge integration into the consuming mod's loader module.
The compatibility mod ID remains `seamlessapi` on every loader.
