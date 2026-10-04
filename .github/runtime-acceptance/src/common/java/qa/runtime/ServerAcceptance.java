package qa.runtime;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.derkottersberg.swordthrow.SwordThrow;
import io.github.derkottersberg.swordthrow.entity.ThrownSwordEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

/** Dedicated test server only; paired real clients drive the production input/network paths. */
public final class ServerAcceptance {
    private static int phase, phaseStart, start, ticks;
    private static boolean finished;
    private static boolean bootstrapped;
    private static final Set<String> readyClients = new HashSet<>();
    private static ServerPlayer fixturePlayer;
    private static final Set<String> acknowledgements = new HashSet<>();
    private static final BlockPos BARREL = new BlockPos(2, 201, 0);
    private static final BlockPos TABLE = new BlockPos(1, 201, 0);
    private static ItemStack template;
    private static double partialLaunchSpeed, fullLaunchSpeed;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("qa_acceptance")
            .then(Commands.literal("ready").executes(context -> {
                String name = context.getSource().getPlayerOrException().getGameProfile().name();
                if (!Set.of("QA_A", "QA_B").contains(name)) return 0;
                readyClients.add(name);
                return 1;
            }))
            .then(Commands.literal("ack").then(Commands.argument("phase", IntegerArgumentType.integer(1, 18))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!Set.of("QA_A", "QA_B").contains(player.getGameProfile().name())) return 0;
                    if (IntegerArgumentType.getInteger(context, "phase") == phase) {
                        acknowledgements.add(player.getGameProfile().name());
                        log("phase=" + phase + " acknowledged by " + player.getGameProfile().name());
                    }
                    return 1;
                }))));
    }

    public static void tick(MinecraftServer server) {
        if (!server.isDedicatedServer() || !Boolean.getBoolean("qa.runtime.acceptance") || finished) return;
        ticks++;
        ServerPlayer a = server.getPlayerList().getPlayerByName("QA_A");
        ServerPlayer b = server.getPlayerList().getPlayerByName("QA_B");
        if (phase == 0) {
            if (a == null || b == null) return;
            if (!bootstrapped) {
                a.setGameMode(GameType.CREATIVE);
                b.setGameMode(GameType.CREATIVE);
                bootstrapped = true;
                return;
            }
            // Initial creative inventory sync can overwrite an early fixture.
            // Commands share the real client connection's packet ordering: wait
            // until both clients have loaded their world and acknowledged readiness.
            if (!readyClients.containsAll(Set.of("QA_A", "QA_B"))) return;
            start = ticks;
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "fill -8 200 -8 8 200 12 minecraft:stone");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp QA_A 0.5 201 0.5 0 0");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp QA_B 0.5 201 6.5 180 0");
            next(server, 1);
            return;
        }
        if (ticks - start > 6000 || ticks - phaseStart > 600) fail("Timed out waiting for phase " + phase + ": " + acknowledgements);
        if (phase == 16 || phase == 17) {
            for (var entity : server.overworld().getAllEntities()) {
                if (entity instanceof ThrownSwordEntity thrown && thrown.getOwner() == a
                    && ItemStack.isSameItemSameComponents(thrown.getItem(), template)) {
                    double speed = thrown.getDeltaMovement().length();
                    if (phase == 16 && partialLaunchSpeed == 0) partialLaunchSpeed = speed;
                    if (phase == 17 && fullLaunchSpeed == 0) fullLaunchSpeed = speed;
                }
            }
        }
        if (phase == 12 && ticks - phaseStart == 30) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "execute in minecraft:the_nether run tp QA_A 0 80 0");
        }
        if (phase == 13 && ticks - phaseStart == 30) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "kill QA_A");
        }
        if (phase == 10 && a == null) {
            verifyFixture(server, "actual disconnect");
            next(server, 11);
            return;
        }
        if (phase == 11 && a != null && !acknowledgements.contains("QA_A") && (ticks - phaseStart) % 20 == 0) {
            a.sendSystemMessage(Component.literal("QA_ACCEPTANCE_PHASE 11"));
        }
        int minimum = phase == 6 ? 30 : (phase <= 7 ? 20 : 5);
        if (ticks - phaseStart < minimum || acknowledgements.size() != 2) return;
        if (phase == 8 || phase == 9 || phase == 11) verifyFixture(server, "menu/reconnect phase " + phase);
        if (phase == 14 && a.getMainHandItem().getCount() != 2) fail("Shared Drop key tap did not drop exactly one item");
        if (phase == 15 && !ItemStack.matches(a.getMainHandItem(), template)) fail("Custom Throw key tap changed/dropped its item");
        if (phase == 16 && (partialLaunchSpeed < 1.1D || partialLaunchSpeed > 1.5D || !a.getMainHandItem().isEmpty())) fail("Partial throw power/conservation failed: " + partialLaunchSpeed);
        if (phase == 17 && (fullLaunchSpeed < 2.1D || fullLaunchSpeed > 2.5D || fullLaunchSpeed <= partialLaunchSpeed + 0.6D || !a.getMainHandItem().isEmpty())) fail("Full throw power/conservation failed: " + fullLaunchSpeed);
        if (phase == 18) {
            finished = true;
            write("runtime-server-passed.txt", "PASS remote charge/release/cancel/isolation/late tracking; inventory/table close; real disconnect/reconnect; dimension/respawn; shared-key tap, custom-key tap, partial/full throws. Partial speed=" + partialLaunchSpeed + " full speed=" + fullLaunchSpeed + "\n");
            log("All real two-client scenarios passed");
            return;
        }
        next(server, phase + 1);
    }

    private static void next(MinecraftServer server, int next) {
        phase = next;
        phaseStart = ticks;
        acknowledgements.clear();
        ServerPlayer a = server.getPlayerList().getPlayerByName("QA_A");
        ServerPlayer b = server.getPlayerList().getPlayerByName("QA_B");
        if (phase == 1 || phase == 3 || phase == 6) {
            a.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
            b.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
            a.containerMenu.broadcastChanges(); b.containerMenu.broadcastChanges();
        }
        if (phase == 6) server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp QA_B 512 201 6");
        if (phase == 7) server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp QA_B 0.5 201 6.5 180 0");
        if (phase >= 8 && phase <= 10) prepareFixture(server, a, phase != 8);
        if (phase == 12) {
            a.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
            a.containerMenu.broadcastChanges();
        }
        if (phase == 13) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "execute in minecraft:overworld run tp QA_A 0.5 201 0.5 0 0");
            a.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
            a.containerMenu.broadcastChanges();
        }
        if (phase >= 14 && phase <= 17) {
            SwordThrow.clearCharge(a);
            a.setGameMode(GameType.SURVIVAL);
            a.getInventory().clearContent();
            template = new ItemStack(Items.IRON_SWORD, 3);
            template.set(DataComponents.CUSTOM_NAME, Component.literal("QA charge phase " + phase));
            a.getInventory().setItem(0, template.copy());
            a.containerMenu.broadcastChanges();
        }
        if (a != null) a.sendSystemMessage(Component.literal("QA_ACCEPTANCE_PHASE " + phase));
        if (b != null) b.sendSystemMessage(Component.literal("QA_ACCEPTANCE_PHASE " + phase));
        log("Started phase " + phase);
    }

    private static void prepareFixture(MinecraftServer server, ServerPlayer player, boolean table) {
        SwordThrow.clearCharge(player);
        player.closeContainer();
        player.getInventory().clearContent();
        player.inventoryMenu.getInputGridSlots().forEach(slot -> slot.set(ItemStack.EMPTY));
        var level = server.overworld();
        level.setBlockAndUpdate(BARREL, Blocks.BARREL.defaultBlockState());
        BarrelBlockEntity barrel = (BarrelBlockEntity) level.getBlockEntity(BARREL);
        barrel.clearContent();
        template = new ItemStack(Items.OAK_PLANKS, 4);
        template.set(DataComponents.CUSTOM_NAME, Component.literal("QA exact-component planks"));
        barrel.setItem(0, template.copy());
        if (table) {
            level.setBlockAndUpdate(TABLE, Blocks.CRAFTING_TABLE.defaultBlockState());
            player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new CraftingMenu(id, inventory, ContainerLevelAccess.create(level, TABLE)), Component.literal("QA Crafting")));
        }
        var recipe = level.recipeAccess().getRecipes().stream()
            .filter(r -> r.id().identifier().toString().equals("minecraft:crafting_table")).findFirst().orElseThrow();
        var menu = (AbstractCraftingMenu) player.containerMenu;
        menu.handlePlacement(false, false, recipe, level, player.getInventory());
        int grid = menu.getInputGridSlots().stream().mapToInt(s -> s.getItem().getCount()).sum();
        if (grid != 4 || !barrel.getItem(0).isEmpty()) fail("Could not prepare real player's nearby crafting fixture: grid=" + grid);
        menu.broadcastChanges();
        fixturePlayer = player;
        log("Nearby recipe fixture placed 4 exact-component planks through production placement");
    }

    private static void verifyFixture(MinecraftServer server, String context) {
        BarrelBlockEntity barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(BARREL);
        if (barrel.getItem(0).getCount() != 4 || !ItemStack.isSameItemSameComponents(barrel.getItem(0), template)) fail(context + ": source count/components changed");
        if (fixturePlayer.inventoryMenu.getInputGridSlots().stream().anyMatch(s -> !s.getItem().isEmpty())) fail(context + ": inventory grid not empty");
        if (fixturePlayer.getInventory().getNonEquipmentItems().stream().anyMatch(s -> s.is(Items.OAK_PLANKS))) fail(context + ": duplicated planks in player inventory");
        if (fixturePlayer.containerMenu instanceof AbstractCraftingMenu menu && menu.getInputGridSlots().stream().anyMatch(s -> !s.getItem().isEmpty())) fail(context + ": crafting grid not empty");
        log("PASS item conservation: " + context);
    }

    private static void fail(String message) { write("runtime-server-failed.txt", message); throw new IllegalStateException(message); }
    private static void write(String name, String text) {
        try { Files.writeString(Path.of(name), text); } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void log(String message) { System.out.println("[RUNTIME-QA-SERVER] " + message); }
}
