package com.derko.seamlessapi.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registry for dynamic buff modifiers (pre-registered calculation functions).
 *
 * <p>Modders can register functions that intercept and modify buff calculations
 * before they're applied. Useful for scaling effects based on other mods' systems.
 *
 * <p>Example: A mod might register a modifier that increases buff magnitude
 * when the player wears special armor:
 * <pre>
 *   BuffModifiers.registerMagnitudeModifier("special_armor_sync",
 *       (player, buffId, baseMagnitude) -> {
 *           if (player.getInventory().contains(Items.NETHERITE_HELMET)) {
 *               return baseMagnitude * 1.5;
 *           }
 *           return baseMagnitude;
 *       });
 * </pre>
 */
public final class BuffModifiers {

    /**
     * Function that can modify buff magnitude.
     * Receives player, buff ID, and base magnitude; returns modified magnitude.
     */
    @FunctionalInterface
    public interface MagnitudeModifier {
        double apply(Object player, String buffId, double baseMagnitude);
    }

    /**
     * Function that can modify health bonus.
     * Receives player, buff ID, and base health bonus; returns modified bonus.
     */
    @FunctionalInterface
    public interface HealthModifier {
        double apply(Object player, String buffId, double baseHealth);
    }

    /**
     * Function that can determine if a buff should be applied at all.
     * Return false to veto the application.
     */
    @FunctionalInterface
    public interface ApplicationFilter {
        boolean shouldApply(Object player, String foodSource, String buffId);
    }

    private static final Map<String, List<MagnitudeModifier>> MAGNITUDE_MODS = new HashMap<>();
    private static final Map<String, List<HealthModifier>> HEALTH_MODS = new HashMap<>();
    private static final List<ApplicationFilter> FILTERS = new ArrayList<>();

    static {
        // Register default empty lists for common buff IDs to avoid NPE
        for (String buffId : List.of("walk_speed", "attack_speed", "mining_speed", "damage_reduction",
                                      "regeneration", "saturation_boost", "knockback_resistance", "jump_height")) {
            MAGNITUDE_MODS.put(buffId, new ArrayList<>());
            HEALTH_MODS.put(buffId, new ArrayList<>());
        }
    }

    private BuffModifiers() {}

    /**
     * Register a magnitude modifier function for a buff type.
     * Modifiers are applied in registration order (first registered = applied first).
     *
     * @param modifierId Unique identifier for this modifier (e.g., "mymod_armor_sync")
     * @param buff      The buff type ID to hook (e.g., "walk_speed")
     * @param modifier  The modification function
     * @throws IllegalArgumentException if modifierId already registered
     */
    public static void registerMagnitudeModifier(String modifierId, String buff, MagnitudeModifier modifier) {
        Objects.requireNonNull(modifierId, "modifierId cannot be null");
        Objects.requireNonNull(buff, "buff cannot be null");
        Objects.requireNonNull(modifier, "modifier cannot be null");

        if (MAGNITUDE_MODS.values().stream()
                .flatMap(Collection::stream)
                .anyMatch(m -> m == modifier)) { // Simple reference check to prevent exact duplicates
            throw new IllegalArgumentException("[SeamlessAPI] Modifier " + modifierId + " already registered");
        }

        MAGNITUDE_MODS.computeIfAbsent(buff, k -> new ArrayList<>())
                .add(modifier);
    }

    /**
     * Register a health bonus modifier function for a buff type.
     *
     * @param modifierId Unique identifier for this modifier
     * @param buff      The buff type ID to hook
     * @param modifier  The modification function
     * @throws IllegalArgumentException if modifierId already registered
     */
    public static void registerHealthModifier(String modifierId, String buff, HealthModifier modifier) {
        Objects.requireNonNull(modifierId, "modifierId cannot be null");
        Objects.requireNonNull(buff, "buff cannot be null");
        Objects.requireNonNull(modifier, "modifier cannot be null");

        HEALTH_MODS.computeIfAbsent(buff, k -> new ArrayList<>())
                .add(modifier);
    }

    /**
     * Register a filter that can veto buff application entirely.
     *
     * @param filter The filter function; return false to prevent application
     */
    public static void registerApplicationFilter(ApplicationFilter filter) {
        Objects.requireNonNull(filter, "filter cannot be null");
        FILTERS.add(filter);
    }

    /**
     * Apply all registered magnitude modifiers to a value.
     * Called internally by Advanced Food System.
     *
     * <p><strong>Do not call this from your code.</strong>
     */
    public static double applyMagnitudeModifiers(Object player, String buffId, double baseMagnitude) {
        double result = baseMagnitude;
        var mods = MAGNITUDE_MODS.getOrDefault(buffId, Collections.emptyList());
        for (MagnitudeModifier mod : mods) {
            try {
                result = mod.apply(player, buffId, result);
            } catch (Exception e) {
                // Log but continue with other modifiers
                System.err.println("[SeamlessAPI] Error in magnitude modifier for " + buffId + ": " + e);
            }
        }
        return result;
    }

    /**
     * Apply all registered health modifiers to a value.
     * Called internally by Advanced Food System.
     *
     * <p><strong>Do not call this from your code.</strong>
     */
    public static double applyHealthModifiers(Object player, String buffId, double baseHealth) {
        double result = baseHealth;
        var mods = HEALTH_MODS.getOrDefault(buffId, Collections.emptyList());
        for (HealthModifier mod : mods) {
            try {
                result = mod.apply(player, buffId, result);
            } catch (Exception e) {
                System.err.println("[SeamlessAPI] Error in health modifier for " + buffId + ": " + e);
            }
        }
        return result;
    }

    /**
     * Check if buff should be applied via filters.
     * Called internally by Advanced Food System.
     *
     * <p><strong>Do not call this from your code.</strong>
     */
    public static boolean shouldApplyBuff(Object player, String foodSource, String buffId) {
        for (ApplicationFilter filter : FILTERS) {
            try {
                if (!filter.shouldApply(player, foodSource, buffId)) {
                    return false;
                }
            } catch (Exception e) {
                System.err.println("[SeamlessAPI] Error in application filter: " + e);
            }
        }
        return true;
    }
}
