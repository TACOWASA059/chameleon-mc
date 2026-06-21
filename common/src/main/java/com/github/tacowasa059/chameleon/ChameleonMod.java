package com.github.tacowasa059.chameleon;

import com.github.tacowasa059.chameleon.platform.Services;

/**
 * Shared entry point. Each loader bootstraps the mod by calling {@link #init()}.
 */
public final class ChameleonMod {

    private ChameleonMod() {
    }

    public static void init() {
        Constants.LOG.info("Chameleon initializing on {} ({})",
                Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
        ChameleonConfig.load();
    }
}
