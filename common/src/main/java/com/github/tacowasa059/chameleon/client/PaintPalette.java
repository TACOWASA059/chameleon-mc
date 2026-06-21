package com.github.tacowasa059.chameleon.client;

import com.github.tacowasa059.chameleon.Constants;
import net.minecraft.client.Minecraft;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Colours shared by BOTH paint UIs (the K editor and the N in-world mode): the
 * A/B (primary/secondary) working colours, the active swatch, and the recent-
 * colour history. Persisted to disk and loaded by whichever mode opens first, so
 * everything survives restarts for both modes.
 */
public final class PaintPalette {

    public static int primary = 0xFF000000;
    public static int secondary = 0xFFFFFFFF;
    public static int activeSwatch = 0; // 0 = A/primary, 1 = B/secondary

    /** Recent colours, most-recent first. */
    public static final Deque<Integer> COLORS = new ArrayDeque<>();

    private static boolean loaded = false;

    private PaintPalette() {
    }

    public static void push(int color) {
        color = color | 0xFF000000;
        COLORS.remove(color);
        COLORS.addFirst(color);
        while (COLORS.size() > 10) {
            COLORS.removeLast();
        }
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("chameleon").resolve("editor.bin");
    }

    /** Restore A/B colours + history from disk (once per session). */
    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path p = path();
            if (!Files.exists(p)) {
                return;
            }
            ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(p));
            if (b.remaining() < 12) {
                return;
            }
            primary = b.getInt();
            secondary = b.getInt();
            int n = b.getInt();
            COLORS.clear();
            for (int i = 0; i < n && b.remaining() >= 4; i++) {
                COLORS.addLast(b.getInt());
            }
        } catch (Exception e) {
            Constants.LOG.warn("Failed to load palette: {}", e.toString());
        }
    }

    public static void save() {
        try {
            ByteBuffer b = ByteBuffer.allocate(12 + COLORS.size() * 4);
            b.putInt(primary);
            b.putInt(secondary);
            b.putInt(COLORS.size());
            for (int c : COLORS) {
                b.putInt(c);
            }
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.write(p, b.array());
        } catch (Exception e) {
            Constants.LOG.warn("Failed to save palette: {}", e.toString());
        }
    }
}
