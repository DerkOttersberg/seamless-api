# Contributing

For this branch, base changes on `1.20.1`, not `main` or a 26.x branch.
Java 25 hosts Gradle; Java 17 compiles and runs Minecraft. Supported loaders
are Fabric and Forge only. Keep a matching `1.20.1` API sibling checkout.


Start work from `1.20.1`; short-lived branches use `feat/<name>` or `fix/<name>`.

Before opening a pull request:

1. Keep loader-specific imports outside `common`.
2. Add or update tests for public-contract behavior.
3. Update migration notes when a consumer must change source or metadata.
4. Run `gradlew.bat clean check build` with Java 25.
5. Confirm every distributable jar contains only its own loader metadata.

Bug reports should include Minecraft, API, and loader versions plus a minimal
reproduction. API proposals should describe the integration use case and how
the contract stays loader-neutral.
