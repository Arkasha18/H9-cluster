package net.adminrunet.h9cluster;

/** Placement of the support-log button on the shared settings canvas. */
public final class SettingsLogButton {
    public static final float LEFT = 60.0f;
    public static final float TOP = 466.0f;
    public static final float RIGHT = 320.0f;
    public static final float BOTTOM = 510.0f;

    private SettingsLogButton() {
    }

    public static boolean contains(float x, float y) {
        return x >= LEFT && x <= RIGHT && y >= TOP && y <= BOTTOM;
    }
}
