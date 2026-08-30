package com.derko.seamlessapi.api.visual;

import java.util.Set;

/**
 * Shared defaults for thrown-item visual tuning and small-item classification.
 */
public record ThrownItemVisualProfile(
    float thrownItemScale,
    float smallItemScale,
    float pointFirstPitchBias,
    Set<String> smallItemPaths
) {
    public static ThrownItemVisualProfile swordthrowDefaults() {
        return new ThrownItemVisualProfile(
            0.96F,
            0.72F,
            -45.0F,
            Set.of(
                "egg",
                "sugar",
                "wheat",
                "wheat_seeds",
                "beetroot_seeds",
                "melon_seeds",
                "pumpkin_seeds",
                "cocoa_beans",
                "nether_wart",
                "brown_mushroom",
                "red_mushroom",
                "kelp",
                "dried_kelp",
                "sweet_berries",
                "glow_berries",
                "rabbit_foot",
                "rabbit_hide",
                "spider_eye",
                "fermented_spider_eye",
                "slime_ball",
                "magma_cream",
                "ghast_tear",
                "blaze_powder",
                "blaze_rod",
                "bone_meal",
                "paper",
                "gunpowder",
                "prismarine_shard",
                "prismarine_crystals",
                "nautilus_shell"
            )
        );
    }

    public boolean isSmallItemPath(String itemPath) {
        return smallItemPaths.contains(itemPath);
    }

    public float resolveScale(boolean forceSmallVisual) {
        return forceSmallVisual ? smallItemScale : thrownItemScale;
    }
}
