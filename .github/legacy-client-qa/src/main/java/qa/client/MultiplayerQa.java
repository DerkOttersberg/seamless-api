package qa.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** External commands affect this JVM only; actions use real game/network paths. */
final class MultiplayerQa {
    private static int sequence, ticks, step;
    private static JsonObject command;
    private static boolean done = true;
    private static KeyMapping throwKey;
    private static String settingsOriginal;
    private static Object visualMeteor;

    static void tick(Minecraft client) throws Exception {
        var path = client.gameDirectory.toPath().resolve("qa-command.json");
        if (Files.isRegularFile(path)) {
            var next = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            int incoming = next.get("sequence").getAsInt();
            if (incoming != sequence && done) {
                sequence = incoming; command = next; ticks = step = 0; done = false; visualMeteor = null;
                System.out.println("QA1201_MP_BEGIN " + sequence + " " + command.get("action"));
            }
        }
        if (done || command == null) return;
        ticks++;
        String action = command.get("action").getAsString();
        if (ticks > 1200) throw new IllegalStateException("Multiplayer action timed out: " + action);
        if (action.equals("menu-ready")) {
            if (client.screen instanceof TitleScreen && ticks > 40) complete(client, "native menu ready, not connected");
            return;
        }
        if (action.equals("connect")) {
            if (step == 0) {
                if (client.level != null) throw new IllegalStateException("Late-join client was already connected");
                String address = System.getProperty("qa.server");
                ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
                    new ServerData("Loopback QA", address, false), false);
                step = 1; ticks = 0;
            }
            if (step == 1 && client.level != null && client.player != null && ticks > 80) {
                client.setScreen(null); complete(client, "real late-join connection ready");
            }
            return;
        }
        if (action.equals("reconnect")) {
            if (step == 0 && client.level != null) {
                client.level.disconnect(); client.clearLevel(); client.setScreen(new TitleScreen()); step = 1; ticks = 0;
                return;
            }
            if (step == 1 && ticks == 40) {
                String address = System.getProperty("qa.server");
                ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
                    new ServerData("Loopback QA", address, false), false);
                step = 2;
            }
            if (step == 2 && client.level != null && client.player != null && ticks > 100) complete(client, "reconnected");
            return;
        }
        if (client.level == null || client.player == null || client.gameMode == null) return;
        client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        switch (action) {
            case "ready" -> {
                if (ticks > 80) { client.setScreen(null); complete(client, "world ready"); }
            }
            case "meteor" -> {
                client.options.setCameraType(CameraType.FIRST_PERSON);
                client.player.setYRot(0); client.player.setXRot(-35);
                if (ticks < 100) return;
                Object state = Class.forName("com.derko.prettymeteors.client.MeteorShowerClientState").getField("INSTANCE").get(null);
                var field = state.getClass().getDeclaredField("meteors"); field.setAccessible(true);
                int count = ((List<?>) field.get(state)).size();
                boolean active = command.get("active").getAsBoolean();
                if (active && count == 0) return;
                if (!active && count != 0) return;
                if (active) {
                    if (visualMeteor == null) { visualMeteor = ((List<?>)field.get(state)).get(0); step = ticks; }
                    // Aim at an actual network-generated meteor. No pose/state
                    // injection: this removes random camera framing from visual QA.
                    var originField = state.getClass().getDeclaredField("showerOrigin"); originField.setAccessible(true);
                    float age = (float) visualMeteor.getClass().getMethod("ageAt",long.class,float.class).invoke(visualMeteor,client.level.getGameTime(),0f);
                    Vec3 target = ((Vec3)originField.get(state)).add((Vec3)visualMeteor.getClass().getMethod("positionAt",float.class).invoke(visualMeteor,age));
                    Vec3 direction = target.subtract(client.player.getEyePosition());
                    client.player.setYRot((float)Math.toDegrees(Math.atan2(direction.z,direction.x))-90f);
                    client.player.setXRot((float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z))));
                    if (ticks - step < 8) return;
                }
                screenshot(client, "mp-meteors-" + sequence + ".png", "meteors=" + count);
            }
            case "dimension" -> {
                if (client.level.dimension().location().toString().equals(command.get("dimension").getAsString()) && ticks > 50)
                    complete(client, "dimension synchronized");
            }
            case "respawn" -> {
                if (!client.player.isAlive()) { client.player.respawn(); step = 1; ticks = 0; }
                if (step == 1 && client.player.isAlive() && ticks > 80) { client.setScreen(null); complete(client, "respawned"); }
            }
            case "inventory" -> {
                if (step == 0) { client.setScreen(new InventoryScreen(client.player)); step = 1; ticks = 0; }
                if (ticks < 60) return;
                List<?> entries = (List<?>) Class.forName("com.derk.easyinventorycrafter.client.NearbyItemsClientState").getMethod("getEntries").invoke(null);
                long total = 0;
                for (Object entry : entries) {
                    ItemStack stack = (ItemStack) entry.getClass().getMethod("stack").invoke(entry);
                    if (stack.is(Items.OAK_PLANKS) && stack.isEnchanted()) total += (long) entry.getClass().getMethod("count").invoke(entry);
                }
                if (total != command.get("count").getAsLong()) throw new IllegalStateException("Nearby NBT count=" + total);
                screenshot(client, "mp-inventory-" + sequence + ".png", "nearby=" + total);
            }
            case "craft" -> {
                if (step == 0) { open(client, new BlockPos(1, 201, 0)); step = 1; ticks = 0; }
                if (step == 1 && ticks > 40) {
                    if (!(client.screen instanceof CraftingScreen)) throw new IllegalStateException("No real crafting menu");
                    var recipe = client.level.getRecipeManager().byKey(new ResourceLocation("minecraft", "crafting_table")).orElseThrow();
                    client.gameMode.handlePlaceRecipe(client.player.containerMenu.containerId, recipe, false);
                    step = 2; ticks = 0;
                }
                if (step == 2 && ticks > 40) {
                    int grid = client.player.containerMenu.slots.subList(1,10).stream().mapToInt(slot -> slot.getItem().getCount()).sum();
                    if (grid != 4) throw new IllegalStateException("Autofill client grid=" + grid);
                    screenshot(client, "mp-autofill-" + sequence + ".png", "grid=4");
                }
            }
            case "return" -> {
                if (step == 0) {
                    Class<?> payload = Class.forName("com.derk.easyinventorycrafter.net.CommonPayload");
                    Object request = Class.forName("com.derk.easyinventorycrafter.net.ReturnNearbyItemsPacket").getConstructor().newInstance();
                    Class.forName("com.derk.easyinventorycrafter.net.EasyInventoryCrafterNetwork").getMethod("sendToServer", payload).invoke(null, request);
                    step = 1; ticks = 0;
                }
                if (ticks > 40) {
                    int grid = client.player.containerMenu.slots.subList(1,10).stream().mapToInt(slot -> slot.getItem().getCount()).sum();
                    if (grid != 0) throw new IllegalStateException("Return client grid=" + grid);
                    complete(client, "returned");
                }
            }
            case "close" -> { client.player.closeContainer(); client.setScreen(null); if (ticks > 40) complete(client, "closed"); }
            case "workbench" -> {
                if (step == 0) { open(client, new BlockPos(-1, 201, 0)); step = 1; ticks = 0; }
                if (ticks > 40) {
                    if (!client.screen.getClass().getName().equals("com.seamlessdeconstructor.screen.ReverseDeconstructorScreen"))
                        throw new IllegalStateException("Workbench remote screen not opened");
                    var position = (qa.client.mixin.ContainerQaAccess) client.screen;
                    var mouse = (qa.client.mixin.MouseQaAccess) client.mouseHandler;
                    mouse.qaX((position.qaLeft()+38) * client.getWindow().getGuiScale());
                    mouse.qaY((position.qaTop()+50) * client.getWindow().getGuiScale());
                    if (ticks > 55) screenshot(client, "mp-book-hint.png", "workbench open");
                }
            }
            case "throw" -> {
                if (step == 0) {
                    client.setScreen(null); client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    client.player.getInventory().selected = 0;
                    if (!client.player.getMainHandItem().is(Items.DIAMOND_SWORD)) return;
                    client.player.setYRot(0); client.player.setXRot(-25);
                    throwKey = key(); throwKey.setDown(true); step = 1; ticks = 0;
                }
                if (step == 1 && ticks >= command.get("hold").getAsInt()) {
                    assertSleeves(client, client.player);
                    throwKey.setDown(false); step = 2; ticks = 0;
                }
                if (step == 2 && ticks > 8) screenshot(client, "mp-throw-" + sequence + ".png", "released");
            }
            case "observe" -> {
                var actor = client.level.players().stream().filter(player -> player != client.player).findFirst();
                if (actor.isEmpty()) return;
                client.setScreen(null);
                client.options.setCameraType(CameraType.FIRST_PERSON);
                Vec3 direction = actor.get().position().add(0, 1, 0).subtract(client.player.getEyePosition());
                client.player.setYRot((float)Math.toDegrees(Math.atan2(direction.z,direction.x))-90f);
                client.player.setXRot((float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z))));
                Object pose = Class.forName("io.github.derkottersberg.swordthrow.client.SwordThrowClient")
                    .getMethod("poseStateFor", int.class).invoke(null, actor.get().getId());
                if (pose != null && !(boolean) pose.getClass().getMethod("isIdle").invoke(pose)) {
                    // A shared PlayerRenderer model at rest can match sleeves
                    // without the actor ever being rendered. Require a visible,
                    // non-neutral charge pose before comparing its transforms.
                    var rendered = RenderedPlayerQa.sample(actor.get().getId());
                    if (ticks < 5 || rendered == null || client.level.getGameTime() - rendered.gameTime() > 2
                            || rendered.supportPitch() > -0.15f) return;
                    if (!rendered.sleevesMatch()) throw new IllegalStateException("Rendered remote sleeve transform differs");
                    System.out.println("QA1201_REMOTE_RENDER_PITCH=" + rendered.supportPitch());
                    screenshot(client, "mp-remote-arm-" + sequence + ".png", "actual remote charge pose");
                }
            }
            case "reload" -> {
                if (step == 0) { client.reloadResourcePacks().thenRun(() -> client.execute(() -> {
                    try { complete(client, "resources reloaded"); } catch (Exception e) { throw new IllegalStateException(e); }
                })); step = 1; }
            }
            case "settings" -> {
                if (step == 0) {
                    client.getWindow().setWindowed(1280, 960);
                    client.options.guiScale().set(command.get("scale").getAsInt()); client.resizeDisplay();
                    Screen screen = (Screen) Class.forName(command.get("class").getAsString()).getConstructor(Screen.class).newInstance((Screen)null);
                    client.setScreen(screen); step = 1; ticks = 0;
                }
                if (step == 1 && ticks > 30) {
                    if (client.getWindow().getGuiScale() != command.get("scale").getAsInt())
                        throw new IllegalStateException("Requested GUI scale was not actually exercised");
                    var edits = client.screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.EditBox).toList();
                    if (edits.isEmpty()) {
                        // Throw Weapons exposes only toggles/colors, no numeric input.
                        var toggle = toggle(client); settingsOriginal = toggle.getMessage().getString(); toggle.onPress();
                        String changed = toggle.getMessage().getString();
                        if (changed.equals(settingsOriginal)) throw new IllegalStateException("Toggle did not change its draft");
                        client.screen.resize(client, client.screen.width, client.screen.height);
                        if (!toggle(client).getMessage().getString().equals(changed)) throw new IllegalStateException("Toggle draft lost on resize");
                        toggle(client).onPress();
                    } else {
                        var edit = (net.minecraft.client.gui.components.EditBox) edits.get(0);
                        settingsOriginal = edit.getValue(); edit.setValue("NaN");
                        button(client,"Save").onPress();
                        if (client.screen == null) throw new IllegalStateException("Invalid numeric value was saved");
                        var error = client.screen.getClass().getSuperclass().getDeclaredField("error"); error.setAccessible(true);
                        if (((String) error.get(client.screen)).isEmpty()) throw new IllegalStateException("Invalid settings had no validation message");
                        client.screen.resize(client, client.screen.width, client.screen.height);
                        var next = button(client,">");
                        if (next.active) { next.onPress(); button(client,"<").onPress(); }
                        var rebuilt = client.screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();
                        if (!((net.minecraft.client.gui.components.EditBox) rebuilt).getValue().equals("NaN"))
                            throw new IllegalStateException("Unsaved draft lost on resize/page navigation");
                        ((net.minecraft.client.gui.components.EditBox) rebuilt).setValue(settingsOriginal);
                    }
                    step = 2; ticks = 0;
                }
                if (step == 2 && ticks > 20) screenshot(client, "mp-settings-" + sequence + ".png", "settings scale/draft/validation passed");
            }
            case "finish" -> {
                complete(client, "completed all coordinated actions");
                Files.writeString(client.gameDirectory.toPath().resolve("client-qa-passed.txt"), "Packaged multiplayer QA passed\n");
                client.stop();
            }
            default -> throw new IllegalStateException("Unknown QA action " + action);
        }
    }

    private static void open(Minecraft client, BlockPos pos) {
        client.player.closeContainer(); client.setScreen(null);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }
    private static KeyMapping key() throws Exception {
        var field = Class.forName("io.github.derkottersberg.swordthrow.client.SwordThrowClient").getDeclaredField("services");
        field.setAccessible(true);
        return (KeyMapping) Class.forName("io.github.derkottersberg.swordthrow.internal.ClientPlatformServices").getMethod("throwKeyMapping").invoke(field.get(null));
    }
    private static net.minecraft.client.gui.components.Button button(Minecraft client, String label) {
        return client.screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button)
            .map(child -> (net.minecraft.client.gui.components.Button) child).filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static net.minecraft.client.gui.components.Button toggle(Minecraft client) {
        return client.screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button)
            .map(child -> (net.minecraft.client.gui.components.Button) child)
            .filter(button -> button.getMessage().getString().equals("Enabled") || button.getMessage().getString().equals("Disabled"))
            .findFirst().orElseThrow();
    }
    private static void assertSleeves(Minecraft client, net.minecraft.client.player.AbstractClientPlayer player) {
        var model = ((PlayerRenderer) client.getEntityRenderDispatcher().getRenderer(player)).getModel();
        if (!samePose(model.rightArm,model.rightSleeve) || !samePose(model.leftArm,model.leftSleeve))
            throw new IllegalStateException("Remote/local sleeve transform differs");
    }
    private static boolean samePose(net.minecraft.client.model.geom.ModelPart arm, net.minecraft.client.model.geom.ModelPart sleeve) {
        return arm.x == sleeve.x && arm.y == sleeve.y && arm.z == sleeve.z
            && arm.xRot == sleeve.xRot && arm.yRot == sleeve.yRot && arm.zRot == sleeve.zRot;
    }
    private static void screenshot(Minecraft client, String name, String detail) {
        done = true;
        IsolatedQa.capture(client, name, () -> {
            try { complete(client, detail); } catch (Exception e) { throw new IllegalStateException(e); }
        });
    }
    private static void complete(Minecraft client, String detail) throws Exception {
        var event = new JsonObject(); event.addProperty("sequence", sequence); event.addProperty("action", command.get("action").getAsString());
        event.addProperty("detail", detail);
        Files.writeString(client.gameDirectory.toPath().resolve("qa-event-" + sequence + ".json"), event.toString());
        System.out.println("QA1201_MP_PASSED " + sequence + " " + detail); done = true;
    }
}
