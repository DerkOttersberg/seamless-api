package io.github.derkottersberg.seamlessapi.fabric;

import io.github.derkottersberg.seamlessapi.internal.SeamlessApiBootstrap;
import net.fabricmc.api.ModInitializer;

public final class SeamlessApiFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SeamlessApiBootstrap.initialize(() -> "Fabric");
    }
}
