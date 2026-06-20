package com.github.tacowasa059.chameleon.mixin.client;

import com.github.tacowasa059.chameleon.client.ClientNetwork;
import com.github.tacowasa059.chameleon.client.ClientSkins;
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
        // Only override in-world when the server has the mod, so you never look
        // different from what everyone else sees. Offline/vanilla servers = plain editor.
        if (!ClientNetwork.serverHasMod()) {
            return;
        }
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
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
        if (!ClientNetwork.serverHasMod()) {
            return;
        }
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        if (ClientSkins.has(self.getUUID())) {
            cir.setReturnValue(ClientSkins.isSlim(self.getUUID()) ? "slim" : "default");
        }
    }
}
