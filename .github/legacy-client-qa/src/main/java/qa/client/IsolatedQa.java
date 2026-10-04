package qa.client;

import java.nio.file.Files;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** Test-only in-game actions on the private Xvfb display; never desktop input. */
public final class IsolatedQa {
    private static int ticks;
    private static int phase;
    private static boolean capturePending;

    public static void tick(Minecraft client) {
        if (capturePending) return;
        try {
            if (Boolean.getBoolean("qa.multiplayer")) { MultiplayerQa.tick(client); return; }
            if (Boolean.getBoolean("qa.menuOnly")) {
                if (client.screen instanceof TitleScreen && ++ticks > 100)
                    capture(client, "qa-main-menu.png", () -> passed(client));
                return;
            }
            if (client.level == null || client.player == null) {
                if (phase == 0 && client.screen instanceof TitleScreen && ++ticks >= 100) {
                    phase = -1;
                    ticks = 0;
                    System.out.println("QA1201_OPEN_COPIED_WORLD");
                    client.createWorldOpenFlows().loadLevel(client.screen, "qa-world");
                }
                return;
            }
            if (phase == -1) phase = 0;
            if (Boolean.getBoolean("qa.expectweapons") && !WeaponQa.tick(client)) return;
            if (Boolean.getBoolean("qa.expectworkbench") && !WorkbenchQa.tick(client)) return;
            if (Boolean.getBoolean("qa.expectcrafting") && !CraftingQa.tick(client)) return;
            if (phase == 0) {
                phase = 1;
                client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                client.player.setYRot(0);
                client.player.setXRot(-35);
                client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                if (Boolean.getBoolean("qa.expectmeteors")) {
                    var server = client.getSingleplayerServer();
                    if (server == null) throw new IllegalStateException("No integrated QA server");
                    server.execute(() -> {
                        var player = server.getPlayerList().getPlayer(client.player.getUUID());
                        var level = player.serverLevel();
                        for (var pos : net.minecraft.core.BlockPos.betweenClosed(
                                player.blockPosition().offset(-4, 0, -4), player.blockPosition().offset(4, 12, 4))) {
                            if (level.getBlockState(pos).is(net.minecraft.tags.BlockTags.LEAVES))
                                level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        }
                        var source = server.createCommandSourceStack();
                        server.getCommands().performPrefixedCommand(source, "time set night");
                        server.getCommands().performPrefixedCommand(source, "prettymeteors start large");
                    });
                }
            }
            if (phase == 1 && ++ticks >= 160) {
                if (Boolean.getBoolean("qa.expectmeteors")) {
                    Class<?> stateClass = Class.forName("com.derko.prettymeteors.client.MeteorShowerClientState");
                    var field = stateClass.getDeclaredField("meteors");
                    field.setAccessible(true);
                    List<?> meteors = (List<?>) field.get(stateClass.getField("INSTANCE").get(null));
                    if (meteors.isEmpty()) throw new IllegalStateException("No synchronized client meteors");
                    System.out.println("QA1201_METEORS=" + meteors.size());
                }
                phase = 2;
                ticks = 0;
                capture(client, "qa-world.png", () -> {
                    String settings = System.getProperty("qa.settings", "");
                    if (settings.isEmpty()) { passed(client); return; }
                    try {
                        Screen screen = (Screen) Class.forName(settings).getConstructor(Screen.class).newInstance(client.screen);
                        client.setScreen(screen);
                    } catch (ReflectiveOperationException e) { fail(client, e); }
                });
            } else if (phase == 2 && ++ticks >= 60) {
                phase = 3;
                capture(client, "qa-settings.png", () -> passed(client));
            }
        } catch (Exception exception) { fail(client, exception); }
    }

    static void capture(Minecraft client, String name, Runnable next) {
        capturePending = true;
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> client.execute(() -> {
            try {
                if (!Files.isRegularFile(client.gameDirectory.toPath().resolve("screenshots").resolve(name)))
                    throw new IllegalStateException("Screenshot was not saved: " + message.getString());
                capturePending = false;
                next.run();
            } catch (Exception exception) { fail(client, exception); }
        }));
    }

    private static void passed(Minecraft client) {
        try {
            Files.writeString(client.gameDirectory.toPath().resolve("client-qa-passed.txt"), "Rendered 1.20.1 QA profile and saved screenshots.\n");
            System.out.println("QA1201_CLIENT_PASSED");
            capturePending = true;
            client.stop();
        } catch (Exception exception) { fail(client, exception); }
    }

    private static void fail(Minecraft client, Exception exception) {
        exception.printStackTrace();
        try { Files.writeString(client.gameDirectory.toPath().resolve("client-qa-failed.txt"), exception.toString()); }
        catch (Exception ignored) {}
        capturePending = true;
        client.stop();
    }
}
