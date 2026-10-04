package qa.runtime;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
@Mod("qaruntime")
public final class NeoForgeAcceptance {
    public NeoForgeAcceptance() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> ServerAcceptance.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> ServerAcceptance.tick(e.getServer()));
    }
}
