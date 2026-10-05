package qa.runtime.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Coordinates inside the private test JVM, never operating-system input. */
@Mixin(MouseHandler.class)
public interface MouseAccess {
    @Accessor("xpos") void qa$x(double value);
    @Accessor("ypos") void qa$y(double value);
}
