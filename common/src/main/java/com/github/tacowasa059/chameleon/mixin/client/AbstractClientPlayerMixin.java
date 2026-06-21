package com.github.tacowasa059.chameleon.mixin.client;

import com.github.tacowasa059.chameleon.client.ClientNetwork;
import com.github.tacowasa059.chameleon.client.ClientSkins;
import com.github.tacowasa059.chameleon.client.InWorldPaint;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides the skin texture used to render a player when a custom Chameleon
 * skin is available for them.
 */
@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {

    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void chameleon$customSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        // Only override in-world when the server has the mod, so you never look
        // different from what everyone else sees. Offline/vanilla servers = plain editor.
        // Exception: in-world paint mode previews YOUR OWN skin locally regardless,
        // so you can paint against the real surroundings even on a vanilla server.
        boolean selfPaint = InWorldPaint.isActive() && InWorldPaint.isSelf(self);
        if (!ClientNetwork.serverHasMod() && !selfPaint) {
            return;
        }
        if (ClientSkins.has(self.getUUID())) {
            ResourceLocation rl = ClientSkins.textureFor(self);
            if (rl != null) {
                cir.setReturnValue(rl);
            }
        }
    }

    /** Make the slim/wide arm model chosen in the editor show for everyone. */
    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void chameleon$modelName(CallbackInfoReturnable<String> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        boolean selfPaint = InWorldPaint.isActive() && InWorldPaint.isSelf(self);
        if (!ClientNetwork.serverHasMod() && !selfPaint) {
            return;
        }
        if (ClientSkins.has(self.getUUID())) {
            cir.setReturnValue(ClientSkins.isSlim(self.getUUID()) ? "slim" : "default");
        }
    }
}
