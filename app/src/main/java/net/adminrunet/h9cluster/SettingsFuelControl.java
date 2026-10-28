package net.adminrunet.h9cluster;

/** Tank selector beside the skin-specific settings button on the 960x540 canvas. */
public final class SettingsFuelControl {
    public static final float TOP = 318.0f;
    public static final float BOTTOM = 360.0f;
    public static final float TEXT_BASELINE = 346.0f;
    public static final int SEGMENT_COUNT = 2;

    private SettingsFuelControl() {
    }

    public static float segmentLeft(int index) {
        return 300.0f + index * 134.0f;
    }

    public static float segmentRight(int index) {
        return segmentLeft(index) + 126.0f;
    }

    public static float segmentCenterX(int index) {
        return segmentLeft(index) + 63.0f;
    }

    public static int segmentAt(float x, float y) {
        if (y < TOP || y > BOTTOM) {
            return -1;
        }
        for (int index = 0; index < SEGMENT_COUNT; index++) {
            if (x >= segmentLeft(index) && x <= segmentRight(index)) {
                return index;
            }
        }
        return -1;
    }
}
