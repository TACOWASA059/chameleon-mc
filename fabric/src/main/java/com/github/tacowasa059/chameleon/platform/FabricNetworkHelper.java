package com.github.tacowasa059.chameleon.platform;

import com.github.tacowasa059.chameleon.net.ChameleonNetwork;
import com.github.tacowasa059.chameleon.platform.services.INetworkHelper;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class FabricNetworkHelper implements INetworkHelper {

    @Override
    public void sendSkinToClient(ServerPlayer target, UUID owner, byte[] skinData) {
        ServerPlayNetworking.send(target, ChameleonNetwork.SYNC_SKIN, write(owner, skinData));
    }

    @Override
    public void broadcastSkin(MinecraftServer server, UUID owner, byte[] skinData) {
        // Skip the owner -- they already applied their own skin locally.
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.getUUID().equals(owner)) {
                ServerPlayNetworking.send(p, ChameleonNetwork.SYNC_SKIN, write(owner, skinData));
            }
        }
    }

    @Override
    public void sendConfigToClient(ServerPlayer target, int sendIntervalTicks, int allowedPoseMask) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(sendIntervalTicks);
        buf.writeVarInt(allowedPoseMask);
        ServerPlayNetworking.send(target, ChameleonNetwork.SYNC_CONFIG, buf);
    }

    @Override
    public void sendPoseToClient(ServerPlayer target, UUID owner, int poseId) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUUID(owner);
        buf.writeVarInt(poseId);
        ServerPlayNetworking.send(target, ChameleonNetwork.SYNC_POSE, buf);
    }

    private static FriendlyByteBuf write(UUID owner, byte[] data) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUUID(owner);
        buf.writeByteArray(data);
        return buf;
    }
}
