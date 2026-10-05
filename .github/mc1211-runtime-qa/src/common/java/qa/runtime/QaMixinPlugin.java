package qa.runtime;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Keeps standalone test installations free of sibling gameplay dependencies. */
public final class QaMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String pkg) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String mixin) {
        return !Boolean.getBoolean("qa.noWeapons") || !(mixin.endsWith("ServerThrowProbeMixin")
            || mixin.endsWith("ThrowStateMixin") || mixin.endsWith("PlayerModelProbeMixin"));
    }
    public void acceptTargets(Set<String> own, Set<String> other) {}
    public List<String> getMixins() { return null; }
    public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
