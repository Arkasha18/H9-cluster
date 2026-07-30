package net.adminrunet.h9cluster.skins.sport;

/**
 * Calibrated paths for the two asymmetric main scales in the Sport artwork.
 *
 * <p>The RPM scale is not a mirror of the speed scale: its zero starts farther
 * along the lower arm and its maximum extends farther along the upper arm.
 * Keeping independent measured control points prevents the RPM needle from
 * stopping around the printed 7 at 8000 rpm.</p>
 */
final class SportDialCalibration {
    private static final float[] SPEED_VALUES_KPH = {
            0.0f, 20.0f, 40.0f, 60.0f, 80.0f,
            100.0f, 120.0f, 140.0f, 180.0f, 220.0f
    };
    private static final float[] SPEED_X = {
            444.0f, 322.0f, 245.0f, 184.0f, 130.0f,
            96.0f, 118.0f, 202.0f, 360.0f, 526.0f
    };
    private static final float[] SPEED_Y = {
            660.0f, 660.0f, 631.0f, 577.0f, 498.0f,
            405.0f, 313.0f, 231.0f, 156.0f, 154.0f
    };

    private static final float[] RPM_VALUES = {
            0.0f, 1000.0f, 2000.0f, 3000.0f, 4000.0f,
            5000.0f, 6000.0f, 7000.0f, 8000.0f
    };
    private static final float[] RPM_X = {
            1598.0f, 1697.0f, 1790.0f, 1824.0f, 1802.0f,
            1718.0f, 1560.0f, 1394.0f, 1266.0f
    };
    private static final float[] RPM_Y = {
            660.0f, 610.0f, 498.0f, 405.0f, 313.0f,
            231.0f, 156.0f, 154.0f, 154.0f
    };

    private SportDialCalibration() {
    }

    static Sample speed(float speedKph) {
        return interpolate(speedKph, SPEED_VALUES_KPH, SPEED_X, SPEED_Y);
    }

    static Sample rpm(float rpm) {
        return interpolate(rpm, RPM_VALUES, RPM_X, RPM_Y);
    }

    private static Sample interpolate(
            float value,
            float[] values,
            float[] xCoordinates,
            float[] yCoordinates) {
        float clampedValue = Float.isFinite(value)
                ? clamp(value, values[0], values[values.length - 1])
                : values[0];
        int lastPoint = values.length - 1;
        int segment = lastPoint - 1;
        for (int index = 0; index < lastPoint; index++) {
            if (clampedValue <= values[index + 1]) {
                segment = index;
                break;
            }
        }

        float valueStart = values[segment];
        float valueEnd = values[segment + 1];
        float valueSpan = valueEnd - valueStart;
        float t = valueSpan > 0.0f
                ? (clampedValue - valueStart) / valueSpan
                : 0.0f;

        float startSlopeX = slope(values, xCoordinates, segment);
        float startSlopeY = slope(values, yCoordinates, segment);
        float endSlopeX = slope(values, xCoordinates, segment + 1);
        float endSlopeY = slope(values, yCoordinates, segment + 1);

        return new Sample(
                hermitePosition(
                        xCoordinates[segment],
                        xCoordinates[segment + 1],
                        startSlopeX,
                        endSlopeX,
                        valueSpan,
                        t),
                hermitePosition(
                        yCoordinates[segment],
                        yCoordinates[segment + 1],
                        startSlopeY,
                        endSlopeY,
                        valueSpan,
                        t),
                hermiteTangent(
                        xCoordinates[segment],
                        xCoordinates[segment + 1],
                        startSlopeX,
                        endSlopeX,
                        valueSpan,
                        t),
                hermiteTangent(
                        yCoordinates[segment],
                        yCoordinates[segment + 1],
                        startSlopeY,
                        endSlopeY,
                        valueSpan,
                        t));
    }

    private static float slope(float[] values, float[] coordinates, int index) {
        int lastPoint = values.length - 1;
        if (index <= 0) {
            return (coordinates[1] - coordinates[0])
                    / (values[1] - values[0]);
        }
        if (index >= lastPoint) {
            return (coordinates[lastPoint] - coordinates[lastPoint - 1])
                    / (values[lastPoint] - values[lastPoint - 1]);
        }

        float previousSpan = values[index] - values[index - 1];
        float nextSpan = values[index + 1] - values[index];
        float previousDelta =
                (coordinates[index] - coordinates[index - 1]) / previousSpan;
        float nextDelta =
                (coordinates[index + 1] - coordinates[index]) / nextSpan;
        if (previousDelta == 0.0f
                || nextDelta == 0.0f
                || Math.signum(previousDelta) != Math.signum(nextDelta)) {
            return 0.0f;
        }

        // Weighted harmonic mean from the monotone cubic Hermite method.
        // It prevents a flat or turning scale segment from bowing past its
        // measured endpoints while keeping the tangent continuous.
        float previousWeight = 2.0f * nextSpan + previousSpan;
        float nextWeight = nextSpan + 2.0f * previousSpan;
        return (previousWeight + nextWeight)
                / (previousWeight / previousDelta + nextWeight / nextDelta);
    }

    private static float hermitePosition(
            float start,
            float end,
            float startSlope,
            float endSlope,
            float valueSpan,
            float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return (2.0f * t3 - 3.0f * t2 + 1.0f) * start
                + (t3 - 2.0f * t2 + t) * valueSpan * startSlope
                + (-2.0f * t3 + 3.0f * t2) * end
                + (t3 - t2) * valueSpan * endSlope;
    }

    private static float hermiteTangent(
            float start,
            float end,
            float startSlope,
            float endSlope,
            float valueSpan,
            float t) {
        float t2 = t * t;
        return (6.0f * t2 - 6.0f * t) * start
                + (3.0f * t2 - 4.0f * t + 1.0f) * valueSpan * startSlope
                + (-6.0f * t2 + 6.0f * t) * end
                + (3.0f * t2 - 2.0f * t) * valueSpan * endSlope;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static final class Sample {
        final float x;
        final float y;
        final float tangentX;
        final float tangentY;

        Sample(float x, float y, float tangentX, float tangentY) {
            this.x = x;
            this.y = y;
            this.tangentX = tangentX;
            this.tangentY = tangentY;
        }
    }
}
