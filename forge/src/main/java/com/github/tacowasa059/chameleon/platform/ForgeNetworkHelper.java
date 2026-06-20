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
        ForgePackets.sendToAll(owner, skinData);
    }
}
