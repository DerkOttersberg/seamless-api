package qa.client;

import java.security.MessageDigest;
import java.util.HexFormat;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Verifies the resources actually loaded by the client, not a loose source PNG. */
final class IconQa {
    static void verify(Minecraft client) throws Exception {
        com.google.gson.JsonObject expected;
        try (var stream = IconQa.class.getResourceAsStream("/qa/icon-provenance.json")) {
            if (stream == null) throw new IllegalStateException("Missing generated QA icon provenance");
            try (var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                expected = JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
        for (String id : System.getProperty("qa.iconIds", "").split(",")) {
            if (id.isBlank()) continue;
            var resource = client.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(id, "icon.png")).orElseThrow();
            try (var stream = resource.open()) {
                byte[] data = stream.readAllBytes();
                String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
                if (!digest.equalsIgnoreCase(expected.getAsJsonObject(id).get("sha256").getAsString()))
                    throw new IllegalStateException("Wrong loaded icon for " + id + ": " + digest);
                var decoded = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));
                if (decoded == null || decoded.getWidth() != 400 || decoded.getHeight() != 400) throw new IllegalStateException("Invalid icon " + id);
                System.out.println("QA1211_LOADED_ICON_PASSED=" + id + "; SHA256=" + digest);
            }
        }
    }
}
