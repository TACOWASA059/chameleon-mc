package com.github.tacowasa059.chameleon.client;

import com.github.tacowasa059.chameleon.net.ForgePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ChameleonForgeClient {

    private ChameleonForgeClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ChameleonForgeClient::onRegisterKeys);
        MinecraftForge.EVENT_BUS.addListener(ChameleonForgeClient::onClientTick);
        // Does the connected server speak our channel (i.e. have the mod)?
        ClientNetwork.setModCheck(() -> {
            ClientPacketListener conn = Minecraft.getInstance().getConnection();
            return conn != null && ForgePackets.CHANNEL.isRemotePresent(conn.getConnection());
        });
        ClientNetwork.setSender(data -> {
            if (ClientNetwork.serverHasMod()) {
                ForgePackets.sendToServer(data);
            }
        });
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(ChameleonClient.OPEN_EDITOR);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ChameleonClient.clientTick();
        }
    }
}
