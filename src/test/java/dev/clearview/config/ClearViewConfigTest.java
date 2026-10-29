package dev.clearview.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClearViewConfigTest {
    @TempDir
    Path dir;

    @Test
    void missingFileIsCreatedWithDefaults() {
        Path file = dir.resolve(ClearViewConfig.FILE_NAME);
        ClearViewConfig config = ClearViewConfig.load(file);
        assertTrue(Files.isRegularFile(file));
        assertEquals(ClearViewConfig.DEFAULT_FIRE_LOWERING, config.fireLowering());
        assertEquals(ClearViewConfig.DEFAULT_SHIELD_LOWERING, config.shieldLowering());
        assertTrue(config.hideFireWhenImmune());
        assertTrue(config.hidePumpkin());
        assertTrue(config.hideFrost());
        assertFalse(config.clearAir());
        assertTrue(config.clearLava());
        assertTrue(config.clearWater());
        assertTrue(config.clearPowderSnow());
        assertFalse(config.fullbright());
    }

    @Test
    void savedSettingsComeBack() throws IOException {
        Path file = dir.resolve(ClearViewConfig.FILE_NAME);
        ClearViewConfig config = new ClearViewConfig();
        config.setFireLowering(100);
        config.setShieldLowering(0);
        config.setHideFireWhenImmune(false);
        config.setHidePumpkin(false);
        config.setHideFrost(false);
        config.setClearAir(true);
        config.setClearLava(false);
        config.setClearWater(false);
        config.setClearPowderSnow(false);
        config.setFullbright(true);
        config.save(file);

        ClearViewConfig loaded = ClearViewConfig.load(file);
        assertEquals(100, loaded.fireLowering());
        assertEquals(0, loaded.shieldLowering());
        assertFalse(loaded.hideFireWhenImmune());
        assertFalse(loaded.hidePumpkin());
        assertFalse(loaded.hideFrost());
        assertTrue(loaded.clearAir());
        assertFalse(loaded.clearLava());
        assertFalse(loaded.clearWater());
        assertFalse(loaded.clearPowderSnow());
        assertTrue(loaded.fullbright());
    }

    @Test
    void loweringSnapsToStepsInsideTheRange() {
        ClearViewConfig config = new ClearViewConfig();
        config.setFireLowering(250);
        assertEquals(ClearViewConfig.MAX_LOWERING, config.fireLowering());
        config.setFireLowering(-40);
        assertEquals(ClearViewConfig.MIN_LOWERING, config.fireLowering());
        config.setShieldLowering(47);
        assertEquals(50, config.shieldLowering());
        config.setShieldLowering(44);
        assertEquals(40, config.shieldLowering());
    }

    @Test
    void outOfRangeValuesInTheFileAreClamped() throws IOException {
        Path file = dir.resolve(ClearViewConfig.FILE_NAME);
        Files.writeString(file, "{\"fireLowering\": 999, \"shieldLowering\": -5}", StandardCharsets.UTF_8);
        ClearViewConfig loaded = ClearViewConfig.load(file);
        assertEquals(ClearViewConfig.MAX_LOWERING, loaded.fireLowering());
        assertEquals(ClearViewConfig.MIN_LOWERING, loaded.shieldLowering());
        assertTrue(loaded.clearLava());
    }

    @Test
    void brokenFileIsSetAsideAndReplaced() throws IOException {
        Path file = dir.resolve(ClearViewConfig.FILE_NAME);
        Files.writeString(file, "{not json", StandardCharsets.UTF_8);
        ClearViewConfig loaded = ClearViewConfig.load(file);
        assertEquals(ClearViewConfig.DEFAULT_FIRE_LOWERING, loaded.fireLowering());
        assertTrue(Files.isRegularFile(dir.resolve(ClearViewConfig.FILE_NAME + ".broken")));
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void resetRestoresEveryDefault() {
        ClearViewConfig config = new ClearViewConfig();
        config.setFireLowering(0);
        config.setShieldLowering(100);
        config.setHidePumpkin(false);
        config.setClearWater(false);
        config.setFullbright(true);
        config.resetToDefaults();
        assertEquals(ClearViewConfig.DEFAULT_FIRE_LOWERING, config.fireLowering());
        assertEquals(ClearViewConfig.DEFAULT_SHIELD_LOWERING, config.shieldLowering());
        assertTrue(config.hidePumpkin());
        assertTrue(config.clearWater());
        assertFalse(config.fullbright());
    }
}
