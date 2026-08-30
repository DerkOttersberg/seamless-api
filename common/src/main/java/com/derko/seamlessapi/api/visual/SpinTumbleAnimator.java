package com.derko.seamlessapi.api.visual;

import java.util.HashMap;
import java.util.Map;

/**
 * Reusable per-entity spin/tumble animator for thrown 3D sprites.
 */
public final class SpinTumbleAnimator {
    private final Map<Integer, SpinState> states = new HashMap<>();
    private final float flightSpinSpeed;
    private final int tumbleDurationTicks;
    private final double tumbleTriggerDot;
    private final double tumbleMinSpeedSquared;
    private final double restingSpeedSquared;

    public SpinTumbleAnimator(float flightSpinSpeed, int tumbleDurationTicks, double tumbleTriggerDot, double tumbleMinSpeedSquared, double restingSpeedSquared) {
        this.flightSpinSpeed = flightSpinSpeed;
        this.tumbleDurationTicks = tumbleDurationTicks;
        this.tumbleTriggerDot = tumbleTriggerDot;
        this.tumbleMinSpeedSquared = tumbleMinSpeedSquared;
        this.restingSpeedSquared = restingSpeedSquared;
    }

    public static SpinTumbleAnimator swordthrowDefaults() {
        return new SpinTumbleAnimator(34.0F, 14, 0.55D, 0.16D, 0.0025D);
    }

    public Orientation sample(int entityId, int entityAge, SeamlessVec3 velocity, float partialTicks) {
        SpinState spinState = states.computeIfAbsent(entityId, ignored -> new SpinState(flightSpinSpeed));
        if (spinState.lastProcessedAge != entityAge) {
            advanceSpinState(entityId, entityAge, spinState, velocity);
            spinState.lastProcessedAge = entityAge;
        }

        return new Orientation(
            spinState.baseRoll + spinState.rollSpeed * partialTicks,
            spinState.baseYaw + spinState.yawSpeed * partialTicks,
            spinState.basePitch + spinState.pitchSpeed * partialTicks
        );
    }

    public void clear(int entityId) {
        states.remove(entityId);
    }

    private void advanceSpinState(int entityId, int entityAge, SpinState spinState, SeamlessVec3 velocity) {
        double speedSquared = velocity.lengthSquared();
        if (speedSquared < restingSpeedSquared) {
            spinState.rollSpeed = 0.0F;
            spinState.yawSpeed = 0.0F;
            spinState.pitchSpeed = 0.0F;
            spinState.tumbleTicksRemaining = 0;
            return;
        }

        if (spinState.lastVelocity.lengthSquared() >= tumbleMinSpeedSquared && speedSquared >= tumbleMinSpeedSquared) {
            double alignment = spinState.lastVelocity.normalize().dot(velocity.normalize());
            if (alignment < tumbleTriggerDot) {
                spinState.tumbleTicksRemaining = tumbleDurationTicks;
                spinState.rollSpeed = randomSigned(entityId, entityAge, 0) * 24.0F;
                spinState.yawSpeed = randomSigned(entityId, entityAge, 1) * 42.0F;
                spinState.pitchSpeed = randomSigned(entityId, entityAge, 2) * 42.0F;
            }
        }

        if (spinState.tumbleTicksRemaining > 0) {
            spinState.baseRoll += spinState.rollSpeed;
            spinState.baseYaw += spinState.yawSpeed;
            spinState.basePitch += spinState.pitchSpeed;
            spinState.rollSpeed *= 0.9F;
            spinState.yawSpeed *= 0.9F;
            spinState.pitchSpeed *= 0.9F;
            spinState.tumbleTicksRemaining--;
        } else {
            spinState.baseRoll += flightSpinSpeed;
            spinState.baseYaw *= 0.75F;
            spinState.basePitch *= 0.75F;
            spinState.yawSpeed = 0.0F;
            spinState.pitchSpeed = 0.0F;
            spinState.rollSpeed = flightSpinSpeed;
        }

        spinState.lastVelocity = velocity;
    }

    private static float randomSigned(int entityId, int entityAge, int salt) {
        int hash = entityId * 73428767 ^ entityAge * 9122713 ^ salt * 19349663;
        hash ^= hash >>> 16;
        return ((hash & 0xFFFF) / 32767.5F) - 1.0F;
    }

    public record Orientation(float roll, float tumbleYaw, float tumblePitch) {
    }

    private static final class SpinState {
        private SeamlessVec3 lastVelocity = SeamlessVec3.ZERO;
        private int lastProcessedAge = Integer.MIN_VALUE;
        private int tumbleTicksRemaining;
        private float baseRoll;
        private float baseYaw;
        private float basePitch;
        private float rollSpeed;
        private float yawSpeed;
        private float pitchSpeed;

        private SpinState(float flightSpinSpeed) {
            this.rollSpeed = flightSpinSpeed;
        }
    }
}
