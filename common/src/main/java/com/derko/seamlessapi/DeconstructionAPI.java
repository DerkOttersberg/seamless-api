package com.derko.seamlessapi;

import com.derko.seamlessapi.api.deconstruction.DeconstructionContext;
import com.derko.seamlessapi.api.deconstruction.DeconstructionModifier;
import com.derko.seamlessapi.api.deconstruction.DeconstructionRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Generic deconstruction registration and modifier API.
 *
 * <p>This is additive to SatiationAPI and is intended as a shared "Seamless" integration point
 * for mods that expose item breakdown/deconstruction systems.
 */
public final class DeconstructionAPI {
    private static final Map<String, DeconstructionRegistration> PENDING = new ConcurrentHashMap<>();
    private static final List<DeconstructionModifier> MODIFIERS = new CopyOnWriteArrayList<>();
    private static volatile boolean locked = false;

    private DeconstructionAPI() {
    }

    /**
     * Register deconstruction units for an input item.
     */
    public static synchronized void registerDeconstruction(String inputItemId, DeconstructionRegistration registration) {
        if (locked) {
            throw new IllegalStateException(
                    "[SeamlessAPI] Too late to register deconstruction for '" + inputItemId + "'. " +
                    "Call DeconstructionAPI.registerDeconstruction() during setup.");
        }
        if (inputItemId == null || inputItemId.isBlank()) {
            throw new IllegalArgumentException("[SeamlessAPI] inputItemId cannot be null or blank.");
        }
        if (registration == null) {
            throw new IllegalArgumentException("[SeamlessAPI] DeconstructionRegistration cannot be null.");
        }
        PENDING.put(inputItemId, registration);
    }

    /**
     * Register an output modifier hook (priority = registration order).
     */
    public static synchronized void registerModifier(DeconstructionModifier modifier) {
        if (locked) {
            throw new IllegalStateException(
                    "[SeamlessAPI] Too late to register deconstruction modifier. Register during setup.");
        }
        if (modifier == null) {
            throw new IllegalArgumentException("[SeamlessAPI] DeconstructionModifier cannot be null.");
        }
        MODIFIERS.add(modifier);
    }

    /**
     * Host mod call: lock and get immutable registrations.
     */
    public static synchronized Map<String, DeconstructionRegistration> freezeAndGetAll() {
        locked = true;
        return Map.copyOf(PENDING);
    }

    /**
     * Host mod call: lock and get immutable modifiers.
     */
    public static synchronized List<DeconstructionModifier> freezeAndGetModifiers() {
        locked = true;
        return List.copyOf(MODIFIERS);
    }

    /**
     * Convenience helper to run currently registered modifiers in order.
     */
    public static Map<String, Integer> applyModifiers(DeconstructionContext context, Map<String, Integer> output) {
        Map<String, Integer> current = output;
        for (DeconstructionModifier modifier : MODIFIERS) {
            Map<String, Integer> next = modifier.apply(context, current);
            current = next != null ? next : current;
        }
        return current;
    }

    /**
     * Best-effort snapshot used by tooling or tests before freeze.
     */
    public static List<DeconstructionModifier> getRegisteredModifiersSnapshot() {
        return new ArrayList<>(MODIFIERS);
    }

    public static boolean isLocked() {
        return locked;
    }
}
