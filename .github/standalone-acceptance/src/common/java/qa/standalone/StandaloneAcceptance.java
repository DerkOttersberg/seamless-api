package qa.standalone;

import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

/** Bootstrap/dependency smoke test, not a replacement for gameplay GameTests. */
public final class StandaloneAcceptance {
    private static int ticks, initialServerTick, shutdown;
    private static boolean started, finished;
    private static volatile boolean serverPassed;
    private static volatile String serverFailure;

    public static void tick(Minecraft client) {
        if (finished) { if (++shutdown == 30) client.stop(); return; }
        String mod = System.getProperty("qa.standalone.mod");
        if (mod == null || client.level == null || client.player == null || client.gui.overlay() != null) return;
        client.options.pauseOnLostFocus = false;
        var server = client.getSingleplayerServer();
        if (server == null) { fail(client, "Expected an integrated server"); return; }
        ticks++;
        if (!started && ticks >= 20) {
            started = true;
            initialServerTick = server.getTickCount();
            server.execute(() -> {
                try {
                    var level = server.overworld();
                    var pos = new BlockPos(8, 201, 8);
                    level.setBlockAndUpdate(pos, Blocks.OAK_LOG.defaultBlockState());
                    if (!level.destroyBlock(pos, true)) throw new AssertionError("Vanilla drop path failed");
                    switch (mod) {
                        case "seamless-api" -> { }
                        case "pretty-meteors-with-trails" -> {
                            if (server.getCommands().getDispatcher().getRoot().getChild("prettymeteors") == null)
                                throw new AssertionError("Meteor command missing");
                            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set night");
                            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "prettymeteors start large");
                        }
                        case "seamless-deconstructing-workbench" -> {
                            var id = Identifier.fromNamespaceAndPath("seamlessdeconstructor", "reverse_deconstructor");
                            var block = BuiltInRegistries.BLOCK.getValue(id);
                            if (block == Blocks.AIR) throw new AssertionError("Workbench block missing");
                            level.setBlockAndUpdate(pos, block.defaultBlockState());
                            if (level.getBlockEntity(pos) == null) throw new AssertionError("Workbench block entity missing");
                        }
                        case "seamless-crafting" -> {
                            if (!Files.isRegularFile(client.gameDirectory.toPath().resolve("config/seamless-crafting.json")))
                                throw new AssertionError("Crafting configuration not initialized");
                        }
                        case "sword-throw" -> {
                            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.fromNamespaceAndPath("swordthrow", "thrown_sword")))
                                throw new AssertionError("Sword projectile registration missing");
                        }
                        default -> throw new AssertionError("Unknown standalone mod " + mod);
                    }
                    serverPassed = true;
                } catch (Throwable failure) { serverFailure = failure.toString(); }
            });
        }
        if (serverFailure != null) { fail(client, serverFailure); return; }
        if (started && ticks >= 140 && serverPassed && server.getTickCount() - initialServerTick >= 100) {
            Screenshot.grab(client.gameDirectory, "standalone-" + mod + ".png", client.gameRenderer.mainRenderTarget(), 1, ignored -> {});
            write(client, "standalone-passed.txt", "PASS " + mod + ": actual integrated world, initialization, vanilla drops, 100+ server ticks.\n");
            finished = true;
        }
        if (ticks > 500) fail(client, "Integrated-server ticks or bootstrap did not complete");
    }

    private static void fail(Minecraft client, String failure) {
        write(client, "standalone-failed.txt", failure);
        finished = true;
        throw new IllegalStateException(failure);
    }
    private static void write(Minecraft client, String name, String text) {
        try { Files.writeString(client.gameDirectory.toPath().resolve(name), text); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        System.out.println("[STANDALONE-QA] " + text.strip());
    }
}
