package com.github.tacowasa059.chameleon.mixin.client;

import com.github.tacowasa059.chameleon.client.InWorldPaint;
import com.github.tacowasa059.chameleon.client.editor.SkinGeometry;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
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
        if (!InWorldPaint.isActive() || !InWorldPaint.isSelf(entity)) {
            return;
        }
        PlayerModel<?> m = (PlayerModel<?>) (Object) this;
        grab(SkinGeometry.PART_HEAD, m.head);
        grab(SkinGeometry.PART_BODY, m.body);
        grab(SkinGeometry.PART_RIGHT_ARM, m.rightArm);
        grab(SkinGeometry.PART_LEFT_ARM, m.leftArm);
        grab(SkinGeometry.PART_RIGHT_LEG, m.rightLeg);
        grab(SkinGeometry.PART_LEFT_LEG, m.leftLeg);
        InWorldPaint.setPoseReady(true);
    }

    private static void grab(int part, ModelPart p) {
        InWorldPaint.capturePart(part, p.x, p.y, p.z, p.xRot, p.yRot, p.zRot);
    }
}
