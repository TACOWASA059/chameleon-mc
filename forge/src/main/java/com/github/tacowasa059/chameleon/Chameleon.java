package com.github.tacowasa059.chameleon;

import com.github.tacowasa059.chameleon.client.ChameleonForgeClient;
import com.github.tacowasa059.chameleon.net.ChameleonNetwork;
import com.github.tacowasa059.chameleon.net.ForgePackets;
import com.github.tacowasa059.chameleon.skin.SkinPersistence;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkConstants;

@Mod(Constants.MOD_ID)
public class Chameleon {

    public Chameleon() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ChameleonMod.init();
        ForgePackets.register();
        MinecraftForge.EVENT_BUS.register(this);

        // Chameleon is client-side only: let clients join servers that don't have
        // it (and vice versa) without a "server mismatch" / incompatible error.
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> NetworkConstants.IGNORESERVERONLY,
                        (remoteVersion, isFromServer) -> true));

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ChameleonForgeClient.init(modBus);
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ChameleonNetwork.onPlayerJoin(sp);
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        SkinPersistence.load(event.getServer());
    }

    private int flushCounter;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ++flushCounter % 100 == 0) {
            SkinPersistence.flush(); // batch queued skin writes every ~5s
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        SkinPersistence.flush(); // write anything still queued before shutdown
    }
}
