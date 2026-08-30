package com.derko.seamlessapi;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/** Forge entrypoint for the loader-neutral Seamless API contracts. */
@Mod(SeamlessApiMod.MOD_ID)
public final class SeamlessApiMod {
    public static final String MOD_ID = "seamlessapi";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SeamlessApiMod(FMLJavaModLoadingContext context) {
        LOGGER.info("Seamless API initialized.");
    }
}
