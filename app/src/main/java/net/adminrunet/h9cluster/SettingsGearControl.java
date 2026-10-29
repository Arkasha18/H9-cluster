package net.adminrunet.h9cluster;

/** Shared gear-number checkbox on the logical 960x540 settings canvas. */
public final class SettingsGearControl {
    public static final float LEFT = 110.0f;
    public static final float RIGHT = 330.0f;
    public static final float TOP = 368.0f;
    public static final float BOTTOM = 416.0f;

    private SettingsGearControl() {
    }

    public static boolean contains(float x, float y) {
        return x >= LEFT && x <= RIGHT && y >= TOP && y <= BOTTOM;
    }
}
