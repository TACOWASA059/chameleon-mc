package com.github.tacowasa059.chameleon.net;

import com.github.tacowasa059.chameleon.ChameleonConfig;
import com.github.tacowasa059.chameleon.Constants;
import com.github.tacowasa059.chameleon.platform.Services;
import com.github.tacowasa059.chameleon.skin.ChameleonSkin;
import com.github.tacowasa059.chameleon.skin.SkinPersistence;
import com.github.tacowasa059.chameleon.skin.SkinStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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

    /** Called when a player joins: send them every known skin so they render correctly. */
    public static void onPlayerJoin(ServerPlayer player) {
        // Hand the client the server's send interval so it uses the server's policy.
        Services.NETWORK.sendConfigToClient(player, ChameleonConfig.sendIntervalTicks);
        for (Map.Entry<UUID, byte[]> e : SkinStore.all().entrySet()) {
            Services.NETWORK.sendSkinToClient(player, e.getKey(), e.getValue());
        }
    }

    /** Push the current send interval to every connected client (after a command change). */
    public static void broadcastConfig(MinecraftServer server, int sendIntervalTicks) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Services.NETWORK.sendConfigToClient(p, sendIntervalTicks);
        }
    }
}
