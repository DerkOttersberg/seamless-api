package qa.runtime.mixin;
import io.github.derkottersberg.swordthrow.client.SwordThrowClient;
import io.github.derkottersberg.swordthrow.network.ThrowStatePayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=SwordThrowClient.class,remap=false)
public abstract class ThrowStateMixin {
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private static void qa$productionTick(net.minecraft.client.Minecraft client, CallbackInfo ci) { qa.runtime.ClientAcceptance.productionTick(); }
    @Inject(method="handleThrowState",at=@At("RETURN"),remap=false)
    private static void qa$state(ThrowStatePayload payload, CallbackInfo ci) { qa.runtime.ClientAcceptance.payload(payload); }
}
