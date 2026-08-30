package com.derko.seamlessapi;

import com.derko.seamlessapi.api.FoodBuffRegistration;
import com.derko.seamlessapi.api.ComboRegistration;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static entry point for the Advanced Food System API.
 *
 * <p>Call {@link #registerFood(String, FoodBuffRegistration)} from your mod's constructor or
 * {@code FMLCommonSetupEvent} handler <em>before</em> load completes.
 * After load is complete, registrations are frozen and an exception is thrown.
 *
 * <p>Example:
 * <pre>
 * SatiationAPI.registerFood(
 *     "mymod:spicy_pepper",
 *     FoodBuffRegistration.builder()
 *         .buff("attack_speed")
 *         .duration(1200)   // 20 minutes
 *         .magnitude(0.15)
 *         .hearts(1.0)
 *         .build()
 * );
 * </pre>
 */
public final class SatiationAPI {

    private static final Map<String, FoodBuffRegistration> PENDING = new ConcurrentHashMap<>();
    private static final Map<String, ComboRegistration> PENDING_COMBOS = new ConcurrentHashMap<>();
    private static volatile boolean foodLocked = false;
    private static volatile boolean comboLocked = false;

    private SatiationAPI() {}

    /**
     * Register a custom food's buff data with the Advanced Food System.
     *
     * @param itemId       The full item registry ID, e.g. {@code "mymod:spicy_pepper"}
     * @param registration The buff configuration built via {@link FoodBuffRegistration#builder()}
     * @throws IllegalStateException if called after load has completed
     */
    public static synchronized void registerFood(String itemId, FoodBuffRegistration registration) {
        if (foodLocked) {
            throw new IllegalStateException(
                    "[SeamlessAPI] Too late to register food '" + itemId + "'. " +
                    "Call SatiationAPI.registerFood() in your mod constructor or FMLCommonSetupEvent.");
        }
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("[SeamlessAPI] itemId cannot be null or blank.");
        }
        if (registration == null) {
            throw new IllegalArgumentException("[SeamlessAPI] FoodBuffRegistration cannot be null.");
        }
        PENDING.put(itemId, registration);
    }

    /**
     * Register a custom combo definition consumed by Advanced Food System.
     *
     * @param comboId      Unique combo ID, e.g. {@code "mymod:combo_scholar"}
     * @param registration Combo details and effects
     * @throws IllegalStateException if called after load has completed
     */
    public static synchronized void registerCombo(String comboId, ComboRegistration registration) {
        if (comboLocked) {
            throw new IllegalStateException(
                    "[SeamlessAPI] Too late to register combo '" + comboId + "'. " +
                    "Call SatiationAPI.registerCombo() in your mod constructor or FMLCommonSetupEvent.");
        }
        if (comboId == null || comboId.isBlank()) {
            throw new IllegalArgumentException("[SeamlessAPI] comboId cannot be null or blank.");
        }
        if (registration == null) {
            throw new IllegalArgumentException("[SeamlessAPI] ComboRegistration cannot be null.");
        }
        PENDING_COMBOS.put(comboId, registration);
    }

    /**
     * Called by the Advanced Food System main mod once after all mods have set up.
     * Locks the registry and returns all registered entries.
     *
     * <p><strong>Do not call this from your code.</strong>
     */
    public static synchronized Map<String, FoodBuffRegistration> freezeAndGetAll() {
        foodLocked = true;
        return Map.copyOf(PENDING);
    }

    /**
     * Called by the Advanced Food System main mod after setup.
     * Locks the combo registry and returns all registered combo entries.
     *
     * <p><strong>Do not call this from your code.</strong>
     */
    public static synchronized Map<String, ComboRegistration> freezeAndGetCombos() {
        comboLocked = true;
        return Map.copyOf(PENDING_COMBOS);
    }

    /** Returns whether the registry has been frozen (i.e. past load-complete). */
    public static boolean isLocked() {
        return foodLocked || comboLocked;
    }
}
