package com.derko.seamlessapi.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;

/**
 * Query API for accessing active buff data on players.
 *
 * <p>All methods are thread-safe and server-side only.
 * Call from event handlers or tick loops on the server thread.
 * Example:
 * <pre>
 *   List<BuffData> allBuffs = BuffQueryAPI.getAllBuffs(player);
 *   if (BuffQueryAPI.hasBuffWithId(player, "regeneration")) {
 *       // player has active regen
 *   }
 *   BuffQueryAPI.removeBuff(player, "walk_speed");  // remove by ID
 * </pre>
 */
public final class BuffQueryAPI {

    private BuffQueryAPI() {}

    /**
     * Get all active buffs for a player.
     *
     * @param player The server player
     * @return Immutable list of active buffs, or empty list if none
     */
    public static List<BuffData> getAllBuffs(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        // Delegate to Advanced Food System's internal BuffStorage
        try {
            return invokeBuffStorageGet(player);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Get all active buffs matching a predicate.
     *
     * @param player    The server player
     * @param predicate Filter condition
     * @return Immutable list of matching buffs
     */
    public static List<BuffData> getBuffsMatching(ServerPlayer player, Predicate<BuffData> predicate) {
        return getAllBuffs(player).stream()
                .filter(predicate)
                .toList();
    }

    /**
     * Check if player has any active buff with the given ID.
     *
     * @param player The server player
     * @param buffId The buff type ID (e.g., "walk_speed")
     * @return true if buff is active, false otherwise
     */
    public static boolean hasBuffWithId(ServerPlayer player, String buffId) {
        if (buffId == null || buffId.isBlank()) {
            return false;
        }
        return getAllBuffs(player).stream()
                .anyMatch(b -> b.buffId().equals(buffId));
    }

    /**
     * Get the count of active food buffs (excludes combo buffs).
     *
     * @param player The server player
     * @return Count of food-sourced buffs
     */
    public static int getActiveFoodBuffCount(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        try {
            return (int) invokeBuffStorageGet(player).stream()
                    .filter(b -> !b.foodSource().startsWith("combo:"))
                    .count();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Remove all buffs with a given ID for a player.
     *
     * <p>This is destructive and irreversible. Use with caution.
     * Returns true if at least one buff was removed.
     *
     * @param player The server player
     * @param buffId The buff type ID to remove
     * @return true if one or more buffs were removed
     */
    public static boolean removeBuffsWithId(ServerPlayer player, String buffId) {
        if (player == null || buffId == null || buffId.isBlank()) {
            return false;
        }
        try {
            List<BuffData> currentList = invokeBuffStorageGet(player);
            List<BuffData> afterRemoval = currentList.stream()
                    .filter(b -> !b.buffId().equals(buffId))
                    .toList();
            if (currentList.size() != afterRemoval.size()) {
                invokeBuffStorageSet(player, afterRemoval);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Remove all buffs from a specific food source.
     *
     * @param player     The server player
     * @param foodSource Food registry ID
     * @return true if one or more buffs were removed
     */
    public static boolean removeBuffsFromSource(ServerPlayer player, String foodSource) {
        if (player == null || foodSource == null || foodSource.isBlank()) {
            return false;
        }
        try {
            List<BuffData> currentList = invokeBuffStorageGet(player);
            List<BuffData> afterRemoval = currentList.stream()
                    .filter(b -> !b.foodSource().equals(foodSource))
                    .toList();
            if (currentList.size() != afterRemoval.size()) {
                invokeBuffStorageSet(player, afterRemoval);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get aggregate magnitude of a buff type (sum of all active buffs with that ID).
     *
     * @param player The server player
     * @param buffId The buff type ID
     * @return Sum of magnitudes, or 0 if no such buff
     */
    public static double getAggregateMagnitude(ServerPlayer player, String buffId) {
        return getAllBuffs(player).stream()
                .filter(b -> b.buffId().equals(buffId))
                .mapToDouble(BuffData::magnitude)
                .sum();
    }

    // === Internal helpers ===

    private static List<BuffData> invokeBuffStorageGet(ServerPlayer player) {
        try {
            var clazz = Class.forName("com.derko.advancedfoodsystem.data.BuffStorage");
            var getMethod = clazz.getMethod("get", ServerPlayer.class);
            var result = getMethod.invoke(null, player);
            
            @SuppressWarnings("unchecked")
            List<Object> listOfBuffInstances = (List<Object>) result;
            
            return listOfBuffInstances.stream()
                    .map(BuffQueryAPI::reflectToBuffData)
                    .filter(b -> b != null)
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get buffs from Advanced Food System", e);
        }
    }

    private static BuffData reflectToBuffData(Object buffInstance) {
        try {
            // Reflection-based conversion to bridge Seamless-API with Advanced-Food-System's BuffInstance
            var clazz = buffInstance.getClass();
            var idMethod = clazz.getMethod("id");
            var timeTicksMethod = clazz.getMethod("timeTicks");
            var maxTicksMethod = clazz.getMethod("maxTicks");
            var magnitudeMethod = clazz.getMethod("magnitude");
            var healthBonusMethod = clazz.getMethod("healthBonusHearts");
            var sourceMethod = clazz.getMethod("source");
            var createdMethod = clazz.getMethod("created");

            return new BuffData(
                    (String) idMethod.invoke(buffInstance),
                    (int) timeTicksMethod.invoke(buffInstance),
                    (int) maxTicksMethod.invoke(buffInstance),
                    (double) magnitudeMethod.invoke(buffInstance),
                    (double) healthBonusMethod.invoke(buffInstance),
                    (String) sourceMethod.invoke(buffInstance),
                    (long) createdMethod.invoke(buffInstance)
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static void invokeBuffStorageSet(ServerPlayer player, List<BuffData> buffs) {
        try {
            var clazz = Class.forName("com.derko.advancedfoodsystem.data.BuffStorage");
            var setMethod = clazz.getMethod("set", ServerPlayer.class, List.class);
            
            // Convert BuffData back to BuffInstance objects
            var buffInstanceClass = Class.forName("com.derko.advancedfoodsystem.data.BuffInstance");
            var constructorMethod = buffInstanceClass.getConstructor(
                    String.class, int.class, int.class, double.class, double.class, String.class, long.class
            );
            
            var instanceList = new ArrayList<Object>();
            for (BuffData buff : buffs) {
                var instance = constructorMethod.newInstance(
                        buff.buffId(),
                        buff.remainingTicks(),
                        buff.maxTicks(),
                        buff.magnitude(),
                        buff.healthBonusHearts(),
                        buff.foodSource(),
                        buff.appliedAtGameTime()
                );
                instanceList.add(instance);
            }
            
            setMethod.invoke(null, player, instanceList);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set buffs in Advanced Food System", e);
        }
    }
}
