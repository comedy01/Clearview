package dev.clearview.showcase;

import com.mojang.datafixers.util.Pair;
import dev.clearview.client.ClearViewClient;
import dev.clearview.client.ClearViewRuntime;
import dev.clearview.config.ClearViewConfig;
import dev.clearview.showcase.mixin.GameRendererAccessor;
import dev.clearview.showcase.mixin.LightmapExtractorAccessor;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class Showcase {
    private static final String WORLD = "clearview-showcase";
    private static final long SETTLE_NANOS = 3_000_000_000L;
    private static Showcase instance;

    private final Path out;
    private final Recorder vanilla;
    private final Recorder clear;
    private final long seed;
    private final List<Action> actions = new ArrayList<>();
    private volatile Spots spots;
    private boolean started;
    private boolean finished;
    private boolean pairing;
    private int idle;
    private int index;
    private int frame;
    private int stable;
    private long actionStart;

    private interface Action {
        boolean run(int frame);
    }

    private record Spot(ResourceKey<Level> dimension, Vec3 feet, double yaw, double pitch) {
    }

    private record Spots(Spot peak, Spot meadow, Spot snow, Spot reef, Spot cave, Spot lava, Spot shore) {
    }

    private Showcase(Path out, String ffmpeg, long seed) {
        this.out = out;
        this.vanilla = new Recorder(ffmpeg);
        this.clear = new Recorder(ffmpeg);
        this.seed = seed;
    }

    public static void start() {
        String ffmpeg = System.getProperty("clearview.ffmpeg", "ffmpeg");
        long seed = Long.parseLong(System.getProperty("clearview.seed", "8675309"));
        instance = new Showcase(Path.of(System.getProperty("clearview.showcase")), ffmpeg, seed);
    }

    public static void frame() {
        if (instance != null) {
            instance.onFrame();
        }
    }

    private void onFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 120) {
                started = true;
                setUpOptions(mc);
                plan(mc);
                log("creating world with seed " + seed);
                deleteWorld(mc);
                Worlds.create(mc, WORLD, false, seed);
            }
            return;
        }
        if (!pairing) {
            Clock.step();
        }
        mc.gui.toastManager().clear();
        try {
            while (index < actions.size()) {
                if (frame == 0) {
                    actionStart = System.nanoTime();
                }
                if (!actions.get(index).run(frame++)) {
                    return;
                }
                index++;
                frame = 0;
            }
        } catch (Throwable e) {
            log("FAILED: " + e);
            e.printStackTrace();
        }
        finish(mc);
    }

    private void finish(Minecraft mc) {
        finished = true;
        pairing = false;
        Clock.release();
        mc.options.keyUse.setDown(false);
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(90_000L);
            } catch (InterruptedException e) {
                return;
            }
            log("the game did not close, forcing it");
            Runtime.getRuntime().halt(1);
        }, "clearview-showcase-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        ClearViewClient.config().resetToDefaults();
        ClearViewRuntime.applyBrightness();
        ClearViewClient.saveConfig();
        log("showcase done");
        mc.stop();
    }

    private static void setUpOptions(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
        mc.options.renderDistance().set(12);
        mc.options.bobView().set(false);
        mc.options.damageTiltStrength().set(0.0);
        mc.options.gamma().set(0.5);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.options.guiScale().get() != 3) {
            mc.options.guiScale().set(3);
            mc.resizeGui();
        }
    }

    private void once(Runnable action) {
        actions.add(f -> {
            action.run();
            return true;
        });
    }

    private void until(BooleanSupplier ready) {
        actions.add(f -> ready.getAsBoolean());
    }

    private void holdFor(int frames, Runnable hold) {
        actions.add(f -> {
            hold.run();
            return f >= frames;
        });
    }

    private void settle(Minecraft mc, Runnable hold) {
        actions.add(f -> {
            hold.run();
            stable = mc.levelRenderer.hasRenderedAllSections() ? stable + 1 : 0;
            return f >= 150 && System.nanoTime() - actionStart > SETTLE_NANOS
                    && (stable > 45 || System.nanoTime() - actionStart > 20 * SETTLE_NANOS);
        });
    }

    private void record(String name, int frames, Consumer<ClearViewConfig> look, IntConsumer script) {
        ClearViewConfig config = ClearViewClient.config();
        Path clips = out.resolve("clips");
        Path stills = out.resolve("stills");
        int stillAt = frames * 2 / 3;
        actions.add(f -> {
            if (f == 0) {
                log("recording " + name);
                pairing = true;
                vanilla.start(clips.resolve(name + "-vanilla.mp4"));
                clear.start(clips.resolve(name + "-clear.mp4"));
            } else if (f % 2 == 1) {
                vanilla.capture(f / 2 == stillAt ? stills.resolve(name + "-vanilla.png") : null);
            } else {
                clear.capture((f - 1) / 2 == stillAt ? stills.resolve(name + "-clear.png") : null);
            }
            if (f >= frames * 2) {
                pairing = false;
                return true;
            }
            if (f % 2 == 0) {
                Clock.step();
                script.accept(f / 2);
                vanillaLook(config);
            } else {
                config.resetToDefaults();
                look.accept(config);
            }
            ClearViewRuntime.applyBrightness();
            refreshLightmap(mc());
            return false;
        });
        until(() -> vanilla.drained() && clear.drained());
        once(() -> {
            vanilla.stop();
            clear.stop();
            config.resetToDefaults();
            ClearViewRuntime.applyBrightness();
        });
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static void vanillaLook(ClearViewConfig config) {
        config.setFireLowering(0);
        config.setHideFireWhenImmune(false);
        config.setShieldLowering(0);
        config.setHidePumpkin(false);
        config.setHideFrost(false);
        config.setClearAir(false);
        config.setClearLava(false);
        config.setClearWater(false);
        config.setClearPowderSnow(false);
        config.setFullbright(false);
    }

    private static void refreshLightmap(Minecraft mc) {
        ((LightmapExtractorAccessor) ((GameRendererAccessor) mc.gameRenderer).clearviewShowcase$lightmap())
                .clearviewShowcase$setNeedsUpdate(true);
    }

    static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void cmd(Minecraft mc, String format, Object... args) {
        run(mc, String.format(Locale.ROOT, format, args));
    }

    private static void place(Minecraft mc, Vec3 feet, double yaw, double pitch) {
        LocalPlayer player = mc.player;
        player.setPos(feet.x, feet.y, feet.z);
        player.xo = feet.x;
        player.yo = feet.y;
        player.zo = feet.z;
        player.xOld = feet.x;
        player.yOld = feet.y;
        player.zOld = feet.z;
        player.setDeltaMovement(Vec3.ZERO);
        player.setYRot((float) yaw);
        player.setXRot((float) pitch);
        player.yRotO = (float) yaw;
        player.xRotO = (float) pitch;
        player.setYHeadRot((float) yaw);
        player.yHeadRotO = (float) yaw;
    }

    private static boolean wanted(String scene) {
        String only = System.getProperty("clearview.scenes");
        return only == null || List.of(only.split(",")).contains(scene);
    }

    private static String dim(ResourceKey<Level> dimension) {
        return dimension == Level.NETHER ? "minecraft:the_nether" : "minecraft:overworld";
    }

    private void plan(Minecraft mc) {
        until(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null);
        once(() -> {
            log("world loaded");
            Clock.fix();
            ClearViewClient.config().resetToDefaults();
            run(mc, "time set 6000");
            run(mc, "weather clear 1000000");
            run(mc, "gamerule advance_time false");
            run(mc, "gamerule spawn_mobs false");
            run(mc, "gamerule random_tick_speed 0");
            run(mc, "difficulty easy");
            run(mc, "gamemode survival @p");
            run(mc, "effect give @p minecraft:resistance infinite 4 true");
            run(mc, "effect give @p minecraft:saturation infinite 0 true");
            run(mc, "effect give @p minecraft:water_breathing infinite 0 true");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                try {
                    spots = locate(server);
                } catch (Throwable e) {
                    log("FAILED to find the sets: " + e);
                    e.printStackTrace();
                }
            });
        });
        until(() -> spots != null);
        once(() -> film(mc));
    }

    private void scene(Minecraft mc, Spot spot, Runnable setup, Runnable hold) {
        once(() -> {
            mc.options.keyUse.setDown(false);
            run(mc, "clear @p");
            run(mc, "effect clear @p minecraft:fire_resistance");
            burn(mc, 0);
            String[] items = {"diamond_pickaxe", "diamond_sword", "bow", "cobblestone 64", "torch 32", "bread 12",
                    "water_bucket", "oak_log 32"};
            for (int i = 0; i < items.length; i++) {
                cmd(mc, "item replace entity @p hotbar.%d with minecraft:%s", i, items[i]);
            }
            mc.player.getInventory().setSelectedSlot(0);
            cmd(mc, "execute in %s run tp @p %.3f %.3f %.3f %.1f %.1f", dim(spot.dimension()), spot.feet().x,
                    spot.feet().y, spot.feet().z, spot.yaw(), spot.pitch());
        });
        holdFor(40, () -> {
        });
        once(setup);
        settle(mc, hold);
    }

    private void film(Minecraft mc) {
        Spots s = spots;
        log("peak " + s.peak() + ", meadow " + s.meadow() + ", reef " + s.reef() + ", cave " + s.cave()
                + ", lava " + s.lava() + ", shore " + s.shore());

        if (wanted("fog")) {
            Spot spot = s.peak();
            Runnable hold = () -> place(mc, spot.feet(), spot.yaw() - 12, spot.pitch());
            scene(mc, spot, () -> {
            }, hold);
            record("10-fog", 330, c -> c.setClearAir(true), f -> place(mc, spot.feet(),
                    spot.yaw() - 12 + 24 * smooth(f / 330.0), spot.pitch()));
        }

        if (wanted("snow")) {
            Spot spot = s.snow();
            BlockPos feet = BlockPos.containing(spot.feet());
            int x = feet.getX();
            int y = feet.getY();
            int z = feet.getZ();
            Runnable hold = () -> place(mc, spot.feet(), -8, 4);
            scene(mc, spot, () -> {
                cmd(mc, "fill %d %d %d %d %d %d minecraft:air", x - 9, y, z - 4, x + 9, y + 8, z + 28);
                cmd(mc, "fill %d %d %d %d %d %d minecraft:snow_block", x - 9, y - 1, z - 4, x + 9, y - 1, z + 28);
                cmd(mc, "fill %d %d %d %d %d %d minecraft:powder_snow", x - 7, y, z - 2, x + 7, y + 3, z + 24);
                cmd(mc, "setblock %d %d %d minecraft:gold_block", x + 2, y, z + 9);
                cmd(mc, "setblock %d %d %d minecraft:diamond_block", x - 3, y, z + 13);
                cmd(mc, "setblock %d %d %d minecraft:emerald_block", x + 4, y + 1, z + 17);
                cmd(mc, "setblock %d %d %d minecraft:lantern", x - 1, y, z + 6);
                cmd(mc, "setblock %d %d %d minecraft:spruce_log", x - 5, y, z + 20);
                cmd(mc, "setblock %d %d %d minecraft:spruce_log", x - 5, y + 1, z + 20);
                cmd(mc, "setblock %d %d %d minecraft:spruce_log", x - 5, y + 2, z + 20);
                cmd(mc, "summon minecraft:snow_golem %d.5 %d %d.5 {NoAI:1b,Silent:1b,Rotation:[180f,0f]}", x + 1, y, z + 5);
                freeze(mc);
            }, () -> {
                hold.run();
                freeze(mc);
            });
            record("03-snow", 270, c -> {
            }, f -> {
                place(mc, spot.feet(), -8 + 16 * smooth(f / 270.0), 4);
                if (f % 20 == 0) {
                    freeze(mc);
                }
            });
            once(() -> {
                run(mc, "kill @e[type=minecraft:snow_golem]");
                cmd(mc, "fill %d %d %d %d %d %d minecraft:air", x - 7, y, z - 2, x + 7, y + 3, z + 24);
            });
        }

        Spot meadow = s.meadow();
        BlockPos stand = BlockPos.containing(meadow.feet());
        if (wanted("fire")) {
            Runnable hold = () -> place(mc, meadow.feet(), meadow.yaw() + 8, 4);
            scene(mc, meadow, () -> burn(mc, 400), () -> {
                hold.run();
                burn(mc, 400);
            });
            record("04-fire", 270, c -> c.setFireLowering(ClearViewConfig.MAX_LOWERING), f -> {
                place(mc, meadow.feet(), meadow.yaw() + 8 - 16 * smooth(f / 270.0), 4);
                if (f % 30 == 0) {
                    burn(mc, 400);
                }
            });
            once(() -> burn(mc, 0));
        }

        if (wanted("harmless")) {
            Runnable hold = () -> place(mc, meadow.feet(), meadow.yaw() - 8, 4);
            scene(mc, meadow, () -> {
                burn(mc, 400);
                run(mc, "effect give @p minecraft:fire_resistance infinite 0 true");
            }, () -> {
                hold.run();
                burn(mc, 400);
            });
            record("05-harmless", 240, c -> {
            }, f -> {
                place(mc, meadow.feet(), meadow.yaw() - 8 + 12 * smooth(f / 240.0), 4);
                if (f % 30 == 0) {
                    burn(mc, 400);
                }
            });
            once(() -> {
                run(mc, "effect clear @p minecraft:fire_resistance");
                burn(mc, 0);
            });
        }

        if (wanted("shield")) {
            Runnable hold = () -> place(mc, meadow.feet(), meadow.yaw() + 25, 2);
            scene(mc, meadow, () -> {
                run(mc, "item replace entity @p hotbar.8 with minecraft:air");
                run(mc, "item replace entity @p weapon.offhand with minecraft:shield");
                mc.player.getInventory().setSelectedSlot(8);
            }, hold);
            record("06-shield", 300, c -> c.setShieldLowering(ClearViewConfig.MAX_LOWERING), f -> {
                place(mc, meadow.feet(), meadow.yaw() + 25 - 20 * smooth(f / 300.0), 2);
                mc.options.keyUse.setDown(f >= 100);
            });
            once(() -> {
                mc.options.keyUse.setDown(false);
                run(mc, "item replace entity @p weapon.offhand with minecraft:air");
            });
        }

        if (wanted("pumpkin")) {
            double yaw = meadow.yaw();
            double rad = Math.toRadians(yaw);
            Vec3 at = meadow.feet().add(-Math.sin(rad) * 7, 0, Math.cos(rad) * 7);
            Runnable hold = () -> place(mc, meadow.feet(), yaw, 0);
            scene(mc, meadow, () -> {
                run(mc, "item replace entity @p armor.head with minecraft:carved_pumpkin");
            }, hold);
            record("07-pumpkin", 240, c -> {
            }, f -> place(mc, meadow.feet(), yaw + 6 - 12 * smooth(f / 240.0), 0));
            once(() -> {
                run(mc, "item replace entity @p armor.head with minecraft:air");
                run(mc, "kill @e[type=minecraft:enderman]");
            });
        }

        if (wanted("water")) {
            Spot spot = s.reef();
            Runnable hold = () -> place(mc, spot.feet(), spot.yaw(), spot.pitch());
            scene(mc, spot, () -> {
            }, hold);
            record("02-water", 300, c -> {
            }, f -> place(mc, spot.feet(), spot.yaw() - 15 + 30 * smooth(f / 300.0), spot.pitch()));
        }

        if (wanted("cave")) {
            Spot spot = s.cave();
            Runnable hold = () -> place(mc, spot.feet(), spot.yaw() - 15, spot.pitch());
            scene(mc, spot, () -> {
            }, hold);
            record("08-cave", 300, c -> c.setFullbright(true), f -> place(mc, spot.feet(),
                    spot.yaw() - 15 + 30 * smooth(f / 300.0), spot.pitch()));
        }

        if (wanted("lava")) {
            Spot spot = s.lava();
            Runnable hold = () -> place(mc, spot.feet(), spot.yaw() - 10, spot.pitch());
            scene(mc, spot, () -> run(mc, "effect give @p minecraft:fire_resistance infinite 0 true"), hold);
            record("01-lava", 330, c -> {
            }, f -> place(mc, spot.feet(), spot.yaw() - 10 + 20 * smooth(f / 330.0), spot.pitch()));
        }

        if (wanted("nether")) {
            Spot spot = s.shore();
            Runnable hold = () -> place(mc, spot.feet(), spot.yaw() + 10, spot.pitch());
            scene(mc, spot, () -> {
            }, hold);
            record("09-nether", 270, c -> c.setClearAir(true), f -> place(mc, spot.feet(),
                    spot.yaw() + 10 - 20 * smooth(f / 270.0), spot.pitch()));
        }
    }

    private static void burn(Minecraft mc, int ticks) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getPlayerList().getPlayers().forEach(p -> p.setRemainingFireTicks(ticks)));
    }

    private static void freeze(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getPlayerList().getPlayers().forEach(p -> p.setTicksFrozen(p.getTicksRequiredToFreeze() + 2)));
    }

    private static Spots locate(IntegratedServer server) {
        ServerLevel world = server.overworld();
        ServerLevel nether = server.getLevel(Level.NETHER);
        Spot peak = peak(world);
        log("found peak " + peak);
        Spot meadow = open(world, h -> h.is(Biomes.CHERRY_GROVE), Blocks.GRASS_BLOCK);
        log("found meadow " + meadow);
        Spot snow = open(world, h -> h.is(Biomes.SNOWY_PLAINS), Blocks.SNOW_BLOCK);
        log("found snow " + snow);
        Spot reef = reef(world);
        log("found reef " + reef);
        Spot cave = cave(world);
        log("found cave " + cave);
        Spot[] lava = lavaSea(nether);
        log("found lava " + lava[0] + " and shore " + lava[1]);
        return new Spots(peak, meadow, snow, reef, cave, lava[0], lava[1]);
    }

    private static BlockPos biome(ServerLevel level, Predicate<Holder<Biome>> wanted, BlockPos from) {
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(wanted, from, 6400, 32, 64);
        return found == null ? from : found.getFirst();
    }

    private static int surface(ServerLevel level, int x, int z) {
        return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
    }

    private static int top(ServerLevel level, int x, int z) {
        return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1;
    }

    private static double yawOf(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static Spot peak(ServerLevel level) {
        BlockPos found = biome(level, h -> h.is(Biomes.JAGGED_PEAKS) || h.is(Biomes.FROZEN_PEAKS), BlockPos.ZERO);
        int bx = found.getX();
        int bz = found.getZ();
        int best = Integer.MIN_VALUE;
        for (int dx = -48; dx <= 48; dx += 4) {
            for (int dz = -48; dz <= 48; dz += 4) {
                int h = surface(level, found.getX() + dx, found.getZ() + dz);
                if (h > best) {
                    best = h;
                    bx = found.getX() + dx;
                    bz = found.getZ() + dz;
                }
            }
        }
        double yaw = 0;
        int lowest = Integer.MAX_VALUE;
        for (int a = 0; a < 360; a += 15) {
            double rad = Math.toRadians(a);
            int sum = 0;
            for (int r = 24; r <= 96; r += 24) {
                sum += surface(level, bx + (int) (-Math.sin(rad) * r), bz + (int) (Math.cos(rad) * r));
            }
            if (sum < lowest) {
                lowest = sum;
                yaw = a;
            }
        }
        return new Spot(Level.OVERWORLD, new Vec3(bx + 0.5, best, bz + 0.5), yaw, 24);
    }

    private static Spot open(ServerLevel level, Predicate<Holder<Biome>> wanted, net.minecraft.world.level.block.Block floor) {
        BlockPos found = biome(level, wanted, BlockPos.ZERO);
        Spot best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int dx = -32; dx <= 32; dx += 8) {
            for (int dz = -32; dz <= 32; dz += 8) {
                int x = found.getX() + dx;
                int z = found.getZ() + dz;
                int ground = surface(level, x, z);
                BlockState below = level.getBlockState(new BlockPos(x, ground - 1, z));
                if (top(level, x, z) != ground || !(below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.SNOW_BLOCK)
                        || below.is(Blocks.SNOW) || below.is(floor))) {
                    continue;
                }
                for (int a = 0; a < 360; a += 30) {
                    double rad = Math.toRadians(a);
                    int score = 0;
                    for (int r = 3; r <= 30; r += 3) {
                        int sx = x + (int) Math.round(-Math.sin(rad) * r);
                        int sz = z + (int) Math.round(Math.cos(rad) * r);
                        int h = top(level, sx, sz);
                        if (h <= ground + 1) {
                            score += 3;
                        } else if (r > 9 && h <= ground + 12) {
                            score += 2;
                        }
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        best = new Spot(Level.OVERWORLD, new Vec3(x + 0.5, ground, z + 0.5), a, 4);
                    }
                }
            }
        }
        return best != null ? best : new Spot(Level.OVERWORLD,
                new Vec3(found.getX() + 0.5, surface(level, found.getX(), found.getZ()), found.getZ() + 0.5), 0, 4);
    }

    private static Spot reef(ServerLevel level) {
        BlockPos found = biome(level, h -> h.is(Biomes.WARM_OCEAN), new BlockPos(0, 63, 0));
        int sea = level.getSeaLevel();
        Spot best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int dx = -48; dx <= 48; dx += 8) {
            for (int dz = -48; dz <= 48; dz += 8) {
                int x = found.getX() + dx;
                int z = found.getZ() + dz;
                int floor = level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15) + 1;
                int depth = sea - floor;
                if (depth < 7 || depth > 16) {
                    continue;
                }
                for (int a = 0; a < 360; a += 30) {
                    double rad = Math.toRadians(a);
                    int score = -Math.abs(depth - 10);
                    for (int r = 4; r <= 40; r += 4) {
                        int sx = x + (int) Math.round(-Math.sin(rad) * r);
                        int sz = z + (int) Math.round(Math.cos(rad) * r);
                        int f = level.getChunk(sx >> 4, sz >> 4).getHeight(Heightmap.Types.OCEAN_FLOOR, sx & 15, sz & 15) + 1;
                        if (f <= floor + 3 && f >= floor - 6) {
                            score += 2;
                        }
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        best = new Spot(Level.OVERWORLD, new Vec3(x + 0.5, floor + 2.5, z + 0.5), a, 8);
                    }
                }
            }
        }
        return best != null ? best : new Spot(Level.OVERWORLD, new Vec3(found.getX() + 0.5, sea - 6, found.getZ() + 0.5), 0, 8);
    }

    private static Spot cave(ServerLevel level) {
        BlockPos found = biome(level, h -> h.is(Biomes.DRIPSTONE_CAVES), new BlockPos(0, 0, 0));
        Spot best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int dx = -24; dx <= 24; dx += 4) {
            for (int dz = -24; dz <= 24; dz += 4) {
                for (int dy = -24; dy <= 24; dy += 2) {
                    BlockPos p = found.offset(dx, dy, dz);
                    if (p.getY() > 40 || !level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()
                            || !level.getBlockState(p.below()).isSolid()
                            || level.getBrightness(LightLayer.SKY, p) > 0) {
                        continue;
                    }
                    int open = 0;
                    for (int ox = -9; ox <= 9; ox += 3) {
                        for (int oy = -3; oy <= 9; oy += 3) {
                            for (int oz = -9; oz <= 9; oz += 3) {
                                if (level.getBlockState(p.offset(ox, oy, oz)).isAir()) {
                                    open++;
                                }
                            }
                        }
                    }
                    if (open + 40 > bestScore) {
                        double yaw = 0;
                        int longest = -1;
                        for (int a = 0; a < 360; a += 15) {
                            double rad = Math.toRadians(a);
                            int r = 1;
                            while (r < 40 && level.getBlockState(BlockPos.containing(p.getX() + 0.5 - Math.sin(rad) * r,
                                    p.getY() + 1.6, p.getZ() + 0.5 + Math.cos(rad) * r)).isAir()) {
                                r++;
                            }
                            if (r > longest) {
                                longest = r;
                                yaw = a;
                            }
                        }
                        int score = open + longest * 3;
                        if (score > bestScore) {
                            bestScore = score;
                            best = new Spot(Level.OVERWORLD, Vec3.atBottomCenterOf(p), yaw, 6);
                        }
                    }
                }
            }
        }
        return best != null ? best : new Spot(Level.OVERWORLD, Vec3.atBottomCenterOf(found), 0, 6);
    }

    private static boolean lava(BlockState state) {
        return state.is(Blocks.LAVA);
    }

    private static Spot[] lavaSea(ServerLevel level) {
        Spot bestLava = null;
        Spot bestShore = null;
        int bestScore = Integer.MAX_VALUE;
        for (int dx = -160; dx <= 160; dx += 16) {
            for (int dz = -160; dz <= 160; dz += 16) {
                BlockPos p = new BlockPos(dx, 30, dz);
                if (!lava(level.getBlockState(p)) || !lava(level.getBlockState(p.below(4)))) {
                    continue;
                }
                for (int a = 0; a < 360; a += 30) {
                    double rad = Math.toRadians(a);
                    int hit = -1;
                    for (int r = 2; r <= 48; r++) {
                        BlockPos q = BlockPos.containing(dx + 0.5 - Math.sin(rad) * r, 30, dz + 0.5 + Math.cos(rad) * r);
                        BlockState state = level.getBlockState(q);
                        if (!lava(state) && !state.isAir()) {
                            hit = r;
                            break;
                        }
                    }
                    if (hit < 14 || hit > 36) {
                        continue;
                    }
                    BlockPos shore = BlockPos.containing(dx + 0.5 - Math.sin(rad) * (hit + 1), 31,
                            dz + 0.5 + Math.cos(rad) * (hit + 1));
                    int y = -1;
                    for (int sy = 31; sy < 70; sy++) {
                        BlockPos q = new BlockPos(shore.getX(), sy, shore.getZ());
                        if (level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir()
                                && level.getBlockState(q.above(2)).isAir() && level.getBlockState(q.below()).isSolid()) {
                            y = sy;
                            break;
                        }
                    }
                    if (y < 0 || y > 45) {
                        continue;
                    }
                    int score = Math.abs(hit - 24) + Math.abs(y - 34);
                    if (score < bestScore) {
                        bestScore = score;
                        bestLava = new Spot(Level.NETHER, new Vec3(dx + 0.5, 28.4, dz + 0.5), a, 0);
                        bestShore = new Spot(Level.NETHER, new Vec3(shore.getX() + 0.5, y, shore.getZ() + 0.5),
                                a + 180, 10);
                    }
                }
            }
        }
        if (bestLava == null) {
            bestLava = new Spot(Level.NETHER, new Vec3(0.5, 28.4, 0.5), 0, 0);
            bestShore = new Spot(Level.NETHER, new Vec3(0.5, 40, 0.5), 0, 10);
        }
        return new Spot[] {bestLava, bestShore};
    }

    private static void deleteWorld(Minecraft mc) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
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

    static void log(String message) {
        System.out.println("[clearview-showcase] " + message);
    }
}
