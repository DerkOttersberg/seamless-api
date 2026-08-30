# Build commands

Use Java 25 from the repository root.

```text
gradlew.bat clean check build
gradlew.bat publishToMavenLocal
```

The first command is the release gate. It compiles all three loaders, runs unit
tests, checks common-source isolation, and validates loader-specific jar
metadata.
