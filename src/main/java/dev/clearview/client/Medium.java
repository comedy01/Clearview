package dev.clearview.client;

public enum Medium {
    AIR(1.0E6F, 2.0E6F),
    LAVA(0.0F, 48.0F),
    WATER(-8.0F, 192.0F),
    POWDER_SNOW(0.0F, 48.0F);

    private final float start;
    private final float end;

    Medium(float start, float end) {
        this.start = start;
        this.end = end;
    }

    public float start() {
        return start;
    }

    public float end() {
        return end;
    }
}
