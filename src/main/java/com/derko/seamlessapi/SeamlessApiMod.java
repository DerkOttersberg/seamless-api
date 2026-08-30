package com.derko.seamlessapi;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** NeoForge entrypoint for the loader-neutral Seamless API contracts. */
@Mod(SeamlessApiMod.MOD_ID)
public final class SeamlessApiMod {
    public static final String MOD_ID = "seamlessapi";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SeamlessApiMod(IEventBus modEventBus) {
        LOGGER.info("Seamless API initialized.");
    }
}
