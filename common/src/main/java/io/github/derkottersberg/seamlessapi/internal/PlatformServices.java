package io.github.derkottersberg.seamlessapi.internal;

/** Loader details explicitly supplied by the platform entrypoint. */
@FunctionalInterface
public interface PlatformServices {
    String loaderName();
}
