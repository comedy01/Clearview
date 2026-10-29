package dev.clearview.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.clearview.config.ClearViewConfig;
import net.minecraft.client.KeyMapping;

import java.nio.file.Path;

public final class ClearViewClient {
    public static final String MOD_ID = "clearview";

    private static ClearViewConfig config = new ClearViewConfig();
    private static Path configPath;
    private static KeyMapping fullbrightKey;
    private static KeyMapping settingsKey;

    private ClearViewClient() {
    }

    public static void init(Path configDir) {
        configPath = configDir.resolve(ClearViewConfig.FILE_NAME);
        config = ClearViewConfig.load(configPath);
    }

    public static KeyMapping createFullbrightKey() {
        return KeyFactory.create("key.clearview.fullbright", InputConstants.KEY_G);
    }

    public static KeyMapping createSettingsKey() {
        return KeyFactory.create("key.clearview.settings", InputConstants.UNKNOWN.getValue());
    }

    public static void setKeys(KeyMapping fullbright, KeyMapping settings) {
        fullbrightKey = fullbright;
        settingsKey = settings;
    }

    public static KeyMapping fullbrightKey() {
        return fullbrightKey;
    }

    public static KeyMapping settingsKey() {
        return settingsKey;
    }

    public static ClearViewConfig config() {
        return config;
    }

    public static Path configPath() {
        return configPath;
    }

    public static void saveConfig() {
        config.saveQuietly(configPath);
    }
}
