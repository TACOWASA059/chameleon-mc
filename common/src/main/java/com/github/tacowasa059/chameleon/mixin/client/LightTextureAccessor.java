package com.github.tacowasa059.chameleon.mixin.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the lightmap's per-cell RGB image so the eyedropper can read the exact
 * colour multiplier the body will be lit with and divide the sampled colour by it
 * per channel (block light is warm, sky light cool -- a single scalar gets the
 * brightness but not the hue).
 */
@Mixin(LightTexture.class)
public interface LightTextureAccessor {

    @Accessor("lightPixels")
    NativeImage getLightPixels();
}
