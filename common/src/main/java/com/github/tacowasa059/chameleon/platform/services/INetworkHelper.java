package com.github.tacowasa059.chameleon.platform.services;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Server-safe networking abstraction. Implementations on each loader send the
 * skin sync packet to clients. Client -> server sending is handled separately
 * by the client-only {@code ClientNetwork} so that no client classes leak into
 * code that is loaded on a dedicated server.
 */
public interface INetworkHelper {

    /** Send a single owner's skin to one specific client. */
    void sendSkinToClient(ServerPlayer target, UUID owner, byte[] skinData);

    /** Broadcast an owner's skin to every connected client. */
    void broadcastSkin(MinecraftServer server, UUID owner, byte[] skinData);

    /** Tell one client the config it should use (send interval + allowed-pose mask). */
    void sendConfigToClient(ServerPlayer target, int sendIntervalTicks, int allowedPoseMask);

    /** Tell one client an owner's chosen visual pose (by ordinal). */
    void sendPoseToClient(ServerPlayer target, UUID owner, int poseId);
}
