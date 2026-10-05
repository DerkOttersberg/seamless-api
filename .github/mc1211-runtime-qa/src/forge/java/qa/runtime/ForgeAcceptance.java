package qa.runtime;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
@Mod("qaruntime")
public final class ForgeAcceptance {
    public ForgeAcceptance() {
        if (Boolean.getBoolean("qa.individual") || Boolean.getBoolean("qa.menuOnly")) return;
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> ServerAcceptance.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) ServerAcceptance.tick(e.getServer());
        });
    }
}
