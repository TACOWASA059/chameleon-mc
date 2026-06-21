package com.github.tacowasa059.chameleon.mixin.client;

import com.github.tacowasa059.chameleon.ChameleonPose;
import com.github.tacowasa059.chameleon.client.ClientPoses;
import com.github.tacowasa059.chameleon.client.InWorldPaint;
import com.github.tacowasa059.chameleon.client.editor.SkinGeometry;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In-world paint mode paints on the LOCAL player in their REAL in-world pose
 * (heading, head tilt, idle stance), not a forced straight one. Rather than
 * freezing a snapshot (which let the rendered pose drift from the picked pose),
 * we read the ACTUAL part transforms the renderer just computed -- every frame,
 * at setupAnim TAIL -- into {@link InWorldPaint}. The picker then projects with
 * exactly those values, so the rendered body and the click target can't diverge.
 *
 * <p>Parts are reached by casting {@code this} to PlayerModel and reading its
 * public fields rather than {@code @Shadow} (which can't resolve inherited fields
 * in dev with no refmap).
 */
@Mixin(PlayerModel.class)
public class PlayerModelMixin {

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void chameleon$capturePose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                       float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        PlayerModel<?> m = (PlayerModel<?>) (Object) this;
        chameleon$applyPose(m, entity); // visual camouflage pose (everyone), before capture

        if (!InWorldPaint.isActive() || !InWorldPaint.isSelf(entity)) {
            return;
        }
        grab(SkinGeometry.PART_HEAD, m.head);
        grab(SkinGeometry.PART_BODY, m.body);
        grab(SkinGeometry.PART_RIGHT_ARM, m.rightArm);
        grab(SkinGeometry.PART_LEFT_ARM, m.leftArm);
        grab(SkinGeometry.PART_RIGHT_LEG, m.rightLeg);
        grab(SkinGeometry.PART_LEFT_LEG, m.leftLeg);
        InWorldPaint.setPoseReady(true);
    }

    /**
     * Apply the chosen visual pose's limb config (only while the player's REAL pose
     * is STANDING, so we never fight an actual crouch/swim). The whole-body tilt for
     * CRAWL/LIE/SIT is done in {@code LivingEntityRendererMixin#setupRotations}. Outer
     * (hat/jacket/sleeve/pants) parts are re-copied so they follow the posed base.
     */
    private static void chameleon$applyPose(PlayerModel<?> m, LivingEntity entity) {
        if (!(entity instanceof Player) || entity.getPose() != Pose.STANDING) {
            return;
        }
        ChameleonPose pose = ClientPoses.get(entity.getUUID());
        if (pose == null || pose == ChameleonPose.STAND) {
            return;
        }
        switch (pose) {
            case CROUCH -> {
                // vanilla sneaking pose
                m.body.xRot = 0.5F;
                m.rightArm.xRot += 0.4F;
                m.leftArm.xRot += 0.4F;
                m.rightLeg.z = 4.0F;
                m.leftLeg.z = 4.0F;
                m.rightLeg.y = 12.2F;
                m.leftLeg.y = 12.2F;
                m.head.y = 4.2F;
                m.body.y = 3.2F;
                m.leftArm.y = 5.2F;
                m.rightArm.y = 5.2F;
            }
            case SIT -> {
                // vanilla riding pose (legs bent forward, arms slightly down)
                m.rightArm.xRot += -0.62831855F;
                m.leftArm.xRot += -0.62831855F;
                m.rightLeg.xRot = -1.4137167F;
                m.rightLeg.yRot = 0.31415927F;
                m.rightLeg.zRot = 0.07853982F;
                m.leftLeg.xRot = -1.4137167F;
                m.leftLeg.yRot = -0.31415927F;
                m.leftLeg.zRot = -0.07853982F;
            }
            case CRAWL -> {
                // body laid prone in setupRotations; reach the arms forward
                m.rightArm.xRot = -2.6F;
                m.leftArm.xRot = -2.6F;
                m.rightArm.zRot = 0.1F;
                m.leftArm.zRot = -0.1F;
            }
            case LIE -> {
                // body laid on its back in setupRotations; arms rest at the sides
            }
            default -> {
            }
        }
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftPants.copyFrom(m.leftLeg);
        m.rightPants.copyFrom(m.rightLeg);
    }

    private static void grab(int part, ModelPart p) {
        InWorldPaint.capturePart(part, p.x, p.y, p.z, p.xRot, p.yRot, p.zRot);
    }
}
