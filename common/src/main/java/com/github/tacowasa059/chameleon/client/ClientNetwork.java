package com.github.tacowasa059.chameleon.client;

import net.minecraft.client.Minecraft;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Client-only outbound networking hook. Each loader installs a sender during
 * client init; common client code (the editor) calls {@link #sendSkin}.
 * Kept separate from the {@code INetworkHelper} service so no client networking
 * classes are referenced by code that loads on a dedicated server.
 */
public final class ClientNetwork {

    private static Consumer<byte[]> sender = data -> {
    };
    private static BooleanSupplier modCheck = () -> false;
    private static boolean serverHasMod = false;

    private ClientNetwork() {
    }

    public static void setSender(Consumer<byte[]> s) {
        sender = s;
    }

    /** Loader-provided test for whether the connected server speaks our channel. */
    public static void setModCheck(BooleanSupplier check) {
        modCheck = check;
    }

    /** Re-evaluate (cheaply, once per tick) whether the server has the mod. */
    public static void refreshServerHasMod() {
        serverHasMod = modCheck.getAsBoolean();
    }

    /**
     * True only when the server has Chameleon. When false the custom skin must NOT
     * be shown on the in-world player model, otherwise you'd look different to
     * everyone else. The editor still works (edit + save) as a plain editor.
     */
    public static boolean serverHasMod() {
        return serverHasMod;
    }

    public static void sendSkin(byte[] data) {
        // Only send when actually connected; editing stays fully usable offline.
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        sender.accept(data);
    }
}
