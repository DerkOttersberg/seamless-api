# Migration to 2.0.0 for Minecraft 26.2

## Minecraft 1.20.1 backport

This branch targets Java 17, Fabric and Forge only. Its binary/network/Minecraft
types are not compatible with 26.x jars. Install the matching 1.20.1 Seamless API
2.x dependency; never mix Minecraft lines even when a mod's semantic version is
the same. Config/registry names are retained, but this is **not** a world-downgrade
tool. Do not open a 26.x save in 1.20.1. Use a copied existing 1.20.1 world for
upgrade testing and keep original saves/configs backed up.

The notes below describe the shared modernization and retained compatibility
contracts; references to newer loader/version behavior belong to those branches.


## Dependency changes

Use group `io.github.derkottersberg`, version `2.0.1+mc26.2`, and the module
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
