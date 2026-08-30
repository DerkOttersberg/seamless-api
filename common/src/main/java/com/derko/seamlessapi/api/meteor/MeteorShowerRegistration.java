package com.derko.seamlessapi.api.meteor;

/**
 * Configuration for a meteor shower triggered through {@link MeteorShowerAPI}.
 *
 * <p>Use the static factory methods for pre-tuned sizes, or {@link #builder(ShowerSize)}
 * to customise duration and rate.
 *
 * <p>Example:
 * <pre>
 *   MeteorShowerAPI.startShower(world, MeteorShowerRegistration.large());
 *
 *   MeteorShowerAPI.startShower(world,
 *       MeteorShowerRegistration.builder(MeteorShowerRegistration.ShowerSize.MEDIUM)
 *           .durationSeconds(45)
 *           .meteorsPerSecond(2.0f)
 *           .build());
 * </pre>
 */
public final class MeteorShowerRegistration {

    /** Pre-defined size archetypes. */
    public enum ShowerSize {
        /** Single shooting-star burst (~3 s). */
        SINGLE,
        /** Narrow 30-second shower. */
        SMALL,
        /** Moderate 60-second shower. */
        MEDIUM,
        /** Wide, dramatic 90-second shower. */
        LARGE
    }

    private final ShowerSize size;
    private final int durationSeconds;   // 0 = use size default
    private final float meteorsPerSecond; // 0 = use size default
    private final float angularSpreadDegrees; // 0 = use size default

    private MeteorShowerRegistration(Builder builder) {
        this.size = builder.size;
        this.durationSeconds = builder.durationSeconds;
        this.meteorsPerSecond = builder.meteorsPerSecond;
        this.angularSpreadDegrees = builder.angularSpreadDegrees;
    }

    // ---- Factory shortcuts ----

    /** Pre-tuned large shower (90 s, 2.5/sec, wide spread). */
    public static MeteorShowerRegistration large() {
        return new Builder(ShowerSize.LARGE).build();
    }

    /** Pre-tuned medium shower (60 s, 1.5/sec, moderate spread). */
    public static MeteorShowerRegistration medium() {
        return new Builder(ShowerSize.MEDIUM).build();
    }

    /** Pre-tuned small shower (30 s, 0.8/sec, narrow spread). */
    public static MeteorShowerRegistration small() {
        return new Builder(ShowerSize.SMALL).build();
    }

    /** Pre-tuned single shooting-star burst. */
    public static MeteorShowerRegistration single() {
        return new Builder(ShowerSize.SINGLE).build();
    }

    // ---- Getters ----

    public ShowerSize size() { return size; }

    /** Override duration in seconds, or 0 to use the size default. */
    public int durationSeconds() { return durationSeconds; }

    /** Override meteors-per-second rate, or 0 to use the size default. */
    public float meteorsPerSecond() { return meteorsPerSecond; }

    /** Override angular spread in degrees, or 0 to use the size default. */
    public float angularSpreadDegrees() { return angularSpreadDegrees; }

    // ---- Builder ----

    public static Builder builder(ShowerSize size) {
        return new Builder(size);
    }

    public static final class Builder {
        private final ShowerSize size;
        private int durationSeconds = 0;
        private float meteorsPerSecond = 0;
        private float angularSpreadDegrees = 0;

        private Builder(ShowerSize size) {
            this.size = size;
        }

        /** Override the duration. Set to 0 to use the size default. */
        public Builder durationSeconds(int seconds) {
            this.durationSeconds = seconds;
            return this;
        }

        /** Override the rate. Set to 0 to use the size default. */
        public Builder meteorsPerSecond(float rate) {
            this.meteorsPerSecond = rate;
            return this;
        }

        /** Override the spread. Set to 0 to use the size default. */
        public Builder angularSpreadDegrees(float degrees) {
            this.angularSpreadDegrees = degrees;
            return this;
        }

        public MeteorShowerRegistration build() {
            return new MeteorShowerRegistration(this);
        }
    }

    @Override
    public String toString() {
        return "MeteorShowerRegistration{size=" + size
                + (durationSeconds > 0 ? ", duration=" + durationSeconds + "s" : "")
                + (meteorsPerSecond > 0 ? ", rate=" + meteorsPerSecond + "/s" : "")
                + "}";
    }
}
