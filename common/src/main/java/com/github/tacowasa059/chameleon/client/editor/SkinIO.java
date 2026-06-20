package com.github.tacowasa059.chameleon.client.editor;

import com.github.tacowasa059.chameleon.Constants;
import com.github.tacowasa059.chameleon.skin.ChameleonSkin;
import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/** PNG import/export for skins via native file dialogs. */
public final class SkinIO {

    private static final int N = ChameleonSkin.SIZE;

    private SkinIO() {
    }

    /** Open a PNG and return its pixels as ARGB (length N*N), or null on cancel/error. */
    public static int[] importPng() {
        String path;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.png"));
            filters.flip();
            path = TinyFileDialogs.tinyfd_openFileDialog("Import skin PNG", "", filters, "PNG image (*.png)", false);
        }
        if (path == null) {
            return null;
        }
        try (InputStream in = new FileInputStream(path); NativeImage img = NativeImage.read(in)) {
            // Legacy 64x32 skins -> 64x64 (mirror the right limbs to the left side).
            NativeImage src = img;
            NativeImage converted = null;
            if (img.getWidth() == 64 && img.getHeight() == 32) {
                converted = legacyTo64(img);
                src = converted;
            }
            try {
                int[] out = new int[N * N];
                int w = Math.min(N, src.getWidth());
                int h = Math.min(N, src.getHeight());
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int abgr = src.getPixelRGBA(x, y);
                        int a = (abgr >>> 24) & 0xFF;
                        int b = (abgr >> 16) & 0xFF;
                        int g = (abgr >> 8) & 0xFF;
                        int r = abgr & 0xFF;
                        out[y * N + x] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }
                return out;
            } finally {
                if (converted != null) {
                    converted.close();
                }
            }
        } catch (Exception e) {
            Constants.LOG.warn("Failed to import skin PNG: {}", e.getMessage());
            TinyFileDialogs.tinyfd_messageBox("Chameleon", "Failed to import PNG:\n" + e.getMessage(), "ok", "error", false);
            return null;
        }
    }

    /**
     * Convert a legacy 64x32 skin to 64x64 by mirroring the right arm/leg into the
     * left arm/leg slots. Copy rectangles are ported 1:1 from vanilla
     * {@code HttpTexture.processLegacySkin} (verified against decompiled 1.20.1).
     */
    private static NativeImage legacyTo64(NativeImage legacy) {
        NativeImage out = new NativeImage(64, 64, true);
        out.fillRect(0, 0, 64, 64, 0);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                out.setPixelRGBA(x, y, legacy.getPixelRGBA(x, y));
            }
        }
        out.copyRect(4, 16, 16, 32, 4, 4, true, false);
        out.copyRect(8, 16, 16, 32, 4, 4, true, false);
        out.copyRect(0, 20, 24, 32, 4, 12, true, false);
        out.copyRect(4, 20, 16, 32, 4, 12, true, false);
        out.copyRect(8, 20, 8, 32, 4, 12, true, false);
        out.copyRect(12, 20, 16, 32, 4, 12, true, false);
        out.copyRect(44, 16, -8, 32, 4, 4, true, false);
        out.copyRect(48, 16, -8, 32, 4, 4, true, false);
        out.copyRect(40, 20, 0, 32, 4, 12, true, false);
        out.copyRect(44, 20, -8, 32, 4, 12, true, false);
        out.copyRect(48, 20, -16, 32, 4, 12, true, false);
        out.copyRect(52, 20, -8, 32, 4, 12, true, false);
        return out;
    }

    /** Save the given ARGB pixels (length N*N) as a 64x64 PNG. */
    public static void exportPng(int[] pixels) {
        String path;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.png"));
            filters.flip();
            path = TinyFileDialogs.tinyfd_saveFileDialog("Export skin PNG", "chameleon-skin.png", filters, "PNG image (*.png)");
        }
        if (path == null) {
            return;
        }
        if (!path.toLowerCase().endsWith(".png")) {
            path = path + ".png";
        }
        try (NativeImage img = new NativeImage(N, N, false)) {
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    int argb = pixels[y * N + x];
                    int a = (argb >>> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }
            img.writeToFile(new File(path));
        } catch (Exception e) {
            Constants.LOG.warn("Failed to export skin PNG: {}", e.getMessage());
            TinyFileDialogs.tinyfd_messageBox("Chameleon", "Failed to export PNG:\n" + e.getMessage(), "ok", "error", false);
        }
    }
}
