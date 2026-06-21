package com.github.tacowasa059.chameleon.mixin.client;

import com.github.tacowasa059.chameleon.client.InWorldPaint;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Capture the EXACT model-root matrix the renderer is about to draw the local
 * player's body with -- taken right before {@code model.renderToBuffer}, so it
 * already contains the camera view, the entity translate, setupRotations (body
 * yaw AND the swim/crawl/crouch/sleep tilts) and the 1/16 + (-1.501) scaling.
 *
 * <p>The picker projects with this captured matrix instead of reconstructing the
 * body transform, so the click target matches the rendered body in EVERY pose --
 * the reconstruction approach couldn't replicate poses like swimming/crawling.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void chameleon$captureRoot(LivingEntity entity, float entityYaw, float partialTick,
                                       PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                       CallbackInfo ci) {
        if (InWorldPaint.isActive() && InWorldPaint.isSelf(entity)) {
            InWorldPaint.captureModelRoot(poseStack.last().pose());
        }
    }
}
