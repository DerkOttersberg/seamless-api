package qa.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qa.client.RenderedPlayerQa;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererQaMixin {
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void qaCapture(AbstractClientPlayer player, float yaw, float partialTick,
                           PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo callback) {
        RenderedPlayerQa.capture(player, ((PlayerRenderer)(Object)this).getModel());
    }
}
