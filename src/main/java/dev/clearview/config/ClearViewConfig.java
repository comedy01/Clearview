package dev.clearview.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ClearViewConfig {
    public static final String FILE_NAME = "clearview.json";
    public static final int MIN_LOWERING = 0;
    public static final int MAX_LOWERING = 100;
    public static final int LOWERING_STEP = 10;
    public static final int DEFAULT_FIRE_LOWERING = 60;
    public static final int DEFAULT_SHIELD_LOWERING = 40;
    public static final boolean DEFAULT_HIDE_FIRE_WHEN_IMMUNE = true;
    public static final boolean DEFAULT_HIDE_PUMPKIN = true;
    public static final boolean DEFAULT_HIDE_FROST = true;
    public static final boolean DEFAULT_CLEAR_AIR = false;
    public static final boolean DEFAULT_CLEAR_LAVA = true;
    public static final boolean DEFAULT_CLEAR_WATER = true;
    public static final boolean DEFAULT_CLEAR_POWDER_SNOW = true;
    public static final boolean DEFAULT_FULLBRIGHT = false;

    private static final Logger LOGGER = LoggerFactory.getLogger("clearview");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("fireLowering")
    private int fireLowering = DEFAULT_FIRE_LOWERING;

    @SerializedName("hideFireWhenImmune")
    private boolean hideFireWhenImmune = DEFAULT_HIDE_FIRE_WHEN_IMMUNE;

    @SerializedName("shieldLowering")
    private int shieldLowering = DEFAULT_SHIELD_LOWERING;

    @SerializedName("hidePumpkin")
    private boolean hidePumpkin = DEFAULT_HIDE_PUMPKIN;

    @SerializedName("hideFrost")
    private boolean hideFrost = DEFAULT_HIDE_FROST;

    @SerializedName("clearAir")
    private boolean clearAir = DEFAULT_CLEAR_AIR;

    @SerializedName("clearLava")
    private boolean clearLava = DEFAULT_CLEAR_LAVA;

    @SerializedName("clearWater")
    private boolean clearWater = DEFAULT_CLEAR_WATER;

    @SerializedName("clearPowderSnow")
    private boolean clearPowderSnow = DEFAULT_CLEAR_POWDER_SNOW;

    @SerializedName("fullbright")
    private boolean fullbright = DEFAULT_FULLBRIGHT;

    public int fireLowering() {
        return fireLowering;
    }

    public void setFireLowering(int value) {
        fireLowering = lowering(value);
    }

    public boolean hideFireWhenImmune() {
        return hideFireWhenImmune;
    }

    public void setHideFireWhenImmune(boolean value) {
        hideFireWhenImmune = value;
    }

    public int shieldLowering() {
        return shieldLowering;
    }

    public void setShieldLowering(int value) {
        shieldLowering = lowering(value);
    }

    public boolean hidePumpkin() {
        return hidePumpkin;
    }

    public void setHidePumpkin(boolean value) {
        hidePumpkin = value;
    }

    public boolean hideFrost() {
        return hideFrost;
    }

    public void setHideFrost(boolean value) {
        hideFrost = value;
    }

    public boolean clearAir() {
        return clearAir;
    }

    public void setClearAir(boolean value) {
        clearAir = value;
    }

    public boolean clearLava() {
        return clearLava;
    }

    public void setClearLava(boolean value) {
        clearLava = value;
    }

    public boolean clearWater() {
        return clearWater;
    }

    public void setClearWater(boolean value) {
        clearWater = value;
    }

    public boolean clearPowderSnow() {
        return clearPowderSnow;
    }

    public void setClearPowderSnow(boolean value) {
        clearPowderSnow = value;
    }

    public boolean fullbright() {
        return fullbright;
    }

    public void setFullbright(boolean value) {
        fullbright = value;
    }

    public void resetToDefaults() {
        fireLowering = DEFAULT_FIRE_LOWERING;
        hideFireWhenImmune = DEFAULT_HIDE_FIRE_WHEN_IMMUNE;
        shieldLowering = DEFAULT_SHIELD_LOWERING;
        hidePumpkin = DEFAULT_HIDE_PUMPKIN;
        hideFrost = DEFAULT_HIDE_FROST;
        clearAir = DEFAULT_CLEAR_AIR;
        clearLava = DEFAULT_CLEAR_LAVA;
        clearWater = DEFAULT_CLEAR_WATER;
        clearPowderSnow = DEFAULT_CLEAR_POWDER_SNOW;
        fullbright = DEFAULT_FULLBRIGHT;
    }

    private static int lowering(int value) {
        int clamped = Math.max(MIN_LOWERING, Math.min(MAX_LOWERING, value));
        return Math.round(clamped / (float) LOWERING_STEP) * LOWERING_STEP;
    }

    private void sanitize() {
        setFireLowering(fireLowering);
        setShieldLowering(shieldLowering);
    }

    public static ClearViewConfig load(Path file) {
        if (!Files.isRegularFile(file)) {
            ClearViewConfig fresh = new ClearViewConfig();
            fresh.saveQuietly(file);
            return fresh;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ClearViewConfig loaded = GSON.fromJson(reader, ClearViewConfig.class);
            if (loaded == null) {
                throw new JsonParseException("config file is empty");
            }
            loaded.sanitize();
            return loaded;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}; using defaults. {}", file, e.toString());
            moveAside(file);
            ClearViewConfig fresh = new ClearViewConfig();
            fresh.saveQuietly(file);
            return fresh;
        }
    }

    public void save(Path file) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void saveQuietly(Path file) {
        try {
            save(file);
        } catch (IOException e) {
            LOGGER.warn("Could not save {}: {}", file, e.toString());
        }
    }

    private static void moveAside(Path file) {
        try {
            Files.move(
                    file,
                    file.resolveSibling(file.getFileName() + ".broken"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not back up unreadable config {}: {}", file, e.toString());
        }
    }
}
