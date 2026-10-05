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
            if (Boolean.getBoolean("qa.menuOnly")) {
                if (phase == 0 && client.screen instanceof TitleScreen && ++ticks > 100) {
                    IconQa.verify(client);
                    phase = 1;
                    ticks = 0;
                    capture(client, "qa-main-menu.png", () -> {
                        if (!Boolean.getBoolean("qa.modMenu")) { passed(client); return; }
                        try {
                            String screenClass = switch (System.getProperty("qa.loader")) {
                                case "fabric" -> "com.terraformersmc.modmenu.gui.ModsScreen";
                                case "forge" -> "net.minecraftforge.client.gui.ModListScreen";
                                case "neoforge" -> "net.neoforged.neoforge.client.gui.ModListScreen";
                                default -> throw new IllegalStateException("Unknown QA loader");
                            };
                            client.setScreen((Screen) Class.forName(screenClass).getConstructor(Screen.class).newInstance(client.screen));
                        } catch (Exception exception) { fail(client, exception); }
                    });
                } else if (phase == 1 && ++ticks > 70 && Boolean.getBoolean("qa.modMenu")) {
                    phase = 2;
                    capture(client, "qa-mod-menu.png", () -> {});
                } else if (phase == 2 && ModMenuQa.tick(client)) {
                    passed(client);
                }
                return;
            }
            if (client.level == null || client.player == null) {
                if (phase == 0 && client.screen instanceof TitleScreen && ++ticks >= 100) {
                    phase = -1;
                    ticks = 0;
                    System.out.println("QA1211_OPEN_COPIED_WORLD");
                    client.createWorldOpenFlows().openWorld("qa-world", () -> client.setScreen(new TitleScreen()));
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
                        // Inspect the sky from an unobstructed viewpoint in the
                        // disposable world; nearby hills must not hide a valid trail.
                        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                        player.teleportTo(player.getX(), 280, player.getZ());
                        for (var pos : net.minecraft.core.BlockPos.betweenClosed(
                                player.blockPosition().offset(-4, 0, -4), player.blockPosition().offset(4, 12, 4))) {
                            if (level.getBlockState(pos).is(net.minecraft.tags.BlockTags.LEAVES))
                                level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        }
                        var source = server.createCommandSourceStack().withLevel(level).withPosition(player.position());
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
                    System.out.println("QA1211_METEORS=" + meteors.size());
                }
                if (Boolean.getBoolean("qa.expectmeteors")) {
                    phase = 4;
                    ticks = 0;
                    return;
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
            } else if (phase == 4) {
                if (MeteorQa.tick(client)) {
                    phase = 2;
                    ticks = 0;
                    if (System.getProperty("qa.settings", "").isEmpty()) passed(client);
                }
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
            Files.writeString(client.gameDirectory.toPath().resolve("client-qa-passed.txt"), "Rendered 1.21.1 QA profile and saved screenshots.\n");
            System.out.println("QA1211_CLIENT_PASSED");
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
