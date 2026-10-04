package qa.client.mixin;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(AbstractContainerScreen.class)
public interface ContainerQaAccess {
    @Accessor("leftPos") int qaLeft();
    @Accessor("topPos") int qaTop();
}
