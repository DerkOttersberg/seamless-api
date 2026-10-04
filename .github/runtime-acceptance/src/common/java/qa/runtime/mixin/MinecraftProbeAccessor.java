package qa.runtime.mixin;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Minecraft.class)
public interface MinecraftProbeAccessor {
    @Accessor("gameLoadFinished") boolean qa$gameLoadFinished();
}
