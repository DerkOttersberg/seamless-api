package qa.runtime.mixin;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=io.github.derkottersberg.swordthrow.client.ThrowPoseState.class, remap=false)
public abstract class PlayerModelProbeMixin {
    @Unique private qa.runtime.ClientAcceptance.ModelBaseline qa$baseline;
    @Inject(target=@Desc(value="applyThirdPersonPose", args={AbstractClientPlayer.class,float.class,PlayerModel.class}),at=@At("HEAD"),remap=false)
    private void qa$before(AbstractClientPlayer player, float age, PlayerModel<?> model, CallbackInfo ci) { qa$baseline = qa.runtime.ClientAcceptance.beforeModel(player,model); }
    @Inject(target=@Desc(value="applyThirdPersonPose", args={AbstractClientPlayer.class,float.class,PlayerModel.class}),at=@At("RETURN"),remap=false)
    private void qa$after(AbstractClientPlayer player, float age, PlayerModel<?> model, CallbackInfo ci) {
        qa.runtime.ClientAcceptance.model(player,age,model,(io.github.derkottersberg.swordthrow.client.ThrowPoseState)(Object)this,qa$baseline);
    }
}
