package com.derko.seamlessapi.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Event hooks for buff lifecycle.
 *
 * <p>Register listeners with the static {@code on*} methods.
 * Listeners are called on the server thread when the respective event fires.
 *
 * <p>Example:
 * <pre>
 *   BuffEvents.onBuffApplied(event -> {
 *       ServerPlayerEntity player = event.getPlayer();
 *       BuffData buff = event.getBuff();
 *       System.out.println(player.getName().getString() + " got " + buff.buffId());
 *   });
 * </pre>
 */
public final class BuffEvents {

    private static final List<Consumer<BuffAppliedEvent>> APPLIED_LISTENERS = new ArrayList<>();
    private static final List<Consumer<BuffRemovedEvent>> REMOVED_LISTENERS = new ArrayList<>();
    private static final List<Consumer<BuffApplyingEvent>> APPLYING_LISTENERS = new ArrayList<>();

    private BuffEvents() {}

    // ---- Registration ----

    /**
     * Register a listener that fires after a buff is successfully applied to a player.
     * Called on the server thread.
     */
    public static void onBuffApplied(Consumer<BuffAppliedEvent> listener) {
        APPLIED_LISTENERS.add(listener);
    }

    /**
     * Register a listener that fires after a buff is removed from a player.
     * Called on the server thread.
     */
    public static void onBuffRemoved(Consumer<BuffRemovedEvent> listener) {
        REMOVED_LISTENERS.add(listener);
    }

    /**
     * Register a listener that fires before a buff is applied.
     * Call {@link BuffApplyingEvent#setCanceled(boolean)} to prevent the buff.
     * Called on the server thread.
     */
    public static void onBuffApplying(Consumer<BuffApplyingEvent> listener) {
        APPLYING_LISTENERS.add(listener);
    }

    // ---- Fire (called by Advanced Food System internals) ----

    /** @hidden Internal — called by Advanced Food System to fire the applied event. */
    public static void fireBuffApplied(ServerPlayer player, BuffData buff) {
        if (APPLIED_LISTENERS.isEmpty()) return;
        BuffAppliedEvent event = new BuffAppliedEvent(player, buff);
        for (Consumer<BuffAppliedEvent> listener : APPLIED_LISTENERS) {
            listener.accept(event);
        }
    }

    /** @hidden Internal — called by Advanced Food System to fire the removed event. */
    public static void fireBuffRemoved(ServerPlayer player, BuffData buff, BuffRemovedEvent.RemovalReason reason) {
        if (REMOVED_LISTENERS.isEmpty()) return;
        BuffRemovedEvent event = new BuffRemovedEvent(player, buff, reason);
        for (Consumer<BuffRemovedEvent> listener : REMOVED_LISTENERS) {
            listener.accept(event);
        }
    }

    /**
     * @hidden Internal — called by Advanced Food System before a buff is applied.
     * @return the (possibly modified) event; check {@link BuffApplyingEvent#isCanceled()} before proceeding.
     */
    public static BuffApplyingEvent fireBuffApplying(ServerPlayer player, String foodSource,
                                                      String primaryBuffId, double magnitude, double healthBonus) {
        BuffApplyingEvent event = new BuffApplyingEvent(player, foodSource, primaryBuffId, magnitude, healthBonus);
        for (Consumer<BuffApplyingEvent> listener : APPLYING_LISTENERS) {
            listener.accept(event);
        }
        return event;
    }

    // ---- Event types ----

    /**
     * Fired after a buff is successfully applied to a player.
     */
    public static final class BuffAppliedEvent {
        private final ServerPlayer player;
        private final BuffData buff;

        BuffAppliedEvent(ServerPlayer player, BuffData buff) {
            this.player = player;
            this.buff = buff;
        }

        public ServerPlayer getPlayer() { return player; }
        public BuffData getBuff() { return buff; }
    }

    /**
     * Fired after a buff is removed (expired or via API).
     */
    public static final class BuffRemovedEvent {
        private final ServerPlayer player;
        private final BuffData buff;
        private final RemovalReason reason;

        public enum RemovalReason {
            /** Buff duration expired naturally. */
            EXPIRED,
            /** Player died and buffs were cleared. */
            DEATH,
            /** API or command removed it. */
            FORCED_REMOVAL,
            /** Slot capacity reached; buff was displaced. */
            DISPLACED
        }

        BuffRemovedEvent(ServerPlayer player, BuffData buff, RemovalReason reason) {
            this.player = player;
            this.buff = buff;
            this.reason = reason;
        }

        public ServerPlayer getPlayer() { return player; }
        public BuffData getBuff() { return buff; }
        public RemovalReason getReason() { return reason; }
    }

    /**
     * Fired before a buff would be applied, allowing cancellation or modification.
     */
    public static final class BuffApplyingEvent {
        private final ServerPlayer player;
        private final String foodSource;
        private final String primaryBuffId;
        private double magnitude;
        private double healthBonus;
        private boolean canceled = false;

        BuffApplyingEvent(ServerPlayer player, String foodSource, String primaryBuffId,
                          double magnitude, double healthBonus) {
            this.player = player;
            this.foodSource = foodSource;
            this.primaryBuffId = primaryBuffId;
            this.magnitude = magnitude;
            this.healthBonus = healthBonus;
        }

        public ServerPlayer getPlayer() { return player; }
        public String getFoodSource() { return foodSource; }
        public String getPrimaryBuffId() { return primaryBuffId; }
        public double getMagnitude() { return magnitude; }
        public double getHealthBonus() { return healthBonus; }

        /** Modify the magnitude (strength) of the buff about to be applied. */
        public void setMagnitude(double magnitude) { this.magnitude = magnitude; }

        /** Modify the health bonus of the buff about to be applied. */
        public void setHealthBonus(double healthBonus) { this.healthBonus = healthBonus; }

        public boolean isCanceled() { return canceled; }
        public void setCanceled(boolean canceled) { this.canceled = canceled; }
    }
}
