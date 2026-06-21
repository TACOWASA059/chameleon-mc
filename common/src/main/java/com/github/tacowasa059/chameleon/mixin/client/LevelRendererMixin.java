package com.github.tacowasa059.chameleon.mixin.client;

import com.github.tacowasa059.chameleon.client.InWorldPaint;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hand the in-world painter the EXACT camera matrices the world render is about
 * to use -- the modelview (poseStack) and the projection, plus the camera
 * position. The picker then projects with these instead of reconstructing them,
 * so the click target can't drift from the rendered body via any reconstruction
 * gap (fov modifier, view bob, aspect rounding, ...).
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void chameleon$captureMatrices(PoseStack poseStack, float partialTick, long finishNanoTime,
                                           boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                           LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (InWorldPaint.isActive()) {
            InWorldPaint.captureProjection(projectionMatrix);
        }
    }
}
