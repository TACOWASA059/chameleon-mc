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

    // Debounce outbound skin updates so a burst of strokes becomes at most one
    // send per interval (a server full of painters won't get a packet per stroke).
    private static final int SEND_INTERVAL = 10; // ~0.5s at 20 tps
    private static byte[] pending;
    private static int sinceSend = SEND_INTERVAL;

    /** Queue the latest skin to send; only the newest is kept until it flushes. */
    public static void queueSkin(byte[] data) {
        pending = data;
    }

    /** Per client tick: send the queued skin at most once per interval. */
    public static void tick() {
        if (sinceSend < SEND_INTERVAL) {
            sinceSend++;
        }
        if (pending != null && sinceSend >= SEND_INTERVAL) {
            flush();
        }
    }

    /** Send the queued skin immediately (e.g. when closing a paint screen). */
    public static void flush() {
        if (pending == null) {
            return;
        }
        byte[] data = pending;
        pending = null;
        sinceSend = 0;
        sendSkin(data);
    }
}
