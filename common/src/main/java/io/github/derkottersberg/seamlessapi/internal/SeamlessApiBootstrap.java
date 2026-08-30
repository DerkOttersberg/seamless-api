package io.github.derkottersberg.seamlessapi.internal;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

/** Loader-neutral initialization for Seamless API. */
public final class SeamlessApiBootstrap {
    public static final String MOD_ID = "seamlessapi";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private SeamlessApiBootstrap() {
    }

    public static void initialize(PlatformServices platform) {
        Objects.requireNonNull(platform, "platform");
        if (INITIALIZED.compareAndSet(false, true)) {
            LOGGER.info("Seamless API initialized on {}.", platform.loaderName());
        }
    }
}
