package com.derko.seamlessapi.api.meteor;

import net.minecraft.server.level.ServerLevel;

/**
 * Static facade for triggering meteor shower events.
 *
 * <p>The actual implementation is provided at runtime by the Pretty Meteors mod.
 * If the meteor mod is not loaded, all calls are silently no-ops.
 *
 * <p>Register an implementation (done automatically by the meteor mod):
 * <pre>
 *   MeteorShowerAPI.registerImplementation(
 *       (world, config) -> PrettyMeteorsMod.startShower(world, toConfig(config)),
 *       world -> PrettyMeteorsMod.stopShower(world)
 *   );
 * </pre>
 *
 * <p>Trigger a shower from any other mod:
 * <pre>
 *   if (MeteorShowerAPI.isAvailable()) {
 *       MeteorShowerAPI.startShower(serverWorld, MeteorShowerRegistration.large());
 *   }
 * </pre>
 */
public final class MeteorShowerAPI {

    @FunctionalInterface
    public interface ShowerStarter {
        void startShower(ServerLevel world, MeteorShowerRegistration config);
    }

    @FunctionalInterface
    public interface ShowerStopper {
        void stopShower(ServerLevel world);
    }

    private static ShowerStarter starter;
    private static ShowerStopper stopper;

    private MeteorShowerAPI() {}

    /**
     * Register the shower implementation.
     * Called once from the Pretty Meteors mod's {@code onInitialize()}.
     */
    public static void registerImplementation(ShowerStarter start, ShowerStopper stop) {
        starter = start;
        stopper = stop;
    }

    /**
     * Start a meteor shower in the given world using the supplied configuration.
     * No-op if no implementation has been registered.
     *
     * @param world  The server world to display the shower in
     * @param config Shower configuration (use factory methods on {@link MeteorShowerRegistration})
     */
    public static void startShower(ServerLevel world, MeteorShowerRegistration config) {
        if (starter != null) {
            starter.startShower(world, config);
        }
    }

    /**
     * Stop any active meteor shower in the given world.
     * No-op if no implementation has been registered.
     *
     * @param world The server world where the shower should be stopped
     */
    public static void stopShower(ServerLevel world) {
        if (stopper != null) {
            stopper.stopShower(world);
        }
    }

    /**
     * Returns {@code true} if the Pretty Meteors mod has registered its implementation.
     * Useful for optional cross-mod integration.
     */
    public static boolean isAvailable() {
        return starter != null;
    }
}
