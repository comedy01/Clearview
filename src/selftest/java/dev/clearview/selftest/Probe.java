package dev.clearview.selftest;

import dev.clearview.client.Medium;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public final class Probe {
    private static final Set<String> HIDDEN = new HashSet<>();
    private static final Set<String> SHOWN = new HashSet<>();
    private static final Set<Medium> CLEARED = EnumSet.noneOf(Medium.class);
    private static final Set<Medium> FOGGED = EnumSet.noneOf(Medium.class);

    private static int fireLowered;
    private static int fireHidden;
    private static int shieldLowered;

    private Probe() {
    }

    public static void fire(float drop) {
        if (drop > 0.0F) {
            fireLowered++;
        }
    }

    public static void fireHidden(boolean hidden) {
        if (hidden) {
            fireHidden++;
        }
    }

    public static void shield(float drop) {
        if (drop > 0.0F) {
            shieldLowered++;
        }
    }

    public static void overlay(String texture, boolean hidden) {
        (hidden ? HIDDEN : SHOWN).add(texture);
    }

    public static void fog(Medium medium, boolean cleared) {
        (cleared ? CLEARED : FOGGED).add(medium);
    }

    static void reset() {
        HIDDEN.clear();
        SHOWN.clear();
        CLEARED.clear();
        FOGGED.clear();
        fireLowered = 0;
        fireHidden = 0;
        shieldLowered = 0;
    }

    static int fireLowered() {
        return fireLowered;
    }

    static int fireHidden() {
        return fireHidden;
    }

    static int shieldLowered() {
        return shieldLowered;
    }

    static boolean hidden(String name) {
        return HIDDEN.stream().anyMatch(texture -> texture.contains(name));
    }

    static boolean shown(String name) {
        return SHOWN.stream().anyMatch(texture -> texture.contains(name));
    }

    static boolean cleared(Medium medium) {
        return CLEARED.contains(medium);
    }

    static boolean fogged(Medium medium) {
        return FOGGED.contains(medium);
    }
}
