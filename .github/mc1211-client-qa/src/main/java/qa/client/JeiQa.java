package qa.client;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

/** Query the installed JEI runtime, not a manually instantiated plugin. */
final class JeiQa {
    static void verify(Minecraft client) throws Exception {
        if (!Boolean.getBoolean("qa.expectjei")) return;
        Object runtime = Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
        Class<?> runtimeApi = Class.forName("mezz.jei.api.runtime.IJeiRuntime");
        Object helper = runtimeApi.getMethod("getScreenHelper").invoke(runtime);
        Object overlay = runtimeApi.getMethod("getIngredientListOverlay").invoke(runtime);
        if (!(boolean) Class.forName("mezz.jei.api.runtime.IIngredientListOverlay")
                .getMethod("isListDisplayed").invoke(overlay))
            throw new IllegalStateException("JEI ingredient overlay is not displayed");
        var stream = (Stream<?>) Class.forName("mezz.jei.api.runtime.IScreenHelper")
            .getMethod("getGuiExclusionAreas", Screen.class).invoke(helper, client.screen);
        List<?> actual;
        try (stream) { actual = stream.toList(); }
        List<?> expected = (List<?>) Class.forName("com.derk.easyinventorycrafter.client.NearbyPanelAccess")
            .getMethod("derk$getOverlayExclusionBounds").invoke(client.screen);
        if (expected.isEmpty()) throw new IllegalStateException("Nearby panel has no visible exclusion bounds");
        for (Object bounds : expected) {
            int x = (int) bounds.getClass().getMethod("x").invoke(bounds);
            int y = (int) bounds.getClass().getMethod("y").invoke(bounds);
            int width = (int) bounds.getClass().getMethod("width").invoke(bounds);
            int height = (int) bounds.getClass().getMethod("height").invoke(bounds);
            boolean registered = actual.stream().map(Rect2i.class::cast).anyMatch(rect ->
                rect.getX() == x && rect.getY() == y && rect.getWidth() == width && rect.getHeight() == height);
            if (!registered) throw new IllegalStateException("Installed JEI did not reserve nearby panel " + bounds);
        }
        System.out.println("QA1211_JEI_RUNTIME_EXCLUSIONS_PASSED=" + client.screen.getClass().getSimpleName()
            + "; AREAS=" + expected.size());
    }
}
