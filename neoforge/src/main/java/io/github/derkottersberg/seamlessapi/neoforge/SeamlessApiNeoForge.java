package io.github.derkottersberg.seamlessapi.neoforge;

import io.github.derkottersberg.seamlessapi.internal.SeamlessApiBootstrap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(SeamlessApiBootstrap.MOD_ID)
public final class SeamlessApiNeoForge {
    public SeamlessApiNeoForge(IEventBus modEventBus) {
        SeamlessApiBootstrap.initialize(() -> "NeoForge");
    }
}
