package com.github.tacowasa059.chameleon;

import com.github.tacowasa059.chameleon.net.ChameleonNetwork;
import com.github.tacowasa059.chameleon.skin.SkinPersistence;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class ChameleonFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ChameleonMod.init();

        ServerPlayNetworking.registerGlobalReceiver(ChameleonNetwork.UPDATE_SKIN,
                (server, player, handler, buf, responseSender) -> {
                    byte[] data = buf.readByteArray();
                    server.execute(() -> ChameleonNetwork.serverReceiveUpdate(player, data));
                });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ChameleonNetwork.onPlayerJoin(handler.player));

        ServerLifecycleEvents.SERVER_STARTED.register(SkinPersistence::load);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ChameleonCommand.register(dispatcher));

        // Batch queued skin writes every ~5s, and flush anything left on shutdown.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % ChameleonConfig.saveIntervalTicks == 0) {
                SkinPersistence.flush();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> SkinPersistence.flush());
    }
}
