package qa.client;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.Screen;

/** Loader-menu inspection is confined to the test-only helper/private display. */
final class ModMenuQa {
    private static final String[] IDS = System.getProperty("qa.iconIds", "").split(",");
    private static final Map<String, String> SETTINGS = Map.of(
        "prettymeteors", "com.derko.prettymeteors.client.PrettyMeteorsConfigScreen",
        "seamlessdeconstructor", "com.seamlessdeconstructor.client.SeamlessDeconstructorConfigScreen",
        "seamless_crafting", "com.derk.easyinventorycrafter.client.EasyInventoryCrafterConfigScreen",
        "swordthrow", "io.github.derkottersberg.swordthrow.client.config.SwordThrowConfigScreen");
    private static int index, ticks, phase;
    private static Screen menu;

    static boolean tick(Minecraft client) throws Exception {
        if (index == IDS.length) return true;
        String id = IDS[index];
        String menuId = !System.getProperty("qa.loader").equals("fabric") && id.equals("seamless_crafting")
            ? "derk_easy_inventory_crafter" : id;
        if (phase == 0) {
            if (ticks == 0) select(client.screen, menuId);
            if (++ticks < 30) return false;
            ticks = 0;
            phase = 1;
            IsolatedQa.capture(client, "qa-mod-menu-" + menuId + ".png", () ->
                System.out.println("QA1211_MOD_MENU_SELECTED=" + menuId));
        } else if (phase == 1) {
            if (!SETTINGS.containsKey(id)) { index++; phase = 0; return false; }
            menu = client.screen;
            var field = menu.getClass().getDeclaredField(System.getProperty("qa.loader").equals("fabric")
                ? "configureButton" : "configButton");
            field.setAccessible(true);
            var button = (AbstractButton) field.get(menu);
            if (button == null || !button.active) throw new IllegalStateException("Disabled mod settings button: " + id);
            button.onPress();
            if (client.screen == menu || !client.screen.getClass().getName().equals(SETTINGS.get(id)))
                throw new IllegalStateException("Mod menu did not open correct settings: " + id);
            // At 854x480 Minecraft clamps requested scale 3 to scale 2.
            // Exercise two genuinely different effective scales, not two labels.
            client.options.guiScale().set(1);
            client.resizeDisplay();
            phase = 2;
        } else if (phase == 2 && ++ticks >= 30) {
            phase = 3;
            ticks = 0;
            if (client.getWindow().getGuiScale() != 1.0) throw new IllegalStateException("GUI scale 1 was not applied");
            IsolatedQa.capture(client, "qa-settings-" + id + "-scale1.png", () -> {
                client.options.guiScale().set(2);
                client.resizeDisplay();
            });
        } else if (phase == 3 && ++ticks >= 30) {
            ticks = 0;
            phase = 0;
            index++;
            if (client.getWindow().getGuiScale() != 2.0) throw new IllegalStateException("GUI scale 2 was not applied");
            IsolatedQa.capture(client, "qa-settings-" + id + "-scale2.png", () -> {
                client.options.guiScale().set(2);
                client.resizeDisplay();
                client.setScreen(menu);
                System.out.println("QA1211_MOD_MENU_SETTINGS_PASSED=" + menuId);
            });
        }
        return false;
    }

    private static void select(Screen screen, String id) throws Exception {
        var listField = screen.getClass().getDeclaredField("modList");
        listField.setAccessible(true);
        var list = (AbstractSelectionList<?>) listField.get(screen);
        boolean fabric = System.getProperty("qa.loader").equals("fabric");
        for (Object entry : list.children()) {
            Object info = entry.getClass().getMethod(fabric ? "getMod" : "getInfo").invoke(entry);
            Object actualId = info.getClass().getMethod(fabric ? "getId" : "getModId").invoke(info);
            if (!id.equals(actualId)) continue;
            Object target = fabric ? list : screen;
            Method select = Arrays.stream(target.getClass().getMethods())
                .filter(method -> method.getName().equals(fabric ? "select" : "setSelected"))
                .filter(method -> method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(entry))
                .findFirst().orElseThrow();
            select.invoke(target, entry);
            return;
        }
        throw new IllegalStateException("Missing mod menu entry: " + id);
    }
}
