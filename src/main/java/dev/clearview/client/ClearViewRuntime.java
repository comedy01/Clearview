package dev.clearview.client;

import dev.clearview.client.gui.ClearViewSettingsScreen;
import dev.clearview.config.ClearViewConfig;
import dev.clearview.mixin.OptionInstanceAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ClearViewRuntime {
    private static final float MAX_FIRE_DROP = 0.25F;
    private static final float MAX_SHIELD_DROP = 0.3F;
    private static final Double BRIGHT = 16.0;

    private static Object userGamma;

    private ClearViewRuntime() {
    }

    public static boolean hideFire() {
        LocalPlayer player = Minecraft.getInstance().player;
        return ClearViewClient.config().hideFireWhenImmune()
                && player != null
                && (player.isCreative() || player.hasEffect(MobEffects.FIRE_RESISTANCE));
    }

    public static float fireDrop() {
        return ClearViewClient.config().fireLowering() / 100.0F * MAX_FIRE_DROP;
    }

    public static float shieldDrop(ItemStack stack) {
        return stack.is(Items.SHIELD) ? ClearViewClient.config().shieldLowering() / 100.0F * MAX_SHIELD_DROP : 0.0F;
    }

    public static boolean hideOverlay(String texture) {
        ClearViewConfig config = ClearViewClient.config();
        if (texture.contains("pumpkinblur")) {
            return config.hidePumpkin();
        }
        return texture.contains("powder_snow_outline") && config.hideFrost();
    }

    public static boolean clears(Medium medium, Entity viewer) {
        ClearViewConfig config = ClearViewClient.config();
        switch (medium) {
            case AIR:
                return config.clearAir() && !blinded(viewer);
            case LAVA:
                return config.clearLava();
            case POWDER_SNOW:
                return config.clearPowderSnow();
            default:
                return config.clearWater() && !blinded(viewer);
        }
    }

    private static boolean blinded(Entity viewer) {
        return viewer instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    public static void beforeKeybinds() {
        Minecraft mc = Minecraft.getInstance();
        ClearViewConfig config = ClearViewClient.config();

        KeyMapping fullbright = ClearViewClient.fullbrightKey();
        while (fullbright != null && fullbright.consumeClick()) {
            config.setFullbright(!config.fullbright());
            ClearViewClient.saveConfig();
            if (mc.player != null) {
                Messages.overlay(mc.player, Component.translatable(
                        config.fullbright() ? "clearview.message.fullbright.on" : "clearview.message.fullbright.off"));
            }
        }

        KeyMapping settings = ClearViewClient.settingsKey();
        boolean open = false;
        while (settings != null && settings.consumeClick()) {
            open = true;
        }

        applyBrightness();
        if (open) {
            ClearViewSettingsScreen.open(mc);
        }
    }

    public static void applyBrightness() {
        OptionInstanceAccessor gamma = (OptionInstanceAccessor) (Object) Minecraft.getInstance().options.gamma();
        Object current = gamma.clearview$value();
        if (ClearViewClient.config().fullbright()) {
            if (!BRIGHT.equals(current)) {
                userGamma = current;
                gamma.clearview$setValue(BRIGHT);
            }
        } else if (userGamma != null) {
            if (BRIGHT.equals(current)) {
                gamma.clearview$setValue(userGamma);
            }
            userGamma = null;
        }
    }

    public static void beforeSave(Object gamma) {
        if (userGamma != null) {
            ((OptionInstanceAccessor) gamma).clearview$setValue(userGamma);
        }
    }

    public static void afterSave(Object gamma) {
        if (userGamma != null) {
            ((OptionInstanceAccessor) gamma).clearview$setValue(BRIGHT);
        }
    }
}
