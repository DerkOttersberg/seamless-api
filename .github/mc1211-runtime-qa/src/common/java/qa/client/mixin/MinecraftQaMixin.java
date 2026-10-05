package qa.client.mixin;

import qa.client.IsolatedQa;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftQaMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void isolatedQaTick(CallbackInfo ci) { IsolatedQa.tick(Minecraft.getInstance()); }
}
