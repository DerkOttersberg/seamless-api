package qa.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Aim the test camera at real synchronized trails, not an arbitrary patch of sky. */
final class MeteorQa {
    private static int ticks;
    private static int captures;

    static boolean tick(Minecraft client) throws Exception {
        if (captures >= 4) return true;
        Class<?> type = Class.forName("com.derko.prettymeteors.client.MeteorShowerClientState");
        Object state = type.getField("INSTANCE").get(null);
        var meteorsField = type.getDeclaredField("meteors");
        var originField = type.getDeclaredField("showerOrigin");
        var renderedField = type.getDeclaredField("loggedFirstRender");
        meteorsField.setAccessible(true);
        originField.setAccessible(true);
        renderedField.setAccessible(true);
        List<?> meteors = (List<?>) meteorsField.get(state);
        Vec3 origin = (Vec3) originField.get(state);
        if (origin == null || meteors.isEmpty()) throw new IllegalStateException("No active shower to inspect");
        Object meteor = meteors.get(0);
        float age = (float) meteor.getClass().getMethod("ageAt", long.class, float.class)
            .invoke(meteor, client.level.getGameTime(), 1f);
        Vec3 position = (Vec3) meteor.getClass().getMethod("positionAt", float.class).invoke(meteor, age);
        Vec3 direction = origin.add(position).subtract(client.player.getEyePosition());
        client.player.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        client.player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
        if (++ticks >= 12) {
            if (!(boolean) renderedField.get(state)) throw new IllegalStateException("Meteor world render hook was never reached");
            ticks = 0;
            captures++;
            System.out.println("QA1211_METEOR_RENDER_CAPTURE=" + captures + "; AGE=" + age + "; DIRECTION=" + direction);
            IsolatedQa.capture(client, "qa-meteor-target-" + captures + ".png", () -> {});
        }
        return false;
    }
}
