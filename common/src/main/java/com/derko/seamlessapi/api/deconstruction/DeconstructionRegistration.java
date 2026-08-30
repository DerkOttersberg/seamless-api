package com.derko.seamlessapi.api.deconstruction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registration payload for deconstructing one input item into ingredient units.
 *
 * <p>Units are per one input item. Host mods decide the final rounding and durability scaling.
 */
public final class DeconstructionRegistration {
    private final Map<String, Double> ingredientUnits;
    private final boolean damageScalingEnabled;

    private DeconstructionRegistration(Builder builder) {
        this.ingredientUnits = Map.copyOf(builder.ingredientUnits);
        this.damageScalingEnabled = builder.damageScalingEnabled;
    }

    /** Ingredient registry IDs mapped to units per output item. */
    public Map<String, Double> ingredientUnits() {
        return ingredientUnits;
    }

    /** Whether durability-based scaling should be applied by the host mod. */
    public boolean damageScalingEnabled() {
        return damageScalingEnabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Double> ingredientUnits = new LinkedHashMap<>();
        private boolean damageScalingEnabled = true;

        /**
         * Add one ingredient unit mapping.
         * Example: ingredient("minecraft:stick", 1.0)
         */
        public Builder ingredient(String itemId, double units) {
            if (itemId == null || itemId.isBlank()) {
                throw new IllegalArgumentException("ingredient itemId cannot be null or blank");
            }
            if (units <= 0.0D) {
                throw new IllegalArgumentException("ingredient units must be > 0");
            }
            ingredientUnits.put(itemId, units);
            return this;
        }

        /**
         * Replace all ingredient mappings.
         */
        public Builder ingredients(Map<String, Double> values) {
            ingredientUnits.clear();
            ingredientUnits.putAll(values);
            return this;
        }

        /**
         * Host mods can disable durability scaling for this recipe.
         */
        public Builder damageScalingEnabled(boolean enabled) {
            this.damageScalingEnabled = enabled;
            return this;
        }

        public DeconstructionRegistration build() {
            if (ingredientUnits.isEmpty()) {
                throw new IllegalStateException("DeconstructionRegistration requires at least one ingredient.");
            }
            return new DeconstructionRegistration(this);
        }
    }
}
