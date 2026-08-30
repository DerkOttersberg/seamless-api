package io.github.derkottersberg.seamlessapi.forge;

import io.github.derkottersberg.seamlessapi.internal.SeamlessApiBootstrap;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SeamlessApiBootstrap.MOD_ID)
public final class SeamlessApiForge {
    public SeamlessApiForge(FMLJavaModLoadingContext context) {
        SeamlessApiBootstrap.initialize(() -> "Forge");
    }
}
