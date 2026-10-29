package dev.clearview.selftest;

import dev.clearview.client.ClearViewClient;
import dev.clearview.client.Medium;
import dev.clearview.client.gui.ClearViewSettingsScreen;
import dev.clearview.config.ClearViewConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.ChatVisiblity;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public final class ClearViewSelfTest {
    private static final String WORLD = "clearview-selftest";
    private static final int TIMEOUT = 2400;
    private static final double BRIGHT = 16.0;

    private static volatile int frames;

    private final List<Step> steps = new ArrayList<>();
    private final List<String> shots = new ArrayList<>();
    private final Set<String> existingShots = new HashSet<>();
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int delay;
    private int waited;
    private int seenFrames;
    private int patience;
    private double userGamma;

    private record Step(int delay, BooleanSupplier ready, Runnable action) {
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 40) {
                started = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.tutorialStep = TutorialSteps.NONE;
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                rememberShots(mc);
                plan(mc);
                delay = steps.get(0).delay();
                log("creating world");
                deleteWorld(mc, WORLD);
                Worlds.create(mc, WORLD, true, 0L);
            }
            return;
        }
        int drawn = frames;
        if (drawn == seenFrames) {
            return;
        }
        seenFrames = drawn;
        if (delay > 0) {
            delay--;
            return;
        }
        Step step = steps.get(index);
        if (!step.ready().getAsBoolean()) {
            if (++waited > TIMEOUT) {
                finish(mc, new AssertionError("timed out at step " + index));
            }
            return;
        }
        waited = 0;
        index++;
        try {
            step.action().run();
        } catch (Throwable e) {
            finish(mc, e);
            return;
        }
        if (index >= steps.size()) {
            finish(mc, null);
        } else {
            delay = steps.get(index).delay();
        }
    }

    public static void frame() {
        frames++;
    }

    private void then(int ticks, Runnable action) {
        steps.add(new Step(ticks, () -> true, action));
    }

    private void when(BooleanSupplier ready, Runnable action) {
        steps.add(new Step(0, ready, action));
    }

    private void within(int ticks, BooleanSupplier ready, Runnable action) {
        then(1, () -> patience = ticks);
        when(() -> ready.getAsBoolean() || --patience < 0, action);
    }

    private void finish(Minecraft mc, Throwable failure) {
        finished = true;
        nameShots(mc);
        if (failure == null) {
            log("ALL CHECKS PASSED");
        } else {
            log("FAILED: " + failure);
            failure.printStackTrace();
        }
        ClearViewConfig config = ClearViewClient.config();
        config.resetToDefaults();
        ClearViewClient.saveConfig();
        mc.stop();
    }

    private void plan(Minecraft mc) {
        ClearViewConfig config = ClearViewClient.config();

        when(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null, () -> log("world loaded"));
        then(40, () -> {
            config.resetToDefaults();
            run(mc, "time set 6000");
            run(mc, "gamemode survival @p");
            run(mc, "effect give @p minecraft:resistance 600 4 true");
            run(mc, "tp @p 0.5 -60 0.5 0 0");
        });
        planShield(mc, config);
        planPumpkin(mc, config);
        planFire(mc, config);
        planFog(mc, config, Medium.LAVA, "lava");
        planFog(mc, config, Medium.WATER, "water");
        planFog(mc, config, Medium.POWDER_SNOW, "powder_snow");
        planAir(mc, config);
        planFullbright(mc, config);
        planSettings(mc, config);
    }

    private void planShield(Minecraft mc, ClearViewConfig config) {
        then(20, () -> {
            config.setShieldLowering(0);
            run(mc, "item replace entity @p weapon.offhand with minecraft:shield");
        });
        then(30, () -> {
            check(Probe.shieldLowered() == 0, "the shield was lowered with Lower Shield off");
            screenshot(mc, "shield-vanilla");
            config.setShieldLowering(ClearViewConfig.DEFAULT_SHIELD_LOWERING);
        });
        within(60, () -> Probe.shieldLowered() > 0, () -> {
            check(Probe.shieldLowered() > 0, "the held shield was not lowered");
            screenshot(mc, "shield-low");
            run(mc, "item replace entity @p weapon.offhand with minecraft:air");
        });
    }

    private void planPumpkin(Minecraft mc, ClearViewConfig config) {
        then(10, () -> {
            config.setHidePumpkin(false);
            run(mc, "item replace entity @p armor.head with minecraft:carved_pumpkin");
        });
        within(60, () -> Probe.shown("pumpkinblur"), () -> {
            check(Probe.shown("pumpkinblur"), "the pumpkin overlay never reached Clear View");
            check(!Probe.hidden("pumpkinblur"), "the pumpkin overlay was hidden with the setting off");
            screenshot(mc, "pumpkin-vanilla");
            config.setHidePumpkin(true);
        });
        within(60, () -> Probe.hidden("pumpkinblur"), () -> {
            check(Probe.hidden("pumpkinblur"), "the pumpkin overlay was not hidden");
            screenshot(mc, "pumpkin-hidden");
            run(mc, "item replace entity @p armor.head with minecraft:air");
        });
    }

    private void planFire(Minecraft mc, ClearViewConfig config) {
        then(10, () -> {
            config.setFireLowering(0);
            run(mc, "setblock 0 -60 0 minecraft:fire");
        });
        within(100, () -> mc.player.isOnFire(), () -> check(mc.player.isOnFire(), "the player did not catch fire"));
        then(10, () -> {
            check(Probe.fireLowered() == 0, "the fire was lowered with Lower Fire off");
            screenshot(mc, "fire-vanilla");
            config.setFireLowering(ClearViewConfig.DEFAULT_FIRE_LOWERING);
        });
        within(60, () -> Probe.fireLowered() > 0, () -> {
            check(Probe.fireLowered() > 0, "the fire overlay was not lowered");
            check(Probe.fireHidden() == 0, "the fire was hidden without Fire Resistance");
            screenshot(mc, "fire-low");
            run(mc, "effect give @p minecraft:fire_resistance 600 0 true");
        });
        within(100, () -> Probe.fireHidden() > 0, () -> {
            check(Probe.fireHidden() > 0, "harmless fire was not hidden");
            screenshot(mc, "fire-hidden");
            run(mc, "setblock 0 -60 0 minecraft:air");
        });
    }

    private void planFog(Minecraft mc, ClearViewConfig config, Medium medium, String block) {
        then(10, () -> {
            setClear(config, medium, false);
            Probe.reset();
            run(mc, "fill 3 -61 3 19 -56 7 minecraft:glass");
            run(mc, "fill 4 -60 4 18 -57 6 minecraft:" + block);
            run(mc, "setblock 18 -58 5 minecraft:gold_block");
            run(mc, "tp @p 5.5 -59 5.5 -90 0");
        });
        within(200, () -> Probe.fogged(medium), () -> {
            check(Probe.fogged(medium), "the " + block + " fog never reached Clear View");
            check(!Probe.cleared(medium), "the " + block + " fog was cleared with the setting off");
        });
        then(20, () -> {
            screenshot(mc, block + "-vanilla");
            setClear(config, medium, true);
        });
        within(60, () -> Probe.cleared(medium), () -> check(Probe.cleared(medium), "the " + block + " fog was not cleared"));
        if (medium == Medium.POWDER_SNOW) {
            within(300, () -> Probe.hidden("powder_snow_outline"), () -> check(Probe.hidden("powder_snow_outline"),
                    "the frost overlay was not hidden"));
        }
        then(20, () -> {
            screenshot(mc, block + "-clear");
            run(mc, "tp @p 0.5 -60 0.5 0 0");
            run(mc, "fill 3 -61 3 19 -56 7 minecraft:air");
            run(mc, "fill 3 -61 3 19 -61 7 minecraft:grass_block");
        });
    }

    private void planAir(Minecraft mc, ClearViewConfig config) {
        then(20, () -> {
            Probe.reset();
            run(mc, "effect clear @p");
        });
        within(60, () -> Probe.fogged(Medium.AIR), () -> {
            check(Probe.fogged(Medium.AIR), "the distance fog never reached Clear View");
            check(!Probe.cleared(Medium.AIR), "the distance fog was removed with the setting off");
            screenshot(mc, "fog-vanilla");
            config.setClearAir(true);
        });
        within(60, () -> Probe.cleared(Medium.AIR), () -> check(Probe.cleared(Medium.AIR),
                "the distance fog was not removed"));
        then(10, () -> {
            screenshot(mc, "fog-removed");
            run(mc, "effect give @p minecraft:blindness 600 0 true");
        });
        within(100, () -> mc.player.hasEffect(MobEffects.BLINDNESS), Probe::reset);
        then(10, () -> {
            check(Probe.fogged(Medium.AIR), "Blindness did not keep its fog");
            check(!Probe.cleared(Medium.AIR), "the fog was removed during Blindness");
            run(mc, "effect clear @p");
            config.setClearAir(false);
        });
    }

    private static void setClear(ClearViewConfig config, Medium medium, boolean value) {
        if (medium == Medium.LAVA) {
            config.setClearLava(value);
        } else if (medium == Medium.WATER) {
            config.setClearWater(value);
        } else {
            config.setClearPowderSnow(value);
        }
    }

    private void planFullbright(Minecraft mc, ClearViewConfig config) {
        then(20, () -> {
            run(mc, "effect clear @p");
            run(mc, "gamemode creative @p");
            run(mc, "time set 18000");
        });
        then(40, () -> {
            userGamma = mc.options.gamma().get();
            check(userGamma != BRIGHT, "the brightness option started at the fullbright value");
            screenshot(mc, "night-vanilla");
            config.setFullbright(true);
        });
        then(10, () -> {
            check(mc.options.gamma().get() == BRIGHT, "Fullbright did not raise the brightness: "
                    + mc.options.gamma().get());
            screenshot(mc, "night-fullbright");
            mc.options.save();
            String saved = read(mc.gameDirectory.toPath().resolve("options.txt"));
            check(saved.contains("gamma:" + userGamma), "options.txt did not keep the player's brightness: "
                    + gammaLine(saved));
            check(mc.options.gamma().get() == BRIGHT, "saving the options switched Fullbright off");
            config.setFullbright(false);
        });
        then(10, () -> {
            check(mc.options.gamma().get() == userGamma, "switching Fullbright off did not restore the brightness: "
                    + mc.options.gamma().get());
            run(mc, "time set 6000");
        });
    }

    private static String gammaLine(String options) {
        return options.lines().filter(line -> line.startsWith("gamma:")).findFirst().orElse("no gamma line");
    }

    private void planSettings(Minecraft mc, ClearViewConfig config) {
        then(20, () -> Screens.open(mc, new ClearViewSettingsScreen(null, mc.options)));
        then(10, () -> {
            screenshot(mc, "settings");
            click(mc, find(Screens.current(mc), "clearview.options.pumpkin"));
        });
        then(3, () -> {
            check(!config.hidePumpkin(), "the Hide Pumpkin Overlay button did not switch it off");
            click(mc, find(Screens.current(mc), "clearview.options.fire_immune"));
        });
        then(3, () -> {
            check(!config.hideFireWhenImmune(), "the Hide Harmless Fire button did not switch it off");
            clickRightEdge(mc, slider(Screens.current(mc), "Lower Fire"));
        });
        then(3, () -> {
            check(config.fireLowering() == ClearViewConfig.MAX_LOWERING, "the fire slider did not reach the maximum: "
                    + config.fireLowering());
            scroll(Screens.current(mc), true);
        });
        then(3, () -> click(mc, find(Screens.current(mc), "clearview.options.fullbright")));
        then(3, () -> {
            check(config.fullbright(), "the Fullbright button did not switch it on");
            check(mc.options.gamma().get() == BRIGHT, "the Fullbright button did not raise the brightness");
            click(mc, find(Screens.current(mc), "clearview.options.reset"));
        });
        then(5, () -> {
            Screen screen = Screens.current(mc);
            check(config.hidePumpkin(), "reset did not switch Hide Pumpkin Overlay back on");
            check(config.hideFireWhenImmune(), "reset did not switch Hide Harmless Fire back on");
            check(config.fireLowering() == ClearViewConfig.DEFAULT_FIRE_LOWERING, "reset did not restore Lower Fire");
            check(!config.fullbright(), "reset did not switch Fullbright back off");
            check(mc.options.gamma().get() == userGamma, "reset did not restore the brightness");
            check(count(screen, "Lower Shield") == 1, "reset left " + count(screen, "Lower Shield") + " shield sliders");
            clickRightEdge(mc, slider(screen, "Lower Shield"));
        });
        then(3, () -> {
            check(config.shieldLowering() == ClearViewConfig.MAX_LOWERING,
                    "the shield slider after reset did not change the config");
            Screens.open(mc, null);
        });
        then(5, () -> {
            String saved = read(ClearViewClient.configPath());
            check(saved.contains("\"shieldLowering\": 100"), "settings were not saved on close: " + saved);
        });
        then(10, () -> log("settings checks done"));
    }

    static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    void screenshot(Minecraft mc, String name) {
        log("screenshot: " + name);
        shots.add(name);
        Screenshot.grab(mc.gameDirectory, Screens.renderTarget(mc), message -> {
        });
    }

    private void rememberShots(Minecraft mc) {
        File[] files = new File(mc.gameDirectory, "screenshots").listFiles();
        if (files != null) {
            for (File file : files) {
                existingShots.add(file.getName());
            }
        }
    }

    private void nameShots(Minecraft mc) {
        File dir = new File(mc.gameDirectory, "screenshots");
        File[] files = dir.listFiles((parent, name) -> name.endsWith(".png") && !existingShots.contains(name));
        if (files == null) {
            return;
        }
        List<File> fresh = new ArrayList<>(List.of(files));
        fresh.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        for (int i = 0; i < fresh.size() && i < shots.size(); i++) {
            File target = new File(dir, String.format(Locale.ROOT, "cv-%02d-%s.png", i, shots.get(i)));
            if ((!target.exists() || target.delete()) && fresh.get(i).renameTo(target)) {
                log("saved " + target.getName());
            }
        }
    }

    private static void deleteWorld(Minecraft mc, String name) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(name);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void click(Minecraft mc, AbstractWidget widget) {
        visible(Screens.current(mc), widget);
        Ui.click(Screens.current(mc), widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0);
    }

    private static void clickRightEdge(Minecraft mc, AbstractWidget widget) {
        visible(Screens.current(mc), widget);
        Ui.click(Screens.current(mc), widget.getX() + widget.getWidth() - 2.0, widget.getY() + widget.getHeight() / 2.0);
    }

    private static void scroll(Screen screen, boolean bottom) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractSelectionList<?> list) {
                list.setScrollAmount(bottom ? Double.MAX_VALUE : 0.0);
            }
        }
    }

    private static void visible(Screen screen, AbstractWidget widget) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractSelectionList<?> list) {
                check(Lists.shows(list, widget),
                        widget.getMessage().getString() + " is scrolled out of view");
            }
        }
    }

    static AbstractWidget find(GuiEventListener node, String key) {
        if (node instanceof AbstractWidget widget && labelled(widget, key)) {
            return widget;
        }
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                AbstractWidget found = find(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof Screen) {
            throw new AssertionError("no widget " + key + " on " + node);
        }
        return null;
    }

    private static boolean labelled(AbstractWidget widget, String key) {
        if (!(widget.getMessage().getContents() instanceof TranslatableContents contents)) {
            return false;
        }
        if (contents.getKey().equals(key)) {
            return true;
        }
        Object[] args = contents.getArgs();
        return args.length > 0
                && args[0] instanceof Component name
                && name.getContents() instanceof TranslatableContents inner
                && inner.getKey().equals(key);
    }

    private static AbstractSliderButton slider(GuiEventListener node, String caption) {
        AbstractSliderButton found = findSlider(node, caption);
        check(found != null, "no slider " + caption);
        return found;
    }

    private static AbstractSliderButton findSlider(GuiEventListener node, String caption) {
        if (node instanceof AbstractSliderButton slider && slider.getMessage().getString().startsWith(caption)) {
            return slider;
        }
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                AbstractSliderButton found = findSlider(child, caption);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int count(GuiEventListener node, String caption) {
        int total = node instanceof AbstractSliderButton slider && slider.getMessage().getString().startsWith(caption) ? 1 : 0;
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                total += count(child, caption);
            }
        }
        return total;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void log(String message) {
        System.out.println("[clearview-selftest] " + message);
    }
}
