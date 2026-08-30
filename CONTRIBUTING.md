# Contributing

Use Java 25 and start work from `main`. Branches use `feat/<name>`,
`fix/<name>`, or `port/mc-<version>`; maintained older lines use
`support/mc-<version>`.

Before opening a pull request:

1. Keep loader-specific imports outside `common`.
2. Add or update tests for public-contract behavior.
3. Update migration notes when a consumer must change source or metadata.
4. Run `gradlew.bat clean check build` with Java 25.
5. Confirm every distributable jar contains only its own loader metadata.

Bug reports should include Minecraft, API, and loader versions plus a minimal
reproduction. API proposals should describe the integration use case and how
the contract stays loader-neutral.
