package qa.client.mixin;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(MouseHandler.class)
public interface MouseQaAccess {
    @Accessor("xpos") void qaX(double value);
    @Accessor("ypos") void qaY(double value);
}
