package qa.runtime;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
@Mod("qaruntime")
public final class ForgeAcceptance {
    public ForgeAcceptance() {
        RegisterCommandsEvent.BUS.addListener(e -> ServerAcceptance.register(e.getDispatcher()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> ServerAcceptance.tick(e.server()));
    }
}
