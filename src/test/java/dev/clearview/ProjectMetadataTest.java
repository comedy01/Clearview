package dev.clearview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ProjectMetadataTest {
    private static JsonObject resourceJson(String name) throws IOException {
        InputStream in = ProjectMetadataTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(in, name + " must be on the classpath");
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static boolean has(String name) {
        return ProjectMetadataTest.class.getClassLoader().getResource(name) != null;
    }

    private static String resourceText(String name) throws IOException {
        try (InputStream in = ProjectMetadataTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name + " must be on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void modJson() throws IOException {
        assumeTrue(has("fabric.mod.json"));
        JsonObject mod = resourceJson("fabric.mod.json");
        assertEquals("clearview", mod.get("id").getAsString());
        assertEquals("Clear View", mod.get("name").getAsString());
        assertEquals("client", mod.get("environment").getAsString());
        assertFalse(mod.get("version").getAsString().contains("$"), "version placeholder must be expanded");

        JsonObject entrypoints = mod.getAsJsonObject("entrypoints");
        assertEquals("dev.clearview.fabric.ClearViewFabric", entrypoints.getAsJsonArray("client").get(0).getAsString());
        for (String key : entrypoints.keySet()) {
            for (JsonElement entry : entrypoints.getAsJsonArray(key)) {
                assertClassExists(entry.getAsString());
            }
        }

        JsonObject depends = mod.getAsJsonObject("depends");
        assertFalse(depends.has("modmenu"), "Mod Menu must stay optional");
        assertTrue(mod.getAsJsonObject("suggests").has("modmenu"));
    }

    @Test
    void neoforgeMetadata() throws IOException {
        assumeTrue(has("META-INF/neoforge.mods.toml"));
        String toml = resourceText("META-INF/neoforge.mods.toml");
        assertTrue(toml.contains("modId=\"clearview\""), toml);
        assertTrue(toml.contains("displayName=\"Clear View\""), toml);
        assertTrue(toml.contains("config=\"clearview.mixins.json\""), toml);
        assertFalse(toml.contains("${"), "placeholders must be expanded");
        assertClassExists("dev.clearview.neoforge.ClearViewNeoForge");
    }

    @Test
    void forgeMetadata() throws IOException {
        assumeTrue(has("META-INF/mods.toml"));
        String toml = resourceText("META-INF/mods.toml");
        assertTrue(toml.contains("modId=\"clearview\""), toml);
        assertTrue(toml.contains("displayName=\"Clear View\""), toml);
        assertTrue(toml.contains("modId=\"forge\""), toml);
        assertFalse(toml.contains("${"), "placeholders must be expanded");
        assertEquals("clearview.refmap.json", resourceJson("clearview.mixins.json").get("refmap").getAsString());
        assertEquals(15, resourceJson("pack.mcmeta").getAsJsonObject("pack").get("pack_format").getAsInt());
        assertClassExists("dev.clearview.forge.ClearViewForge");
    }

    @Test
    void mixinsExist() throws IOException {
        JsonObject mixins = resourceJson("clearview.mixins.json");
        assertEquals("dev.clearview.mixin", mixins.get("package").getAsString());
        JsonArray client = mixins.getAsJsonArray("client");
        assertEquals(8, client.size());
        for (JsonElement entry : client) {
            assertClassExists("dev.clearview.mixin." + entry.getAsString());
        }
        assertFalse(mixins.has("mixins"), "all mixins are client-only");
        assertFalse(mixins.has("server"));
    }

    @Test
    void everySettingIsTranslated() throws IOException {
        JsonObject lang = resourceJson("assets/clearview/lang/en_us.json");
        String[] options = {"fire", "fire_immune", "shield", "pumpkin", "frost", "air", "lava", "water", "powder_snow",
                "fullbright", "keys", "reset"};
        for (String option : options) {
            assertTrue(lang.has("clearview.options." + option), option);
            assertTrue(lang.has("clearview.options." + option + ".tooltip"), option + " tooltip");
        }
        for (String key : new String[] {"clearview.options.title", "clearview.options.section.overlays",
                "clearview.options.section.fog", "clearview.options.section.brightness",
                "key.category.clearview.main", "key.clearview.fullbright", "key.clearview.settings",
                "clearview.message.fullbright.on", "clearview.message.fullbright.off"}) {
            assertTrue(lang.has(key), key);
        }
    }

    @Test
    void iconsExist() {
        for (String size : new String[] {"16", "32", "128"}) {
            assertTrue(has("assets/clearview/icon" + size + ".png"), "icon" + size);
        }
    }

    @Test
    void noOtherProjectReferences() throws IOException {
        String[] forbidden = {"limn", "lidless", "reticle", "loupe", "easycrosshair", "easy crosshair", "swivel",
                "heartline"};
        try (Stream<Path> files = Stream.of(Path.of("src/main"), Path.of("src/loader"), Path.of("src/compat"),
                        Path.of("build.gradle"), Path.of("gradle.properties"), Path.of("settings.gradle"), Path.of("README.md"))
                .filter(Files::exists)
                .flatMap(ProjectMetadataTest::walk)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> !file.toString().endsWith(".png"))
                    .forEach(file -> {
                        String text;
                        try {
                            text = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                        } catch (IOException e) {
                            throw new IllegalStateException(file.toString(), e);
                        }
                        for (String token : forbidden) {
                            assertFalse(text.contains(token), file + " mentions \"" + token + "\"");
                        }
                    });
        }
    }

    private static void assertClassExists(String className) {
        String path = className.replace('.', '/') + ".class";
        assertNotNull(ProjectMetadataTest.class.getClassLoader().getResource(path), path + " must be compiled");
    }

    private static Stream<Path> walk(Path root) {
        try {
            return Files.walk(root);
        } catch (IOException e) {
            throw new IllegalStateException(root.toString(), e);
        }
    }
}
