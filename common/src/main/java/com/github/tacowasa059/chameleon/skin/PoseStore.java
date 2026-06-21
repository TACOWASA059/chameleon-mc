package com.github.tacowasa059.chameleon.skin;

import com.github.tacowasa059.chameleon.ChameleonPose;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side store of each player's chosen visual pose. In-memory only -- poses
 * are a transient camouflage choice that resets when the player (or server) leaves.
 * STAND means "no pose" and is stored as removal.
 */
public final class PoseStore {

    private static final Map<UUID, ChameleonPose> POSES = new ConcurrentHashMap<>();

    private PoseStore() {
    }

    public static void put(UUID id, ChameleonPose pose) {
        if (pose == null || pose == ChameleonPose.STAND) {
            POSES.remove(id);
        } else {
            POSES.put(id, pose);
        }
    }

    public static ChameleonPose get(UUID id) {
        return POSES.get(id);
    }

    public static Map<UUID, ChameleonPose> all() {
        return POSES;
    }

    public static void remove(UUID id) {
        POSES.remove(id);
    }
}
