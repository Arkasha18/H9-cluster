package net.adminrunet.h9cluster;

/**
 * Placement of the speedometer-mode segments inside the logical 960x540
 * settings canvas. The row sits under the skin card and never moves.
 */
public final class SettingsSpeedControl {
    public static final float LABEL_X = 140.0f;
    public static final float LABEL_BASELINE = 289.0f;
    public static final float TOP = 258.0f;
    public static final float BOTTOM = 310.0f;
    public static final float TEXT_BASELINE = 289.0f;
    public static final int SEGMENT_COUNT = 3;
    private static final float FIRST_LEFT = 300.0f;
    private static final float LAST_RIGHT = 850.0f;
    private static final float GAP = 8.0f;
    private static final float SEGMENT_WIDTH =
            (LAST_RIGHT - FIRST_LEFT - GAP * (SEGMENT_COUNT - 1))
                    / SEGMENT_COUNT;

    private SettingsSpeedControl() {
    }

    public static float segmentLeft(int index) {
        return FIRST_LEFT + index * (SEGMENT_WIDTH + GAP);
    }

    public static float segmentRight(int index) {
        return segmentLeft(index) + SEGMENT_WIDTH;
    }

    public static float segmentCenterX(int index) {
        return (segmentLeft(index) + segmentRight(index)) * 0.5f;
    }

    /** Segment index under the point, or -1 when the tap misses the row. */
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
