package qa.runtime.mixin;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class ClientTickMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void qa$tick(CallbackInfo ci) {
        if (Boolean.getBoolean("qa.menuOnly") || Boolean.getBoolean("qa.individual"))
            qa.client.IsolatedQa.tick((Minecraft)(Object)this);
        else qa.runtime.ClientAcceptance.tick((Minecraft)(Object)this);
    }
}
