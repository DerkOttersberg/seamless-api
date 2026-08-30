package com.derko.seamlessapi.api.deconstruction;

import java.util.Map;

/**
 * Hook that allows another mod to adjust rolled deconstruction output.
 *
 * <p>Return a new map or the same map instance after modifications.
 */
@FunctionalInterface
public interface DeconstructionModifier {
    Map<String, Integer> apply(DeconstructionContext context, Map<String, Integer> currentOutput);
}
