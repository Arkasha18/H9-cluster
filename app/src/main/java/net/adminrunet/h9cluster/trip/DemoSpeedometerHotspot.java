package net.adminrunet.h9cluster.trip;

/** Pure scaling policy for the invisible Demo speedometer action. */
public final class DemoSpeedometerHotspot {
    private static final float REFERENCE_WIDTH = 1_920.0f;
    private static final float REFERENCE_HEIGHT = 720.0f;

    private DemoSpeedometerHotspot() {
    }

    public static boolean contains(
            float x,
            float y,
            int width,
            int height) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        float referenceX = x * REFERENCE_WIDTH / width;
        float referenceY = y * REFERENCE_HEIGHT / height;
        return referenceX >= 40.0f
                && referenceX <= 600.0f
                && referenceY >= 140.0f
                && referenceY <= 650.0f;
    }
}
