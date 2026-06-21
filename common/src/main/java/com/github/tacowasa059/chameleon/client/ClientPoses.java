package com.github.tacowasa059.chameleon.client;

import com.github.tacowasa059.chameleon.ChameleonPose;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of every player's chosen visual pose (synced from the server)
 * plus the server's allowed-pose set (for the radial wheel). The render mixins read
 * {@link #get} each frame to pose the model.
 */
public final class ClientPoses {

    private static final Map<UUID, ChameleonPose> POSES = new ConcurrentHashMap<>();
    private static int allowedMask = ChameleonPose.defaultMask();

    private ClientPoses() {
    }

    /** Server told us an owner's pose (STAND = clear). */
    public static void receive(UUID owner, int poseId) {
        ChameleonPose p = ChameleonPose.byId(poseId);
        if (p == ChameleonPose.STAND) {
            POSES.remove(owner);
        } else {
            POSES.put(owner, p);
        }
    }

    public static ChameleonPose get(UUID owner) {
        return POSES.get(owner);
    }

    public static void setAllowed(int mask) {
        allowedMask = mask;
    }

    public static boolean isAllowed(ChameleonPose p) {
        return p == ChameleonPose.STAND || (allowedMask & p.bit()) != 0;
    }

    /** Local player picks a pose: apply locally for instant feedback and tell the server. */
    public static void choose(ChameleonPose pose) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        UUID self = mc.player.getUUID();
        if (pose == ChameleonPose.STAND) {
            POSES.remove(self);
        } else {
            POSES.put(self, pose);
        }
        ClientNetwork.sendPose(pose.ordinal());
    }

    /** Reset on world change; the server re-syncs current poses on join. */
    public static void clear() {
        POSES.clear();
    }
}
