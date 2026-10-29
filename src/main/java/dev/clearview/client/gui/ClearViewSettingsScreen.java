package dev.clearview.client.gui;

import dev.clearview.client.ClearViewClient;
import dev.clearview.client.ClearViewRuntime;
import dev.clearview.config.ClearViewConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class ClearViewSettingsScreen extends ClearViewOptionsScreen {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 20;

    public ClearViewSettingsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, Component.translatable("clearview.options.title"));
    }

    public static void open(Minecraft client) {
        ScreenOpener.open(client, new ClearViewSettingsScreen(null, client.options));
    }

    @Override
    protected void addOptions() {
        ClearViewConfig config = ClearViewClient.config();

        addHeader(Component.translatable("clearview.options.section.overlays"));

        addRow(
                new LoweringSlider("clearview.options.fire", config.fireLowering(), config::setFireLowering),
                toggle("clearview.options.fire_immune", config::hideFireWhenImmune, config::setHideFireWhenImmune));

        addRow(
                new LoweringSlider("clearview.options.shield", config.shieldLowering(), config::setShieldLowering),
                toggle("clearview.options.pumpkin", config::hidePumpkin, config::setHidePumpkin));

        addRow(toggle("clearview.options.frost", config::hideFrost, config::setHideFrost), null);

        addHeader(Component.translatable("clearview.options.section.fog"));

        addRow(
                toggle("clearview.options.air", config::clearAir, config::setClearAir),
                toggle("clearview.options.lava", config::clearLava, config::setClearLava));

        addRow(
                toggle("clearview.options.water", config::clearWater, config::setClearWater),
                toggle("clearview.options.powder_snow", config::clearPowderSnow, config::setClearPowderSnow));

        addHeader(Component.translatable("clearview.options.section.brightness"));

        addRow(
                toggle("clearview.options.fullbright", config::fullbright, value -> {
                    config.setFullbright(value);
                    ClearViewRuntime.applyBrightness();
                }),
                keyBindsButton());

        addRow(resetButton(config), null);
    }

    @Override
    public void removed() {
        super.removed();
        ClearViewClient.saveConfig();
    }

    private AbstractWidget keyBindsButton() {
        return Button.builder(Component.translatable("clearview.options.keys"),
                        button -> ScreenOpener.open(minecraft, KeyBinds.screen(this, options)))
                .width(WIDTH)
                .tooltip(Tooltip.create(Component.translatable("clearview.options.keys.tooltip")))
                .build();
    }

    private AbstractWidget resetButton(ClearViewConfig config) {
        return Button.builder(Component.translatable("clearview.options.reset"), button -> {
                    config.resetToDefaults();
                    ClearViewRuntime.applyBrightness();
                    ScreenOpener.open(minecraft, new ClearViewSettingsScreen(lastScreen, options));
                })
                .width(WIDTH)
                .tooltip(Tooltip.create(Component.translatable("clearview.options.reset.tooltip")))
                .build();
    }

    private static AbstractWidget toggle(String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        return Button.builder(toggleLabel(key, getter.getAsBoolean()), button -> {
                    setter.accept(!getter.getAsBoolean());
                    button.setMessage(toggleLabel(key, getter.getAsBoolean()));
                })
                .width(WIDTH)
                .tooltip(Tooltip.create(Component.translatable(key + ".tooltip")))
                .build();
    }

    private static Component toggleLabel(String key, boolean on) {
        Component state = Component.translatable(on ? "options.on" : "options.off");
        return Component.translatable("options.generic_value", Component.translatable(key), state);
    }

    private static final class LoweringSlider extends AbstractSliderButton {
        private static final int RANGE = ClearViewConfig.MAX_LOWERING - ClearViewConfig.MIN_LOWERING;

        private final String captionKey;
        private final IntConsumer onChange;

        LoweringSlider(String captionKey, int initial, IntConsumer onChange) {
            super(0, 0, WIDTH, HEIGHT, Component.empty(), (initial - ClearViewConfig.MIN_LOWERING) / (double) RANGE);
            this.captionKey = captionKey;
            this.onChange = onChange;
            setTooltip(Tooltip.create(Component.translatable(captionKey + ".tooltip")));
            updateMessage();
        }

        private int current() {
            int raw = ClearViewConfig.MIN_LOWERING + (int) Math.round(value * RANGE);
            return Math.round(raw / (float) ClearViewConfig.LOWERING_STEP) * ClearViewConfig.LOWERING_STEP;
        }

        @Override
        protected void updateMessage() {
            int percent = current();
            Component shown = percent == 0
                    ? Component.translatable("options.off")
                    : Component.literal(percent + "%");
            setMessage(Component.translatable("options.generic_value", Component.translatable(captionKey), shown));
        }

        @Override
        protected void applyValue() {
            int percent = current();
            value = (percent - ClearViewConfig.MIN_LOWERING) / (double) RANGE;
            onChange.accept(percent);
        }
    }
}
