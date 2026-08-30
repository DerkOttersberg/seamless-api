# Seamless API

Seamless API is the shared integration library for the Seamless mod family. It
provides stable contracts for satiation and food buffs, deconstruction, meteor
showers, and reusable visual calculations without owning gameplay state.

Version `2.0.0+mc26.2` supports Minecraft Java 26.2 on Fabric, Forge, and
NeoForge with Java 25.

## Compatibility contract

- The compatibility mod ID is `seamlessapi` on all three loaders.
- Existing public classes under `com.derko.seamlessapi` remain in that package.
- New implementation classes use `io.github.derkottersberg` and are not API.
- Public method signatures use Minecraft or loader-neutral types; loader
  classes are never exposed by common contracts.
- Architectury Loom is build tooling only. Architectury API is not a runtime
  dependency.

The maintained API surface includes `SatiationAPI`, `DeconstructionAPI`,
`MeteorShowerAPI`, their registration records, and the visual profile/math
types. See [MIGRATION.md](MIGRATION.md) for source and dependency changes from
the older branches.

## Architecture

- `common` contains public contracts, loader-neutral implementation, resources,
  and unit tests.
- `fabric`, `forge`, and `neoforge` contain entrypoints and explicit platform
  adapters.
- `gradle/libs.versions.toml` is the sole source for Minecraft, loader,
  toolchain, and test dependency versions.
- CI rejects loader imports in `common` and jars containing another loader's
  metadata.

## Build

Use Java 25 and run:

```text
gradlew.bat clean check build
```

Loader jars are written to each loader module's `build/libs` directory:

```text
seamless-api-2.0.0+mc26.2-fabric.jar
seamless-api-2.0.0+mc26.2-forge.jar
seamless-api-2.0.0+mc26.2-neoforge.jar
```

For sibling development, Meteors and Workbench include this repository as a
pinned Gradle composite. Published module coordinates use group
`io.github.derkottersberg` and version `2.0.0+mc26.2`; the API is not shaded
into dependent mods.

See [PORTING.md](PORTING.md) for the loader boundary and
[CONTRIBUTING.md](CONTRIBUTING.md) before submitting changes.

## License

MIT
