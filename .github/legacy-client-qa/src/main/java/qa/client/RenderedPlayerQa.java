package qa.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;

/** Immutable observations from the actual remote-player render, never injected poses. */
public final class RenderedPlayerQa {
    public record Sample(long gameTime, boolean sleevesMatch, float supportPitch) {}
    private static final Map<Integer, Sample> samples = new HashMap<>();

    public static void capture(AbstractClientPlayer player, PlayerModel<?> model) {
        var client = Minecraft.getInstance();
        if (client.level == null || player == client.player) return;
        var support = player.getMainArm() == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;
        samples.put(player.getId(), new Sample(client.level.getGameTime(),
            same(model.rightArm, model.rightSleeve) && same(model.leftArm, model.leftSleeve), support.xRot));
    }

    public static Sample sample(int id) { return samples.get(id); }

    private static boolean same(ModelPart arm, ModelPart sleeve) {
        return arm.x == sleeve.x && arm.y == sleeve.y && arm.z == sleeve.z
            && arm.xRot == sleeve.xRot && arm.yRot == sleeve.yRot && arm.zRot == sleeve.zRot;
    }

    private RenderedPlayerQa() {}
}
