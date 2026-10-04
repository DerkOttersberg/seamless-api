# Individual packaged-mod smoke tests

Test-only mod, excluded from every production jar. Run serially, independently
of other client-smoke tests (they share Loom's launch configuration):

```powershell
./.github/scripts/run-standalone-acceptance.ps1 `
  -FabricServerTemplateDirectory C:/qa/installed-fabric-server `
  -RuntimeJarsRoot C:/qa/locked-jars -RunDirectory C:/qa/new-standalone-run
```

Uses Java 25 and a new, empty QA directory. Generates a pristine vanilla seed
world without product mods. Each of the 15 loader/product combinations gets
its own copy, exactly one product, and only its declared dependencies. API
runs without Fabric API; Crafting runs without Seamless API. Each real client
must enter its integrated world, execute the vanilla block-drop path, complete
100+ server ticks, initialize the selected product, capture a screenshot, and
exit cleanly. Explicit PASS files and logs are retained. Worlds and user
profiles are never reused. These are initialization/dependency smoke tests,
not exhaustive individual gameplay acceptance; use the loader GameTests and
paired runtime tests as well.
