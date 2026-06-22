package com.github.tacowasa059.chameleon.net;

import com.github.tacowasa059.chameleon.ChameleonConfig;
import com.github.tacowasa059.chameleon.ChameleonPose;
import com.github.tacowasa059.chameleon.Constants;
import com.github.tacowasa059.chameleon.platform.Services;
import com.github.tacowasa059.chameleon.skin.ChameleonSkin;
import com.github.tacowasa059.chameleon.skin.PoseStore;
import com.github.tacowasa059.chameleon.skin.SkinPersistence;
import com.github.tacowasa059.chameleon.skin.SkinStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * Loader-independent networking logic. The actual packet transport is provided
 * per-loader (Forge SimpleChannel / Fabric networking), but the channel ids and
 * the server-side handlers live here so both loaders share them.
 */
public final class ChameleonNetwork {

    public static final ResourceLocation UPDATE_SKIN = new ResourceLocation(Constants.MOD_ID, "update_skin"); // C -> S
    public static final ResourceLocation SYNC_SKIN = new ResourceLocation(Constants.MOD_ID, "sync_skin");     // S -> C
    public static final ResourceLocation SYNC_CONFIG = new ResourceLocation(Constants.MOD_ID, "sync_config"); // S -> C
    public static final ResourceLocation SET_POSE = new ResourceLocation(Constants.MOD_ID, "set_pose");       // C -> S
    public static final ResourceLocation SYNC_POSE = new ResourceLocation(Constants.MOD_ID, "sync_pose");     // S -> C

    public static final int MAX_BYTES = ChameleonSkin.BYTES;

    private ChameleonNetwork() {
    }

    /** Called on the server when a client uploads a freshly painted skin. */
    public static void serverReceiveUpdate(ServerPlayer sender, byte[] data) {
        if (sender == null || data == null) {
            return;
        }
        if (data.length == 0) {
            // empty = player reverted to their default skin; drop and broadcast removal
            UUID id = sender.getUUID();
            SkinStore.remove(id);
            SkinPersistence.delete(sender.getServer(), id);
            Services.NETWORK.broadcastSkin(sender.getServer(), id, new byte[0]);
            return;
        }
        if (data.length > MAX_BYTES) {
            Constants.LOG.warn("Rejected oversized skin from {} ({} bytes)", sender.getGameProfile().getName(), data.length);
            return;
        }
        try {
            ChameleonSkin.fromBytes(data); // validate only; store raw
        } catch (Exception e) {
            Constants.LOG.warn("Rejected invalid skin from {}: {}", sender.getGameProfile().getName(), e.getMessage());
            return;
        }
        UUID id = sender.getUUID();
        SkinStore.put(id, data);
        SkinPersistence.save(sender.getServer(), id, data); // survive restart
        Services.NETWORK.broadcastSkin(sender.getServer(), id, data);
    }

    /** Called when a player joins: hand them the config and every known skin + pose. */
    public static void onPlayerJoin(ServerPlayer player) {
        Services.NETWORK.sendConfigToClient(player, ChameleonConfig.sendIntervalTicks,
                ChameleonConfig.allowedPoseMask, ChameleonConfig.enableEyedropper);
        for (Map.Entry<UUID, byte[]> e : SkinStore.all().entrySet()) {
            Services.NETWORK.sendSkinToClient(player, e.getKey(), e.getValue());
        }
        for (Map.Entry<UUID, ChameleonPose> e : PoseStore.all().entrySet()) {
            Services.NETWORK.sendPoseToClient(player, e.getKey(), e.getValue().ordinal());
        }
    }

    /** Push the current config (send interval + allowed poses) to every client. */
    public static void broadcastConfig(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Services.NETWORK.sendConfigToClient(p, ChameleonConfig.sendIntervalTicks,
                    ChameleonConfig.allowedPoseMask, ChameleonConfig.enableEyedropper);
        }
    }

    /** Client chose a visual pose: validate against the allowed set, store, broadcast. */
    public static void serverReceivePose(ServerPlayer sender, int poseId) {
        if (sender == null) {
            return;
        }
        ChameleonPose pose = ChameleonPose.byId(poseId);
        if (pose.selectable() && (ChameleonConfig.allowedPoseMask & pose.bit()) == 0) {
            return; // not allowed by the server config
        }
        UUID id = sender.getUUID();
        PoseStore.put(id, pose);
        broadcastPose(sender.getServer(), id, pose);
    }

    /** Send one player's pose to every connected client (including the owner). */
    public static void broadcastPose(MinecraftServer server, UUID owner, ChameleonPose pose) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Services.NETWORK.sendPoseToClient(p, owner, pose.ordinal());
        }
    }

    /** Reset any player whose stored pose is no longer allowed (after a config change). */
    public static void clearDisallowedPoses(MinecraftServer server) {
        for (Map.Entry<UUID, ChameleonPose> e : new ArrayList<>(PoseStore.all().entrySet())) {
            ChameleonPose pose = e.getValue();
            if (pose.selectable() && (ChameleonConfig.allowedPoseMask & pose.bit()) == 0) {
                PoseStore.put(e.getKey(), ChameleonPose.STAND); // removes
                broadcastPose(server, e.getKey(), ChameleonPose.STAND);
            }
        }
    }
}
