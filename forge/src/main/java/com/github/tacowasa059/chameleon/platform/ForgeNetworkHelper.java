package com.github.tacowasa059.chameleon.platform;

import com.github.tacowasa059.chameleon.net.ForgePackets;
import com.github.tacowasa059.chameleon.platform.services.INetworkHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class ForgeNetworkHelper implements INetworkHelper {

    @Override
    public void sendSkinToClient(ServerPlayer target, UUID owner, byte[] skinData) {
        ForgePackets.sendToPlayer(target, owner, skinData);
    }

    @Override
    public void broadcastSkin(MinecraftServer server, UUID owner, byte[] skinData) {
        // Skip the owner -- they already applied their own skin locally.
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.getUUID().equals(owner)) {
                ForgePackets.sendToPlayer(p, owner, skinData);
            }
        }
    }

    @Override
    public void sendConfigToClient(ServerPlayer target, int sendIntervalTicks, int allowedPoseMask) {
        ForgePackets.sendConfigToPlayer(target, sendIntervalTicks, allowedPoseMask);
    }

    @Override
    public void sendPoseToClient(ServerPlayer target, UUID owner, int poseId) {
        ForgePackets.sendPoseToPlayer(target, owner, poseId);
    }
}
