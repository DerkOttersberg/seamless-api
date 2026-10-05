package qa.runtime.mixin;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Deterministic vanilla model variants in QA only; never injects animation poses. */
@Mixin(PlayerInfo.class)
public abstract class QaSkinModelMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void qa$model(CallbackInfoReturnable<PlayerSkin> callback) {
        String name = ((PlayerInfo) (Object) this).getProfile().getName();
        if (!name.equals("QA_A") && !name.equals("QA_B")) return;
        PlayerSkin skin = callback.getReturnValue();
        PlayerSkin.Model model = name.equals("QA_A") ? PlayerSkin.Model.WIDE : PlayerSkin.Model.SLIM;
        callback.setReturnValue(new PlayerSkin(skin.texture(), skin.textureUrl(),
            skin.capeTexture(), skin.elytraTexture(), model, skin.secure()));
    }
}
