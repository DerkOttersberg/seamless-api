package qa.client;

/**
 * Gives Forge/NeoForge userdev an existing development-classpath directory. This is deliberately
 * not a mod entrypoint. NeoForge associates it with a metadata-only harness descriptor, while the
 * five packaged jars in run/mods remain the only application mods under test.
 */
public final class SuiteClientSmokeClasspathAnchor {
    private SuiteClientSmokeClasspathAnchor() {
    }
}
