package com.github.tacowasa059.chameleon.client;

import com.github.tacowasa059.chameleon.net.ChameleonNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public class ChameleonFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(ChameleonClient.OPEN_EDITOR);
        KeyBindingHelper.registerKeyBinding(ChameleonClient.OPEN_INWORLD_PAINT);
        KeyBindingHelper.registerKeyBinding(ChameleonClient.TOGGLE_GUIDE);

        // Does the connected server speak our channel (i.e. have the mod)?
        ClientNetwork.setModCheck(() -> ClientPlayNetworking.canSend(ChameleonNetwork.UPDATE_SKIN));
        ClientNetwork.setSender(data -> {
            if (!ClientNetwork.serverHasMod()) {
                return;
            }
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeByteArray(data);
            ClientPlayNetworking.send(ChameleonNetwork.UPDATE_SKIN, buf);
        });

        ClientPlayNetworking.registerGlobalReceiver(ChameleonNetwork.SYNC_SKIN,
                (client, handler, buf, responseSender) -> {
                    UUID owner = buf.readUUID();
                    byte[] data = buf.readByteArray();
                    client.execute(() -> ClientSkins.receiveSync(owner, data));
                });

        ClientTickEvents.END_CLIENT_TICK.register(client -> ChameleonClient.clientTick());
    }
}
