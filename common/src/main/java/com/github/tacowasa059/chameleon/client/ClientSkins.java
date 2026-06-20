package com.github.tacowasa059.chameleon.client;

import com.github.tacowasa059.chameleon.Constants;
import com.github.tacowasa059.chameleon.skin.ChameleonSkin;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of every player's custom skin and the GPU textures built
 * from them. Texture upload/release happens lazily on the render thread.
 */
public final class ClientSkins {

    private static final Map<UUID, ChameleonSkin> SKINS = new ConcurrentHashMap<>();
    private static final Map<UUID, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();

    private ClientSkins() {
    }

    /** Called from the loader-specific client packet receiver. Empty data = removed. */
    public static void receiveSync(UUID owner, byte[] data) {
        if (data == null || data.length == 0) {
            remove(owner); // owner reverted to their default (vanilla) skin
            return;
        }
        try {
            SKINS.put(owner, ChameleonSkin.fromBytes(data));
            DIRTY.add(owner);
        } catch (Exception e) {
            Constants.LOG.warn("Discarded bad skin sync for {}: {}", owner, e.getMessage());
        }
    }

    /** Apply locally (e.g. the player's own freshly painted skin) for instant feedback. */
    public static void setLocal(UUID owner, ChameleonSkin skin) {
        SKINS.put(owner, skin);
        DIRTY.add(owner);
    }

    public static ChameleonSkin get(UUID owner) {
        return SKINS.get(owner);
    }

    public static boolean has(UUID owner) {
        return SKINS.containsKey(owner);
    }

    /** Whether this owner's custom skin uses the slim (Alex) arm model. */
    public static boolean isSlim(UUID owner) {
        ChameleonSkin s = SKINS.get(owner);
        return s != null && s.slim();
    }

    /** Remove a custom skin (e.g. cancelling the editor) and free its texture. */
    public static void remove(UUID owner) {
        SKINS.remove(owner);
        DIRTY.remove(owner);
        release(owner);
    }

    /**
     * Resolve (uploading if needed) the texture for a player. Must be called on
     * the render thread. Returns null if the player has no custom skin.
     */
    public static ResourceLocation textureFor(AbstractClientPlayer player) {
        UUID id = player.getUUID();
        ChameleonSkin skin = SKINS.get(id);
        if (skin == null) {
            return null;
        }
        if (DIRTY.remove(id)) {
            release(id);
        }
        ResourceLocation rl = TEXTURES.get(id);
        if (rl == null) {
            rl = upload(id, skin);
            TEXTURES.put(id, rl);
        }
        return rl;
    }

    private static ResourceLocation upload(UUID id, ChameleonSkin skin) {
        // The inner (base) layer is rendered fully opaque (alpha forced to 1) so the
        // body is never see-through; the stored skin data keeps its original alpha.
        boolean[] base = com.github.tacowasa059.chameleon.client.editor.SkinGeometry.baseMask(skin.slim());
        NativeImage img = new NativeImage(ChameleonSkin.SIZE, ChameleonSkin.SIZE, false);
        for (int y = 0; y < ChameleonSkin.SIZE; y++) {
            for (int x = 0; x < ChameleonSkin.SIZE; x++) {
                int argb = skin.get(x, y);
                int a = base[y * ChameleonSkin.SIZE + x] ? 0xFF : (argb >>> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                // NativeImage stores bytes as R,G,B,A -> packed int is 0xAABBGGRR (ABGR)
                img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        DynamicTexture tex = new DynamicTexture(img);
        ResourceLocation rl = new ResourceLocation(Constants.MOD_ID, "skins/" + id.toString().replace("-", ""));
        Minecraft.getInstance().getTextureManager().register(rl, tex);
        tex.setFilter(false, false); // crisp pixels, no blur
        return rl;
    }

    private static void release(UUID id) {
        ResourceLocation rl = TEXTURES.remove(id);
        if (rl != null) {
            Minecraft.getInstance().getTextureManager().release(rl);
        }
    }
}
