package qa.runtime.mixin;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public abstract class ChatMixin {
    @Inject(method="handleSystemChat",at=@At("TAIL"))
    private void qa$chat(ClientboundSystemChatPacket packet, CallbackInfo ci) { qa.runtime.ClientAcceptance.message(packet.content().getString()); }
}
