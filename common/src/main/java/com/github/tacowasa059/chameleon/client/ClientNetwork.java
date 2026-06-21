package com.github.tacowasa059.chameleon.client;

import com.github.tacowasa059.chameleon.ChameleonConfig;
import net.minecraft.client.Minecraft;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Client-only outbound networking hook. Each loader installs a sender during
 * client init; common client code (the editor) calls {@link #sendSkin}.
 * Kept separate from the {@code INetworkHelper} service so no client networking
 * classes are referenced by code that loads on a dedicated server.
 */
public final class ClientNetwork {

    private static Consumer<byte[]> sender = data -> {
    };
    private static IntConsumer poseSender = id -> {
    };
    private static BooleanSupplier modCheck = () -> false;
    private static boolean serverHasMod = false;

    private ClientNetwork() {
    }

    public static void setSender(Consumer<byte[]> s) {
        sender = s;
    }

    public static void setPoseSender(IntConsumer s) {
        poseSender = s;
    }

    /** Tell the server we chose a visual pose (only on a modded server). */
    public static void sendPose(int poseId) {
        if (Minecraft.getInstance().getConnection() == null || !serverHasMod) {
            return;
        }
        poseSender.accept(poseId);
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
    // The interval is configurable (ChameleonConfig.sendIntervalTicks).
    private static byte[] pending;
    private static int sinceSend = Integer.MAX_VALUE; // start "ready" so the first edit goes out promptly
    private static int serverSendInterval = -1;       // server-provided override (-1 = use local config)

    /** Queue the latest skin to send; only the newest is kept until it flushes. */
    public static void queueSkin(byte[] data) {
        pending = data;
    }

    /** The connected server told us which send interval to use (takes precedence). */
    public static void applyServerSendInterval(int ticks) {
        serverSendInterval = Math.max(1, ticks);
    }

    /** Per client tick: send the queued skin at most once per interval. */
    public static void tick() {
        if (Minecraft.getInstance().getConnection() == null) {
            serverSendInterval = -1; // disconnected -> fall back to the local config value
        }
        int interval = serverSendInterval > 0 ? serverSendInterval : ChameleonConfig.sendIntervalTicks;
        if (sinceSend < interval) {
            sinceSend++;
        }
        if (pending != null && sinceSend >= interval) {
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
