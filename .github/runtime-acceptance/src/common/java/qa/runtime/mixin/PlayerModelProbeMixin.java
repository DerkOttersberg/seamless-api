package qa.runtime.mixin;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=io.github.derkottersberg.swordthrow.client.ThrowPoseState.class, remap=false)
public abstract class PlayerModelProbeMixin {
    @Unique private qa.runtime.ClientAcceptance.ModelBaseline qa$baseline;
    @Inject(method="applyThirdPersonPose",at=@At("HEAD"),remap=false)
    private void qa$before(AvatarRenderState state, PlayerModel model, CallbackInfo ci) { qa$baseline = qa.runtime.ClientAcceptance.beforeModel(state,model); }
    @Inject(method="applyThirdPersonPose",at=@At("RETURN"),remap=false)
    private void qa$after(AvatarRenderState state, PlayerModel model, CallbackInfo ci) {
        qa.runtime.ClientAcceptance.model(state,model,(io.github.derkottersberg.swordthrow.client.ThrowPoseState)(Object)this,qa$baseline);
    }
}
