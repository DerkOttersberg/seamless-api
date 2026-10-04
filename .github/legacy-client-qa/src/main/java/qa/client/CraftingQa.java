package qa.client;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Tests real screens and packets against the integrated server on private Xvfb. */
final class CraftingQa {
    private static int phase;
    private static int ticks;
    private static BlockPos barrelPos;
    private static BlockPos tablePos;
    private static CompletableFuture<Void> check;

    static boolean tick(Minecraft client) throws Exception {
        if (phase == 6) return true;
        var server = client.getSingleplayerServer();
        if (phase == 0) {
            Class<?> config = Class.forName("com.derk.easyinventorycrafter.EasyInventoryCrafterConfig");
            Object settings = config.getMethod("snapshot").invoke(null);
            settings.getClass().getField("nearbyRadius").setInt(settings, 4);
            config.getMethod("update", settings.getClass()).invoke(null, settings);
            client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            client.setScreen(null);
            barrelPos = client.player.blockPosition().offset(2, 0, 0);
            tablePos = client.player.blockPosition().offset(1, 0, 0);
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(client.player.getUUID());
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                var level = player.serverLevel();
                // This is a disposable copy of a generated GameTest world.
                // Remove old test inventory contents so recipe selection cannot
                // use a second fixture's planks instead of this scenario's NBT stack.
                for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-8, -4, -8),
                        player.blockPosition().offset(8, 4, 8))) {
                    if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof net.minecraft.world.Container inventory)
                        inventory.clearContent();
                }
                level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
                level.setBlockAndUpdate(tablePos, Blocks.CRAFTING_TABLE.defaultBlockState());
                ItemStack planks = new ItemStack(Items.OAK_PLANKS, 12);
                planks.enchant(Enchantments.UNBREAKING, 1);
                ((BarrelBlockEntity) level.getBlockEntity(barrelPos)).setItem(0, planks);
                Recipe<?> recipe = level.getRecipeManager().byKey(new ResourceLocation("minecraft", "crafting_table")).orElseThrow();
                player.awardRecipes(List.of(recipe));
                player.inventoryMenu.broadcastChanges();
            });
            phase = 1;
        } else if (phase == 1 && ++ticks >= 40) {
            client.setScreen(new InventoryScreen(client.player));
            ticks = 0;
            phase = 2;
        } else if (phase == 2 && ++ticks >= 50) {
            List<?> entries = (List<?>) Class.forName("com.derk.easyinventorycrafter.client.NearbyItemsClientState")
                .getMethod("getEntries").invoke(null);
            boolean found = false;
            for (Object entry : entries) {
                ItemStack stack = (ItemStack) entry.getClass().getMethod("stack").invoke(entry);
                long count = (long) entry.getClass().getMethod("count").invoke(entry);
                if (stack.is(Items.OAK_PLANKS) && stack.isEnchanted() && count >= 12) found = true;
            }
            if (!found) throw new IllegalStateException("Nearby enchanted items did not synchronize to inventory UI");
            System.out.println("QA1201_NEARBY_INVENTORY_UI_PASSED; ENTRIES=" + entries.size());
            phase = 3;
            ticks = 0;
            IsolatedQa.capture(client, "qa-crafting-inventory.png", () -> {
                client.setScreen(null);
                client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(tablePos), Direction.UP, tablePos, false));
            });
        } else if (phase == 3 && ++ticks >= 40) {
            if (!(client.screen instanceof CraftingScreen)) throw new IllegalStateException("Crafting table screen did not open");
            Recipe<?> recipe = client.level.getRecipeManager().byKey(new ResourceLocation("minecraft", "crafting_table")).orElseThrow();
            client.gameMode.handlePlaceRecipe(client.player.containerMenu.containerId, recipe, false);
            ticks = 0;
            phase = 4;
        } else if (phase == 4 && ++ticks >= 40) {
            check = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(client.player.getUUID());
                    int grid = player.containerMenu.slots.subList(1, 10).stream().mapToInt(slot -> slot.getItem().getCount()).sum();
                    int stored = ((BarrelBlockEntity) player.serverLevel().getBlockEntity(barrelPos)).getItem(0).getCount();
                    if (grid != 4 || stored != 8) throw new IllegalStateException("Autofill conservation failed: grid=" + grid + ", storage=" + stored);
                    check.complete(null);
                } catch (Exception e) { check.completeExceptionally(e); }
            });
            phase = 5;
            ticks = 0;
        } else if (phase == 5 && check.isDone()) {
            check.join();
            System.out.println("QA1201_CRAFTING_CLIENT_AUTOFILL_PASSED");
            phase = 7;
            IsolatedQa.capture(client, "qa-crafting-table.png", () -> {
                try {
                    Class<?> payload = Class.forName("com.derk.easyinventorycrafter.net.CommonPayload");
                    Object returns = Class.forName("com.derk.easyinventorycrafter.net.ReturnNearbyItemsPacket").getConstructor().newInstance();
                    Class.forName("com.derk.easyinventorycrafter.net.EasyInventoryCrafterNetwork")
                        .getMethod("sendToServer", payload).invoke(null, returns);
                    ticks = 0;
                } catch (Exception e) { throw new IllegalStateException(e); }
            });
        } else if (phase == 7 && ++ticks >= 40) {
            check = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(client.player.getUUID());
                    int grid = player.containerMenu.slots.subList(1, 10).stream().mapToInt(slot -> slot.getItem().getCount()).sum();
                    ItemStack stored = ((BarrelBlockEntity) player.serverLevel().getBlockEntity(barrelPos)).getItem(0);
                    if (grid != 0 || stored.getCount() != 12 || !stored.isEnchanted()) {
                        throw new IllegalStateException("Client return request lost/duplicated NBT items");
                    }
                    check.complete(null);
                } catch (Exception e) { check.completeExceptionally(e); }
            });
            phase = 8;
        } else if (phase == 8 && check.isDone()) {
            check.join();
            System.out.println("QA1201_CRAFTING_CLIENT_RETURN_PASSED");
            phase = 6;
            IsolatedQa.capture(client, "qa-crafting-return.png", () -> client.player.closeContainer());
        }
        return false;
    }
}
