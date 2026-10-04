package qa.runtime;

import io.github.derkottersberg.swordthrow.client.SwordThrowClient;
import io.github.derkottersberg.swordthrow.client.SwordThrowKeyMappings;
import io.github.derkottersberg.swordthrow.network.ThrowStatePayload;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;

/** No injected pose data: drives production key bindings and observes actual packets/models. */
public final class ClientAcceptance {
    private static int phase, ticks, reconnectTicks, models;
    private static boolean acknowledged, initialized, disconnected, finished;
    private static final Map<Integer, ThrowStatePayload.Phase> phases = new HashMap<>();
    private static final Map<Integer, Integer> releaseCharges = new HashMap<>();
    private static final Map<Integer, Integer> authoritativeCharges = new HashMap<>();
    private static final Set<Integer> rendered = new HashSet<>();
    private static String role;
    private static net.minecraft.client.player.LocalPlayer respawnSource;
    private static int shutdownTicks;
    private static int productionTicks;
    private static int readyTicks;
    private static boolean readySent;
    public static void productionTick() { productionTicks++; }

    public static void message(String text) {
        if (!text.startsWith("QA_ACCEPTANCE_PHASE ")) return;
        int next = Integer.parseInt(text.substring("QA_ACCEPTANCE_PHASE ".length()));
        if (phase == next) return;
        phase = next; ticks = 0; acknowledged = false; phases.clear(); rendered.clear(); releaseCharges.clear(); authoritativeCharges.clear();
        respawnSource = null;
        log("phase=" + phase);
    }

    public static void payload(ThrowStatePayload payload) {
        phases.put(payload.playerEntityId(), payload.phase());
        if (payload.phase() == ThrowStatePayload.Phase.RELEASE) releaseCharges.put(payload.playerEntityId(), payload.chargeTicks());
        if (payload.phase() == ThrowStatePayload.Phase.CHARGING) authoritativeCharges.put(payload.playerEntityId(), payload.chargeTicks());
        log("actual server packet actor=" + payload.playerEntityId() + " " + payload.phase() + " charge=" + payload.chargeTicks());
    }

    public record ModelBaseline(net.minecraft.client.model.geom.ModelPart main, net.minecraft.client.model.geom.ModelPart off,
        net.minecraft.client.model.geom.PartPose rightSleeve, net.minecraft.client.model.geom.PartPose leftSleeve,
        boolean rightVisible, boolean leftVisible, boolean rightSkip, boolean leftSkip) {}

    public static ModelBaseline beforeModel(AvatarRenderState state, PlayerModel model) {
        if (phase == 0 || finished || state.isSpectator || state.mainArm == null) return null;
        var main = new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of());
        var off = new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of());
        main.loadPose(model.getArm(state.mainArm).storePose());
        off.loadPose(model.getArm(state.mainArm.getOpposite()).storePose());
        return new ModelBaseline(main,off,model.rightSleeve.storePose(),model.leftSleeve.storePose(),
            model.rightSleeve.visible,model.leftSleeve.visible,model.rightSleeve.skipDraw,model.leftSleeve.skipDraw);
    }

    public static void model(AvatarRenderState state, PlayerModel model, io.github.derkottersberg.swordthrow.client.ThrowPoseState pose, ModelBaseline before) {
        if (before == null) return;
        if (!model.rightSleeve.storePose().equals(before.rightSleeve())
            || !model.leftSleeve.storePose().equals(before.leftSleeve())) fail("Sleeve local transform was modified for rendered actor " + state.id);
        if (model.rightSleeve.visible != before.rightVisible() || model.leftSleeve.visible != before.leftVisible()
            || model.rightSleeve.skipDraw != before.rightSkip() || model.leftSleeve.skipDraw != before.leftSkip()) fail("Skin visibility overwritten");
        if (!Float.isFinite(model.rightArm.xRot) || !Float.isFinite(model.leftArm.xRot)) fail("Non-finite arm pose");
        // Compare deltas against the actual vanilla pose, not a fixed arm angle:
        // held-item poses, bobbing, attack and interpolation all change that baseline.
        pose.applyThirdPersonMainHandPose(state.ageInTicks,state.mainArm,before.main());
        pose.applyThirdPersonOffHandPose(state.ageInTicks,state.mainArm.getOpposite(),before.off());
        if (!model.getArm(state.mainArm).storePose().equals(before.main().storePose())
            || !model.getArm(state.mainArm.getOpposite()).storePose().equals(before.off().storePose())) fail("Incorrect rendered arm delta");
        rendered.add(state.id); models++;
    }

    public static void tick(Minecraft client) {
        if (finished) { if (++shutdownTicks == 45) client.stop(); return; }
        if (role == null) role = client.getUser().getName();
        if (!Set.of("QA_A", "QA_B").contains(role)) return;
        if (client.player == null || client.level == null) {
            if (disconnected && ++reconnectTicks == 40) {
                String address = System.getProperty("qa.server", "127.0.0.1:25580");
                ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
                    new ServerData("QA", address, ServerData.Type.OTHER), false, null);
                log("Reconnecting real client");
            }
            return;
        }
        if (!initialized) {
            initialized = true;
            client.options.pauseOnLostFocus = false;
            client.options.setCameraType(role.equals("QA_A") ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
            client.options.mainHand().set(role.equals("QA_A") ? HumanoidArm.RIGHT : HumanoidArm.LEFT);
            for (PlayerModelPart part : PlayerModelPart.values()) client.options.setModelPart(part, true);
        }
        if (phase == 0) {
            SwordThrowKeyMappings.THROW.setDown(false);
            if (client.gui.screen() == null && client.gui.overlay() == null
                && client.player.getAbilities().instabuild) {
                if (!readySent && ++readyTicks >= 20) {
                    client.getConnection().sendCommand("qa_acceptance ready");
                    readySent = true;
                    log("Ready after initial world/creative inventory synchronization");
                }
            } else readyTicks = 0;
            return;
        }
        ticks++;
        if (ticks == 1 || ticks % 100 == 0) log("diagnostic: productionTicks=" + productionTicks + " gameLoadFinished=" + ((qa.runtime.mixin.MinecraftProbeAccessor)client).qa$gameLoadFinished()
            + " keyDown=" + SwordThrowKeyMappings.THROW.isDown() + " held=" + client.player.getMainHandItem() + " screen=" + (client.gui.screen()==null ? "none" : client.gui.screen().getClass().getName()));
        boolean a = role.equals("QA_A");
        if (phase == 13 && a && respawnSource == null && client.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) respawnSource = client.player;
        boolean charge = switch (phase) { case 1, 3, 6, 7 -> true; case 4 -> !a; default -> false; };
        if (phase == 12 && a) charge = client.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD);
        if (phase == 13 && a) charge = client.player == respawnSource && client.player.isAlive();
        if ((phase == 1 || phase == 6 || phase == 7) && !a) charge = false;
        if (phase >= 14 && phase <= 17 && a) {
            if (ticks == 1) {
                SwordThrowKeyMappings.THROW.setKey(com.mojang.blaze3d.platform.InputConstants.getKey(
                    phase == 15 ? "key.keyboard.r" : client.options.keyDrop.saveString()));
                net.minecraft.client.KeyMapping.resetMapping();
            }
            // Paired clients can tick faster than a busy test server. Full power
            // must wait for a real server heartbeat, not assume equal clocks.
            charge = phase == 17 ? authoritativeCharges.getOrDefault(client.player.getId(), 0) < 30
                : ticks <= (phase <= 15 ? 1 : 6);
        }
        SwordThrowKeyMappings.THROW.setDown(charge);
        if (ticks == 1 && phase == 4 && a) client.setScreenAndShow(new InventoryScreen(client.player));
        if (ticks == 1 && phase == 5 && a) client.setScreenAndShow(null);
        if (ticks == 1 && phase == 3) client.options.setModelPart(a ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE, false);
        if (ticks == 1 && phase == 5) { client.options.setModelPart(PlayerModelPart.RIGHT_SLEEVE, true); client.options.setModelPart(PlayerModelPart.LEFT_SLEEVE, true); }
        if (ticks == 1 && phase == 8 && a) client.setScreenAndShow(new InventoryScreen(client.player));
        if (ticks == 15 && (phase == 8 || phase == 9) && a) client.player.closeContainer();
        if (ticks == 15 && phase == 10 && a) {
            disconnected = true;
            reconnectTicks = 0;
            client.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("QA disconnect with borrowed ingredients"));
            return;
        }
        if (phase == 13 && a && !client.player.isAlive()) client.player.respawn();
        if (ticks > 500) fail("Timeout in phase " + phase + " packets=" + phases + " rendered=" + rendered);
        if (acknowledged) return;
        var other = client.level.players().stream().filter(p -> !p.getUUID().equals(client.player.getUUID())).findFirst().orElse(null);
        int local = client.player.getId();
        int remote = other == null ? -1 : other.getId();
        boolean ready = switch (phase) {
            case 1 -> ticks >= 25 && client.gui.overlay() == null && (a ? active(local) && rendered.contains(local) : active(remote) && rendered.contains(remote));
            case 2 -> a ? received(local, ThrowStatePayload.Phase.RELEASE) : received(remote, ThrowStatePayload.Phase.RELEASE);
            case 3 -> ticks >= 25 && active(local) && active(remote) && SwordThrowClient.poseStateFor(local) != SwordThrowClient.poseStateFor(remote);
            case 4 -> a ? received(local, ThrowStatePayload.Phase.CANCEL) && !active(local) && active(remote) : received(remote, ThrowStatePayload.Phase.CANCEL) && !active(remote) && active(local);
            case 5 -> a ? received(remote, ThrowStatePayload.Phase.RELEASE) : received(local, ThrowStatePayload.Phase.RELEASE);
            case 6 -> a ? active(local) : other == null && ticks >= 25;
            case 7 -> a ? active(local) : active(remote) && rendered.contains(remote) && received(remote, ThrowStatePayload.Phase.CHARGING);
            case 8, 9 -> ticks >= 25;
            case 10 -> !a && ticks >= 25;
            case 11 -> a ? ticks >= 20 && SwordThrowClient.localPoseState().isIdle() : ticks >= 20;
            case 12 -> a ? client.level.dimension().equals(net.minecraft.world.level.Level.NETHER) && SwordThrowClient.localPoseState().isIdle() : ticks >= 35 && other == null;
            case 13 -> a ? respawnSource != null && client.player != respawnSource && client.player.isAlive() && SwordThrowClient.localPoseState().isIdle() : ticks >= 50;
            case 14, 15 -> ticks >= 20 && (!a || received(local, ThrowStatePayload.Phase.CANCEL));
            case 16 -> !a ? received(remote, ThrowStatePayload.Phase.RELEASE)
                : releaseCharges.containsKey(local) && releaseCharges.get(local) >= 2 && releaseCharges.get(local) <= 6;
            case 17 -> !a ? received(remote, ThrowStatePayload.Phase.RELEASE)
                : releaseCharges.getOrDefault(local, -1) == 30;
            case 18 -> true;
            default -> false;
        };
        if (!ready) return;
        acknowledged = true;
        log("PASS phase " + phase + " models=" + models);
        if (phase <= 7 || phase >= 14) Screenshot.grab(client.gameDirectory, role + "-phase-" + phase + ".png", client.gameRenderer.mainRenderTarget(), 1, message -> log(message.getString()));
        client.getConnection().sendCommand("qa_acceptance ack " + phase);
        if (phase == 18) {
            try { Files.writeString(client.gameDirectory.toPath().resolve("runtime-client-passed.txt"), "PASS real paired-client scenarios; animated player-model checks=" + models + "\n"); }
            catch (Exception e) { throw new RuntimeException(e); }
            finished = true;
            // Give the acknowledgement time to reach the dedicated server.
            client.options.pauseOnLostFocus = false;
        }
    }

    private static boolean received(int id, ThrowStatePayload.Phase phase) { return phases.get(id) == phase; }
    private static boolean active(int id) { return id >= 0 && SwordThrowClient.poseStateFor(id) != null; }
    private static void fail(String message) {
        try { Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("runtime-client-failed.txt"), message); }
        catch (Exception e) { throw new RuntimeException(e); }
        throw new IllegalStateException(message);
    }
    private static void log(String message) { System.out.println("[RUNTIME-QA-CLIENT " + role + "] " + message); }
}
