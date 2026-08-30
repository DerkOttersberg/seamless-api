package com.derko.seamlessapi.api;

import java.util.Objects;

/**
 * Immutable snapshot of a single active buff on a player.
 *
 * <p>This is what you get when querying the API for active buffs.
 * Values are current as of query-time; modify via {@link BuffQueryAPI#removeBuffsWithId(net.minecraft.server.level.ServerPlayer, String)}.
 */
public final class BuffData {

    private final String buffId;
    private final int remainingTicks;
    private final int maxTicks;
    private final double magnitude;
    private final double healthBonusHearts;
    private final String foodSource;
    private final long appliedAtGameTime;

    /**
     * Constructs a buff data snapshot.
     *
     * @param buffId              Buff type ID (e.g., "walk_speed")
     * @param remainingTicks      Ticks left before expiry
     * @param maxTicks            Original max ticks
     * @param magnitude           Effect strength multiplier
     * @param healthBonusHearts   Heart bonus value
     * @param foodSource          Food registry ID that gave this buff
     * @param appliedAtGameTime   Server tick when applied
     */
    public BuffData(String buffId, int remainingTicks, int maxTicks, double magnitude,
                    double healthBonusHearts, String foodSource, long appliedAtGameTime) {
        this.buffId = Objects.requireNonNull(buffId, "buffId cannot be null");
        this.remainingTicks = remainingTicks;
        this.maxTicks = maxTicks;
        this.magnitude = magnitude;
        this.healthBonusHearts = healthBonusHearts;
        this.foodSource = Objects.requireNonNull(foodSource, "foodSource cannot be null");
        this.appliedAtGameTime = appliedAtGameTime;
    }

    public String buffId() { return buffId; }
    public int remainingTicks() { return remainingTicks; }
    public int maxTicks() { return maxTicks; }
    public double magnitude() { return magnitude; }
    public double healthBonusHearts() { return healthBonusHearts; }
    public String foodSource() { return foodSource; }
    public long appliedAtGameTime() { return appliedAtGameTime; }

    /** Progress from 0 (just applied) to 1 (about to expire). */
    public double progressRatio() {
        return maxTicks > 0 ? (double) (maxTicks - remainingTicks) / maxTicks : 0.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuffData buffData = (BuffData) o;
        return remainingTicks == buffData.remainingTicks
                && Objects.equals(buffId, buffData.buffId)
                && Objects.equals(foodSource, buffData.foodSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(buffId, remainingTicks, foodSource);
    }

    @Override
    public String toString() {
        return "BuffData{" + buffId + ", " + remainingTicks + "t/" + maxTicks + "t, mag=" + magnitude + "}";
    }
}
