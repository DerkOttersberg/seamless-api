package com.derko.seamlessapi.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Describes a combo that can be activated when required foods are currently active.
 *
 * <p>Combos are applied as synthetic buffs with source {@code combo:<comboId>}.
 * Build instances with {@link #builder()} and register through
 * {@link com.derko.seamlessapi.SatiationAPI#registerCombo(String, ComboRegistration)}.
 */
public final class ComboRegistration {

    private final Set<String> requiredFoods;
    private final Map<String, Double> effects;
    private final boolean capstone;
    private final boolean grantsFinalHeart;

    private ComboRegistration(Builder builder) {
        this.requiredFoods = Set.copyOf(builder.requiredFoods);
        this.effects = Map.copyOf(builder.effects);
        this.capstone = builder.capstone;
        this.grantsFinalHeart = builder.grantsFinalHeart;
    }

    /** Full food IDs required for combo activation. */
    public Set<String> requiredFoods() { return requiredFoods; }

    /** Map of buffId -> magnitude granted while combo is active. */
    public Map<String, Double> effects() { return effects; }

    /** Whether this combo is treated as a capstone combo. */
    public boolean capstone() { return capstone; }

    /** Whether this combo grants the final +1 heart unlock when active. */
    public boolean grantsFinalHeart() { return grantsFinalHeart; }

    /** Create a builder for a {@link ComboRegistration}. */
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Set<String> requiredFoods = new HashSet<>();
        private final Map<String, Double> effects = new HashMap<>();
        private boolean capstone = false;
        private boolean grantsFinalHeart = false;

        /** Add a required food item ID (e.g. "minecraft:apple"). */
        public Builder requiresFood(String itemId) {
            requiredFoods.add(itemId);
            return this;
        }

        /** Replace required foods with a full set. */
        public Builder requiredFoods(Set<String> items) {
            requiredFoods.clear();
            requiredFoods.addAll(items);
            return this;
        }

        /** Add one combo effect as buffId -> magnitude. */
        public Builder effect(String buffId, double magnitude) {
            effects.put(buffId, magnitude);
            return this;
        }

        /** Replace effects with a full map. */
        public Builder effects(Map<String, Double> buffEffects) {
            effects.clear();
            effects.putAll(buffEffects);
            return this;
        }

        /** Mark as capstone combo. */
        public Builder capstone(boolean value) {
            this.capstone = value;
            return this;
        }

        /** Mark as final-heart-unlock combo. Usually only one combo should do this. */
        public Builder grantsFinalHeart(boolean value) {
            this.grantsFinalHeart = value;
            return this;
        }

        public ComboRegistration build() {
            if (requiredFoods.isEmpty()) {
                throw new IllegalStateException("ComboRegistration must have at least one required food.");
            }
            if (effects.isEmpty()) {
                throw new IllegalStateException("ComboRegistration must define at least one effect.");
            }
            return new ComboRegistration(this);
        }
    }

    @Override
    public String toString() {
        return "ComboRegistration{requiredFoods=" + new ArrayList<>(requiredFoods)
                + ", effects=" + effects
                + ", capstone=" + capstone
                + ", grantsFinalHeart=" + grantsFinalHeart
                + '}';
    }
}
