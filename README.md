# Seamless API

Shared, loader-specific integration APIs used by the Seamless mod family. The library contains registration and query contracts for food buffs, deconstruction, meteor showers, and reusable visual effects; it does not add gameplay on its own.

## Supported builds

| Minecraft | Loader | Branch |
| --- | --- | --- |
| 26.2 | Fabric | [`26.2-fabric`](https://github.com/DerkOttersberg/seamless-api/tree/26.2-fabric) |
| 26.2 | Forge | [`26.2-forge`](https://github.com/DerkOttersberg/seamless-api/tree/26.2-forge) |
| 26.2 | NeoForge | [`26.2-neoforge`](https://github.com/DerkOttersberg/seamless-api/tree/26.2-neoforge) |

All 26.2 builds require Java 25. Older Minecraft branches remain available for maintenance and compatibility.

## Modules

- Food buff registration, queries, modifiers, and lifecycle events
- Deconstruction recipes and output modifiers
- Meteor shower start/stop integration
- Loader-neutral animation and trail math helpers

## Development

Build the checked-out loader branch:

```powershell
.\gradlew.bat build
```

Publish the API to Maven Local for sibling-mod development:

```powershell
.\gradlew.bat publishToMavenLocal
```

Dependency coordinates for 26.2 are:

```gradle
implementation "com.derko.seamlessapi:seamless-api:1.1.0"
```

## License

MIT
