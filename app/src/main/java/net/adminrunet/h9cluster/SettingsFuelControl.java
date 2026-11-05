package net.adminrunet.h9cluster;

/** Numeric tank-size editor beside the skin settings button on the 960x540 canvas. */
public final class SettingsFuelControl {
    public static final int INPUT_ID = 0x48594655;
    public static final float LEFT = 300.0f;
    public static final float RIGHT = 560.0f;
    public static final float TOP = 318.0f;
    public static final float BOTTOM = 360.0f;
    public static final float TEXT_BASELINE = 346.0f;

    private SettingsFuelControl() {
    }

    public static float centerX() {
        return (LEFT + RIGHT) * 0.5f;
    }

    public static boolean contains(float x, float y) {
        return x >= LEFT && x <= RIGHT && y >= TOP && y <= BOTTOM;
    }
}
