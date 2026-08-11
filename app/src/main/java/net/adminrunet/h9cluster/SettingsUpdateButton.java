package net.adminrunet.h9cluster;

/** Placement of the manual update button on the shared settings canvas. */
public final class SettingsUpdateButton {
    public static final float LEFT = 640.0f;
    public static final float TOP = 466.0f;
    public static final float RIGHT = 900.0f;
    public static final float BOTTOM = 510.0f;

    private SettingsUpdateButton() {
    }

    public static boolean contains(float x, float y) {
        return x >= LEFT && x <= RIGHT && y >= TOP && y <= BOTTOM;
    }
}
