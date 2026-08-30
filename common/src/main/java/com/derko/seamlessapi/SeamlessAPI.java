package com.derko.seamlessapi;

import com.derko.seamlessapi.api.ComboRegistration;
import com.derko.seamlessapi.api.FoodBuffRegistration;
import com.derko.seamlessapi.api.deconstruction.DeconstructionModifier;
import com.derko.seamlessapi.api.deconstruction.DeconstructionRegistration;
import com.derko.seamlessapi.api.visual.SpinTumbleAnimator;
import com.derko.seamlessapi.api.visual.ThrownItemVisualProfile;

/**
 * Unified facade for Seamless-API modules.
 *
 * <p>Existing APIs remain available directly (SatiationAPI, DeconstructionAPI).
 * This facade is optional and intended for discoverability.
 */
public final class SeamlessAPI {

    private SeamlessAPI() {
    }

    public static final class Food {
        private Food() {
        }

        public static void registerFood(String itemId, FoodBuffRegistration registration) {
            SatiationAPI.registerFood(itemId, registration);
        }

        public static void registerCombo(String comboId, ComboRegistration registration) {
            SatiationAPI.registerCombo(comboId, registration);
        }
    }

    public static final class Deconstruction {
        private Deconstruction() {
        }

        public static void register(String inputItemId, DeconstructionRegistration registration) {
            DeconstructionAPI.registerDeconstruction(inputItemId, registration);
        }

        public static void registerModifier(DeconstructionModifier modifier) {
            DeconstructionAPI.registerModifier(modifier);
        }
    }

    public static final class Visual {
        private Visual() {
        }

        public static ThrownItemVisualProfile swordthrowThrownItemProfile() {
            return ThrownItemVisualProfile.swordthrowDefaults();
        }

        public static SpinTumbleAnimator swordthrowSpinAnimator() {
            return SpinTumbleAnimator.swordthrowDefaults();
        }
    }
}
