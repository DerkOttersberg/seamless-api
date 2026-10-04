package qa.client;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Real menu and processing checks, confined to a disposable Xvfb profile. */
final class WorkbenchQa {
    private static int phase, ticks;
    private static BlockPos pos;
    private static CompletableFuture<Void> check;

    static boolean tick(Minecraft client) throws Exception {
        if (phase == 5) return true;
        var server = client.getSingleplayerServer();
        if (phase == 0) {
            Class<?> config = Class.forName("com.seamlessdeconstructor.config.ModConfig");
            Class<?> settings = Class.forName("com.seamlessdeconstructor.config.ModConfig$Settings");
            config.getMethod("update", settings).invoke(null,
                settings.getConstructor(int.class, int.class, int.class).newInstance(20, 0, 0));
            pos = client.player.blockPosition().offset(-1, 0, 0);
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(client.player.getUUID());
                player.serverLevel().setBlockAndUpdate(pos, BuiltInRegistries.BLOCK
                    .get(new ResourceLocation("seamlessdeconstructor", "reverse_deconstructor")).defaultBlockState());
            });
            phase = 1;
        } else if (phase == 1 && ++ticks >= 30) {
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            ticks = 0; phase = 2;
        } else if (phase == 2 && ++ticks >= 30) {
            if (!client.screen.getClass().getName().equals("com.seamlessdeconstructor.screen.ReverseDeconstructorScreen"))
                throw new IllegalStateException("Workbench menu did not open through the real loader packet");
            if (client.player.containerMenu.getSlot(1).hasItem())
                throw new IllegalStateException("Empty-book-slot fixture is not empty");
            var position = (qa.client.mixin.ContainerQaAccess) client.screen;
            // Internal app coordinates on private Xvfb, never OS input.
            var mouse = (qa.client.mixin.MouseQaAccess) client.mouseHandler;
            double scale = client.getWindow().getGuiScale();
            mouse.qaX((position.qaLeft() + 38) * scale);
            mouse.qaY((position.qaTop() + 50) * scale);
            ticks = 0; phase = 3;
        } else if (phase == 3 && ++ticks >= 15) {
            IsolatedQa.capture(client, "qa-workbench-book-hint.png", () -> server.execute(() -> {
                var player = server.getPlayerList().getPlayer(client.player.getUUID());
                ((Container) player.serverLevel().getBlockEntity(pos)).setItem(0, new ItemStack(Items.IRON_PICKAXE));
            }));
            ticks = 0; phase = 4;
        } else if (phase == 4 && ++ticks >= 70) {
            if (check == null) {
                check = new CompletableFuture<>();
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayer(client.player.getUUID());
                        Container inventory = (Container) player.serverLevel().getBlockEntity(pos);
                        int irons = 0, sticks = 0;
                        for (int slot = 2; slot < 8; slot++) {
                            ItemStack stack = inventory.getItem(slot);
                            if (stack.is(Items.IRON_INGOT)) irons += stack.getCount();
                            if (stack.is(Items.STICK)) sticks += stack.getCount();
                        }
                        if (!inventory.getItem(0).isEmpty() || irons != 3 || sticks != 2)
                            throw new IllegalStateException("Live workbench salvage failed: iron=" + irons + ", sticks=" + sticks);
                        check.complete(null);
                    } catch (Exception e) { check.completeExceptionally(e); }
                });
            } else if (check.isDone()) {
                check.join();
                System.out.println("QA1201_WORKBENCH_MENU_AND_PROCESSING_PASSED");
                phase = 5;
                IsolatedQa.capture(client, "qa-workbench-salvage.png", () -> client.player.closeContainer());
            }
        }
        return false;
    }
}
