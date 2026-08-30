package com.derko.seamlessapi.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes a food buff that another mod wants to register with the Advanced Food System.
 *
 * <p>Build instances with {@link #builder()}.
 *
 * <p>Available built-in buff IDs you can reference:
 * <ul>
 *   <li>{@code walk_speed} — increases movement speed</li>
 *   <li>{@code attack_speed} — increases attack speed</li>
 *   <li>{@code mining_speed} — increases break speed (client-side)</li>
 *   <li>{@code jump_height} — increases jump height via JUMP_STRENGTH attribute</li>
 *   <li>{@code damage_reduction} — reduces incoming damage (server-side, capped at 75%)</li>
 *   <li>{@code regeneration} — ticks health regen every second</li>
 *   <li>{@code saturation_boost} — adds saturation on consume</li>
 *   <li>{@code knockback_resistance} — adds knockback resistance</li>
 * </ul>
 */
public final class FoodBuffRegistration {

    private final List<String> buffs;
    private final int durationSeconds;
    private final double magnitude;
    private final double healthBonusHearts;

    private FoodBuffRegistration(Builder builder) {
        this.buffs = List.copyOf(builder.buffs);
        this.durationSeconds = builder.durationSeconds;
        this.magnitude = builder.magnitude;
        this.healthBonusHearts = builder.healthBonusHearts;
    }

    /** The buff IDs this food applies (first in the list is the primary buff shown in HUD). */
    public List<String> buffs() { return buffs; }

    /** Duration in seconds for the buff. */
    public int durationSeconds() { return durationSeconds; }

    /** Effect magnitude/strength multiplier. */
    public double magnitude() { return magnitude; }

    /** Heart bonus granted while this buff is active (contributes to overhealth). */
    public double healthBonusHearts() { return healthBonusHearts; }

    /** Create a builder for a {@link FoodBuffRegistration}. */
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<String> buffs = new ArrayList<>();
        private int durationSeconds = 1200;
        private double magnitude = 0.20;
        private double healthBonusHearts = 0.5;

        /** Add a buff ID. Call multiple times for multi-buff foods. */
        public Builder buff(String buffId) {
            this.buffs.add(buffId);
            return this;
        }

        /** Replace the buff list entirely. */
        public Builder buffs(List<String> buffIds) {
            this.buffs.clear();
            this.buffs.addAll(buffIds);
            return this;
        }

        /** Buff duration in seconds (default 1200 = 20 minutes). */
        public Builder duration(int seconds) {
            this.durationSeconds = seconds;
            return this;
        }

        /** Effect strength multiplier (default 0.20). */
        public Builder magnitude(double mag) {
            this.magnitude = mag;
            return this;
        }

        /**
         * Heart bonus while active. 0.5 = half heart, 1.0 = full heart.
         * Max bonus from all food slots combined is 3 hearts (base is 6, max is 10, last heart via combo).
         */
        public Builder hearts(double hearts) {
            this.healthBonusHearts = hearts;
            return this;
        }

        public FoodBuffRegistration build() {
            if (buffs.isEmpty()) {
                throw new IllegalStateException("FoodBuffRegistration must have at least one buff.");
            }
            return new FoodBuffRegistration(this);
        }
    }

    @Override
    public String toString() {
        return "FoodBuffRegistration{buffs=" + buffs +
                ", duration=" + durationSeconds +
                "s, magnitude=" + magnitude +
                ", hearts=" + healthBonusHearts + "}";
    }
}
