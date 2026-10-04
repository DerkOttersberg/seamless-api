package qa.runtime.mixin;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class ClientTickMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void qa$tick(CallbackInfo ci) { qa.runtime.ClientAcceptance.tick((Minecraft)(Object)this); }
}
