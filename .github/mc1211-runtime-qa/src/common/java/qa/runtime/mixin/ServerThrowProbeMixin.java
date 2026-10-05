package qa.runtime.mixin;

import io.github.derkottersberg.swordthrow.SwordThrow;
import io.github.derkottersberg.swordthrow.network.ThrowActionPayload;
import io.github.derkottersberg.swordthrow.network.ThrowStatePayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostic observation only; never changes production packets or state. */
@Mixin(value = SwordThrow.class, remap = false)
public abstract class ServerThrowProbeMixin {
    @Inject(method = "handleThrowAction", at = @At("HEAD"), remap = false)
    private static void qa$action(ServerPlayer player, ThrowActionPayload payload, CallbackInfo ci) {
        System.out.println("[RUNTIME-QA-ACTION] " + player.getGameProfile().getName()
                + " " + payload + " held=" + player.getMainHandItem()
                + " creative=" + player.getAbilities().instabuild);
    }

    @Inject(method = "broadcastPoseState", at = @At("HEAD"), remap = false)
    private static void qa$state(ServerPlayer player, ThrowStatePayload payload, CallbackInfo ci) {
        System.out.println("[RUNTIME-QA-STATE] " + player.getGameProfile().getName()
                + " " + payload + " held=" + player.getMainHandItem());
    }
}
