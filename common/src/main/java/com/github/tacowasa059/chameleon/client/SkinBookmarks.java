package com.github.tacowasa059.chameleon.client;

import com.github.tacowasa059.chameleon.Constants;
import com.github.tacowasa059.chameleon.skin.ChameleonSkin;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A small client-side library of saved skins ("bookmarks"). Slots are persisted
 * as raw {@link ChameleonSkin} bytes under {@code <gameDir>/chameleon/bookmarks/},
 * so they survive client restarts and let players stash skins temporarily.
 */
public final class SkinBookmarks {

    public static final int SLOTS = 8;

    private static final ChameleonSkin[] CACHE = new ChameleonSkin[SLOTS];
    private static final ResourceLocation[] TEX = new ResourceLocation[SLOTS];
    private static boolean loaded = false;

    private SkinBookmarks() {
    }

    private static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("chameleon").resolve("bookmarks");
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        for (int i = 0; i < SLOTS; i++) {
            Path p = dir().resolve(i + ".skin");
            try {
                CACHE[i] = Files.exists(p) ? ChameleonSkin.fromBytes(Files.readAllBytes(p)) : null;
            } catch (Exception e) {
                CACHE[i] = null;
                Constants.LOG.warn("Bad skin bookmark {}: {}", i, e.toString());
            }
        }
    }

    public static boolean has(int i) {
        ensureLoaded();
        return i >= 0 && i < SLOTS && CACHE[i] != null;
    }

    public static ChameleonSkin get(int i) {
        ensureLoaded();
        return (i >= 0 && i < SLOTS) ? CACHE[i] : null;
    }

    /** A GPU texture of slot i's skin for thumbnail rendering (uploaded lazily). */
    public static ResourceLocation texture(int i) {
        ensureLoaded();
        if (i < 0 || i >= SLOTS || CACHE[i] == null) {
            return null;
        }
        if (TEX[i] != null) {
            return TEX[i];
        }
        ChameleonSkin sk = CACHE[i];
        boolean[] base = com.github.tacowasa059.chameleon.client.editor.SkinGeometry.baseMask(sk.slim());
        NativeImage img = new NativeImage(ChameleonSkin.SIZE, ChameleonSkin.SIZE, false);
        for (int y = 0; y < ChameleonSkin.SIZE; y++) {
            for (int x = 0; x < ChameleonSkin.SIZE; x++) {
                int argb = sk.get(x, y);
                int a = base[y * ChameleonSkin.SIZE + x] ? 0xFF : (argb >>> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r); // NativeImage = ABGR
            }
        }
        DynamicTexture tex = new DynamicTexture(img);
        ResourceLocation rl = new ResourceLocation(Constants.MOD_ID, "bookmark_" + i);
        Minecraft.getInstance().getTextureManager().register(rl, tex);
        tex.setFilter(false, false);
        TEX[i] = rl;
        return rl;
    }

    /** Save a skin into a slot and write it to disk. */
    public static void set(int i, ChameleonSkin skin) {
        ensureLoaded();
        if (i < 0 || i >= SLOTS) {
            return;
        }
        CACHE[i] = skin;
        if (TEX[i] != null) {
            Minecraft.getInstance().getTextureManager().release(TEX[i]); // rebuild on next render
            TEX[i] = null;
        }
        try {
            Files.createDirectories(dir());
            Files.write(dir().resolve(i + ".skin"), skin.toBytes());
        } catch (IOException e) {
            Constants.LOG.warn("Failed to save skin bookmark {}: {}", i, e.toString());
        }
    }
}
