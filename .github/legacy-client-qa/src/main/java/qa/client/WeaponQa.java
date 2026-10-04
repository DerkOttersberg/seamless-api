package qa.client;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

/** Real client tick/network/render exercise. KeyMapping changes stay inside this JVM. */
final class WeaponQa {
    private static int phase;
    private static int ticks;
    private static KeyMapping key;
    private static CompletableFuture<Double> result;

    static boolean tick(Minecraft client) throws Exception {
        if (phase == 5) return true;
        if (phase == 0) {
            var server = client.getSingleplayerServer();
            if (server == null) throw new IllegalStateException("No QA integrated server");
            client.setScreen(null);
            client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            client.player.setYRot(0);
            client.player.setXRot(-10);
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(client.player.getUUID());
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                sword.setHoverName(Component.literal("Isolated charge QA"));
                sword.setDamageValue(7);
                sword.enchant(Enchantments.UNBREAKING, 1);
                player.getInventory().selected = 0;
                player.getInventory().setItem(0, sword);
                player.inventoryMenu.broadcastChanges();
            });
            client.player.getInventory().selected = 0;
            Class<?> clientMod = Class.forName("io.github.derkottersberg.swordthrow.client.SwordThrowClient");
            var field = clientMod.getDeclaredField("services");
            field.setAccessible(true);
            key = (KeyMapping) Class.forName("io.github.derkottersberg.swordthrow.internal.ClientPlatformServices")
                .getMethod("throwKeyMapping").invoke(field.get(null));
            phase = 1;
            return false;
        }
        if (phase == 1 && ++ticks >= 40) {
            if (!client.player.getMainHandItem().is(Items.DIAMOND_SWORD)) {
                throw new IllegalStateException("QA sword did not synchronize to the client");
            }
            key.setDown(true);
            ticks = 0;
            phase = 2;
        } else if (phase == 2 && ++ticks >= 6) {
            var renderer = (PlayerRenderer) client.getEntityRenderDispatcher().getRenderer(client.player);
            var model = renderer.getModel();
            assertSamePose(model.rightArm, model.rightSleeve);
            assertSamePose(model.leftArm, model.leftSleeve);
            Object pose = Class.forName("io.github.derkottersberg.swordthrow.client.SwordThrowClient")
                .getMethod("localPoseState").invoke(null);
            float progress = (float) pose.getClass().getMethod("getChargeIndicatorProgress", float.class).invoke(pose, 1f);
            if (!(progress > 0 && progress < 0.95f)) throw new IllegalStateException("Not a partial charge: " + progress);
            System.out.println("QA1201_SLEEVES_MATCH_ARMS; PARTIAL_CHARGE=" + progress);
            phase = 3;
            IsolatedQa.capture(client, "qa-throw-charging.png", () -> {
                key.setDown(false);
                ticks = 0;
            });
        } else if (phase == 3 && ++ticks >= 3) {
            var server = client.getSingleplayerServer();
            result = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(client.player.getUUID());
                    var projectiles = player.serverLevel().getEntitiesOfClass(ThrowableItemProjectile.class,
                        player.getBoundingBox().inflate(64), entity -> entity.getClass().getSimpleName().equals("ThrownSwordEntity")
                            && entity.getOwner() == player);
                    if (projectiles.size() != 1 || !player.getMainHandItem().isEmpty()) {
                        throw new IllegalStateException("Partial release did not conserve one weapon: " + projectiles.size());
                    }
                    ItemStack item = projectiles.get(0).getItem();
                    if (!item.is(Items.DIAMOND_SWORD) || item.getDamageValue() != 7
                            || !item.isEnchanted() || !item.getHoverName().getString().equals("Isolated charge QA")) {
                        throw new IllegalStateException("Thrown weapon lost NBT identity");
                    }
                    result.complete(projectiles.get(0).getDeltaMovement().length());
                } catch (Exception exception) { result.completeExceptionally(exception); }
            });
            phase = 4;
            ticks = 0;
        } else if (phase == 4 && result.isDone()) {
            System.out.println("QA1201_PARTIAL_THROW_AND_NBT_PASSED; SPEED=" + result.join());
            phase = 5;
            IsolatedQa.capture(client, "qa-throw-release.png", () -> {});
        }
        return false;
    }

    private static void assertSamePose(ModelPart arm, ModelPart sleeve) {
        if (arm.x != sleeve.x || arm.y != sleeve.y || arm.z != sleeve.z
                || arm.xRot != sleeve.xRot || arm.yRot != sleeve.yRot || arm.zRot != sleeve.zRot) {
            throw new IllegalStateException("Rendered sleeve diverged from its arm");
        }
    }
}
