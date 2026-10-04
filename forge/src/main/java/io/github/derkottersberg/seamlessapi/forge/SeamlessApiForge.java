package io.github.derkottersberg.seamlessapi.forge;

import io.github.derkottersberg.seamlessapi.internal.SeamlessApiBootstrap;
import net.minecraftforge.fml.common.Mod;

@Mod(SeamlessApiBootstrap.MOD_ID)
public final class SeamlessApiForge {
    public SeamlessApiForge() {
        SeamlessApiBootstrap.initialize(() -> "Forge");
    }
}
