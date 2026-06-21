package com.github.tacowasa059.chameleon;

import java.util.Locale;

/**
 * A synced, VISUAL-only body pose a player can strike (camouflage). It changes how
 * the player model is rendered for everyone, NOT the real hitbox/state.
 *
 * <p>{@link #STAND} is the default ("no pose" / clear) and is always available; the
 * other four are gated by the server config ({@code allowedPoses}). Serialized by
 * {@link #ordinal()}; a bitmask ({@link #bit()}) carries the allowed set.
 */
public enum ChameleonPose {
    STAND,
    CROUCH,
    CRAWL,
    SIT,
    LIE;

    public static final ChameleonPose[] VALUES = values();

    public static ChameleonPose byId(int id) {
        return (id >= 0 && id < VALUES.length) ? VALUES[id] : STAND;
    }

    public static ChameleonPose byName(String name) {
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Bit for this pose in the allowed-set mask. */
    public int bit() {
        return 1 << ordinal();
    }

    /** STAND is the clear/default; the rest are the selectable, config-gated poses. */
    public boolean selectable() {
        return this != STAND;
    }

    /** Translation key for the pose label (e.g. on the radial wheel). */
    public String key() {
        return "pose.chameleon." + name().toLowerCase(Locale.ROOT);
    }

    /** Default allowed set: every selectable pose. */
    public static int defaultMask() {
        int m = 0;
        for (ChameleonPose p : VALUES) {
            if (p.selectable()) {
                m |= p.bit();
            }
        }
        return m;
    }
}
