# Seamless API

This is the `1.20.1` source branch: **Fabric and Forge only**, with Java 17
for Minecraft. The `26.2` and `26.3` branches remain separate; never mix their
jars, worlds, or dependency checkouts with this line. See
[REPOSITORY_WORKFLOW.md](REPOSITORY_WORKFLOW.md).

Seamless API is the shared integration library for the Seamless mod family. It
provides stable contracts for satiation and food buffs, deconstruction, meteor
showers, and reusable visual calculations without owning gameplay state.

Version `2.0.2+mc1.20.1` supports Minecraft Java 1.20.1 on Fabric and Forge with Java 17.

## Compatibility contract

- The compatibility mod ID is `seamlessapi` on both loaders.
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
- `fabric` and `forge` contain entrypoints and explicit platform
  adapters.
- `gradle/libs.versions.toml` is the sole source for Minecraft, loader,
  toolchain, and test dependency versions.
- CI rejects loader imports in `common` and jars containing another loader's
  metadata.

## Build

Run Gradle on Java 25; source and Minecraft use the Java 17 toolchain:


```text
gradlew.bat clean check build
```

Loader jars are written to each loader module's `build/libs` directory:

```text
seamless-api-2.0.2+mc1.20.1-fabric.jar
seamless-api-2.0.2+mc1.20.1-forge.jar
```

For sibling development, all four gameplay mods include this repository as a
pinned Gradle composite. Published module coordinates use group
`io.github.derkottersberg` and version `2.0.2+mc1.20.1`; the API is not shaded
into dependent mods.

[`suite-lock.json`](suite-lock.json) records the exact compatible commit of all
five maintained repositories. Suite CI reconstructs those sibling directories
and runs every release build and server GameTest. The API entry is a tested
baseline commit because a manifest cannot contain the hash of the commit that
contains the manifest itself.

See [PORTING.md](PORTING.md) for the loader boundary and
[CONTRIBUTING.md](CONTRIBUTING.md) before submitting changes.

Desktop-safe 1.20.1 client QA is documented in
[legacy-client-qa](.github/legacy-client-qa/README.md). Historical 26.x helpers
are not acceptance evidence for this game line.

## License

MIT
