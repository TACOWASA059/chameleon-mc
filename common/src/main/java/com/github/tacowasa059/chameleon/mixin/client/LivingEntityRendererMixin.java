package com.github.tacowasa059.chameleon.mixin.client;

import com.github.tacowasa059.chameleon.ChameleonPose;
import com.github.tacowasa059.chameleon.client.ClientPoses;
import com.github.tacowasa059.chameleon.client.InWorldPaint;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
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

    /**
     * Lay the whole body down for the CRAWL/LIE/SIT visual poses (same place vanilla
     * tilts a swimming entity). Runs only when the real pose is STANDING, so it never
     * fights an actual swim/sleep. The model-root capture above happens after this,
     * so the in-world painter sees the posed body too.
     */
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void chameleon$poseRotations(LivingEntity entity, PoseStack poseStack, float ageInTicks,
                                         float rotationYaw, float partialTicks, CallbackInfo ci) {
        if (!(entity instanceof Player) || entity.getPose() != Pose.STANDING) {
            return;
        }
        ChameleonPose pose = ClientPoses.get(entity.getUUID());
        if (pose == null || pose == ChameleonPose.STAND) {
            return;
        }
        switch (pose) {
            case CRAWL -> {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                poseStack.translate(0.0F, -1.0F, 0.3F);
            }
            case LIE -> {
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                poseStack.translate(0.0F, -1.0F, -0.3F);
            }
            case SIT -> poseStack.translate(0.0F, -0.55F, 0.0F);
            default -> {
                // CROUCH is handled entirely by the setupAnim limb offsets
            }
        }
    }
}
