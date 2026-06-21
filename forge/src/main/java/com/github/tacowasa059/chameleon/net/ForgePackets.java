package com.github.tacowasa059.chameleon.net;

import com.github.tacowasa059.chameleon.Constants;
import com.github.tacowasa059.chameleon.client.ClientNetwork;
import com.github.tacowasa059.chameleon.client.ClientSkins;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.function.Supplier;

public final class ForgePackets {

    private static final String VERSION = "1";

    // acceptMissingOr -> the channel is OPTIONAL: clients may connect to servers
    // that don't have the mod (and the other way around) without a version mismatch.
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Constants.MOD_ID, "main"),
            () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION),
            NetworkRegistry.acceptMissingOr(VERSION));

    private ForgePackets() {
    }

    public static void register() {
        int i = 0;
        CHANNEL.registerMessage(i++, UpdateSkinMsg.class, UpdateSkinMsg::encode, UpdateSkinMsg::decode, UpdateSkinMsg::handle);
        CHANNEL.registerMessage(i++, SyncSkinMsg.class, SyncSkinMsg::encode, SyncSkinMsg::decode, SyncSkinMsg::handle);
        CHANNEL.registerMessage(i++, SyncConfigMsg.class, SyncConfigMsg::encode, SyncConfigMsg::decode, SyncConfigMsg::handle);
    }

    public static void sendToServer(byte[] data) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), new UpdateSkinMsg(data));
    }

    public static void sendToPlayer(ServerPlayer player, UUID owner, byte[] data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncSkinMsg(owner, data));
    }

    public static void sendConfigToPlayer(ServerPlayer player, int sendIntervalTicks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncConfigMsg(sendIntervalTicks));
    }

    /** Client -> Server: a freshly painted skin. */
    public static final class UpdateSkinMsg {
        final byte[] data;

        UpdateSkinMsg(byte[] data) {
            this.data = data;
        }

        static void encode(UpdateSkinMsg m, FriendlyByteBuf b) {
            b.writeByteArray(m.data);
        }

        static UpdateSkinMsg decode(FriendlyByteBuf b) {
            return new UpdateSkinMsg(b.readByteArray());
        }

        static void handle(UpdateSkinMsg m, Supplier<NetworkEvent.Context> ctxSup) {
            NetworkEvent.Context ctx = ctxSup.get();
            ctx.enqueueWork(() -> ChameleonNetwork.serverReceiveUpdate(ctx.getSender(), m.data));
            ctx.setPacketHandled(true);
        }
    }

    /** Server -> Client: an owner's skin. */
    public static final class SyncSkinMsg {
        final UUID owner;
        final byte[] data;

        SyncSkinMsg(UUID owner, byte[] data) {
            this.owner = owner;
            this.data = data;
        }

        static void encode(SyncSkinMsg m, FriendlyByteBuf b) {
            b.writeUUID(m.owner);
            b.writeByteArray(m.data);
        }

        static SyncSkinMsg decode(FriendlyByteBuf b) {
            return new SyncSkinMsg(b.readUUID(), b.readByteArray());
        }

        static void handle(SyncSkinMsg m, Supplier<NetworkEvent.Context> ctxSup) {
            NetworkEvent.Context ctx = ctxSup.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientSkins.receiveSync(m.owner, m.data)));
            ctx.setPacketHandled(true);
        }
    }

    /** Server -> Client: the send interval the server wants clients to use. */
    public static final class SyncConfigMsg {
        final int sendIntervalTicks;

        SyncConfigMsg(int sendIntervalTicks) {
            this.sendIntervalTicks = sendIntervalTicks;
        }

        static void encode(SyncConfigMsg m, FriendlyByteBuf b) {
            b.writeVarInt(m.sendIntervalTicks);
        }

        static SyncConfigMsg decode(FriendlyByteBuf b) {
            return new SyncConfigMsg(b.readVarInt());
        }

        static void handle(SyncConfigMsg m, Supplier<NetworkEvent.Context> ctxSup) {
            NetworkEvent.Context ctx = ctxSup.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientNetwork.applyServerSendInterval(m.sendIntervalTicks)));
            ctx.setPacketHandled(true);
        }
    }
}
