package com.github.tacowasa059.chameleon.mixin.client;

import com.github.tacowasa059.chameleon.client.InWorldPaint;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While in-world paint mode is active, override the camera to orbit the local
 * player so every side of the body is reachable. setRotation/setPosition are the
 * vanilla setters (protected) and can only be called from inside Camera, which is
 * why this lives in a mixin rather than InWorldPaint.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "setup", at = @At("TAIL"))
    private void chameleon$orbit(BlockGetter area, Entity entity, boolean detached,
                                 boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (!InWorldPaint.isActive() || Minecraft.getInstance().player == null) {
            return;
        }
        this.setRotation(InWorldPaint.cameraYaw(), InWorldPaint.cameraPitch());
        double[] pos = InWorldPaint.cameraPosition(partialTick);
        this.setPosition(pos[0], pos[1], pos[2]);
    }
}
