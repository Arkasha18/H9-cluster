package net.adminrunet.h9cluster.skins.classic;

/**
 * Maps live values to the polar rays of the major ticks baked into the Classic skin.
 *
 * <p>The dial artwork is elliptical and its upper part is compressed, so a linear
 * value-to-angle sweep drifts between the printed values. Interpolating between the
 * measured major ticks keeps the needle aligned over the complete scale.</p>
 */
final class ClassicDialCalibration {
    private static final float[] SPEED_VALUES_KPH = {
            0.0f, 20.0f, 40.0f, 60.0f, 80.0f, 100.0f,
            120.0f, 140.0f, 160.0f, 180.0f, 200.0f, 220.0f
    };
    private static final float[] SPEED_ANGLES_DEG = {
            90.0f, 108.8f, 127.7f, 145.5f, 163.4f, 181.4f,
            201.2f, 221.6f, 245.6f, 270.4f, 292.4f, 304.6f
    };
    private static final float[] RPM_VALUES = {
            0.0f, 1000.0f, 2000.0f, 3000.0f, 4000.0f,
            5000.0f, 6000.0f, 7000.0f, 8000.0f
    };
    private static final float[] RPM_ANGLES_DEG = {
            90.0f, 48.9f, 26.4f, 1.7f, -20.9f,
            -44.4f, -71.5f, -98.0f, -126.4f
    };

    private ClassicDialCalibration() {
    }

    static float speedAngleDeg(float speedKph) {
        return interpolate(speedKph, SPEED_VALUES_KPH, SPEED_ANGLES_DEG);
    }

    static float rpmAngleDeg(float rpm) {
        return interpolate(rpm, RPM_VALUES, RPM_ANGLES_DEG);
    }

    private static float interpolate(float value, float[] values, float[] angles) {
        if (!Float.isFinite(value) || value <= values[0]) {
            return angles[0];
        }

        int lastIndex = values.length - 1;
        if (value >= values[lastIndex]) {
            return angles[lastIndex];
        }

        for (int index = 0; index < lastIndex; index++) {
            float segmentEnd = values[index + 1];
            if (value <= segmentEnd) {
                float segmentStart = values[index];
                float fraction = (value - segmentStart) / (segmentEnd - segmentStart);
                return angles[index]
                        + (angles[index + 1] - angles[index]) * fraction;
            }
        }
        return angles[lastIndex];
    }
}
