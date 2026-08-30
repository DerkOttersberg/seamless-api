package com.derko.seamlessapi.api.deconstruction;

/**
 * Immutable context passed to deconstruction output modifiers.
 */
public record DeconstructionContext(
        String inputItemId,
        boolean damageable,
        double durabilityFraction
) {
}
