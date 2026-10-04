package qa.runtime;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
public final class FabricAcceptance implements ModInitializer {
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> ServerAcceptance.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(ServerAcceptance::tick);
    }
}
