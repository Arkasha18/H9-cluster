package net.adminrunet.h9cluster;

/** Deterministic automatic driving loop used by the emulator-only demo build. */
final class DemoScenario {
    static final long CYCLE_MS = 30_000L;

    private static final float[] SPEED_KEYFRAME_SECONDS = new float[] {
            0.0f,
            3.0f,
            8.0f,
            13.0f,
            18.0f,
            24.0f,
            27.0f,
            30.0f
    };
    private static final float[] SPEED_KEYFRAME_KPH = new float[] {
            0.0f,
            35.0f,
            75.0f,
            95.0f,
            95.0f,
            20.0f,
            0.0f,
            0.0f
    };

    ClusterState snapshot(long elapsedMs, long nowMs) {
        long cycleTimeMs = Math.floorMod(elapsedMs, CYCLE_MS);
        float seconds = cycleTimeMs / 1_000.0f;
        float speed = speedAt(seconds);
        float steering = 150.0f * sin(
                (float) (seconds * Math.PI * 2.0 / 12.0));
        int gear = gearFor(speed, seconds);
        int rpm = clamp(
                Math.round(750.0f + speed * 27.0f
                        + 180.0f * sin(
                                (float) (seconds * Math.PI / 2.5))),
                700,
                4_500);
        float turnDelta = steering / 100.0f;
        double distanceKm = Math.max(0L, elapsedMs)
                * 65.0 / 3_600_000.0;

        return new ClusterState(
                Math.round(speed),
                rpm,
                gear,
                Math.round(82.0f + 6.0f * seconds / 30.0f),
                68.0f + 12.0f * seconds / 30.0f,
                58.0f - 0.02f * seconds,
                620 - Math.round(seconds * 0.3f),
                28_642.0 + distanceKm,
                42.3f + (float) distanceKm,
                167.8f + (float) distanceKm,
                2.35f + 0.02f * sin(seconds / 3.0f),
                2.37f + 0.02f * sin(seconds / 3.0f + 0.5f),
                2.42f + 0.02f * sin(seconds / 3.0f + 1.0f),
                2.40f + 0.02f * sin(seconds / 3.0f + 1.5f),
                9.2f + 1.4f * sin(seconds / 2.0f),
                speed <= 0.1f
                        ? 1.1f
                        : 9.2f + 1.4f * sin(seconds / 2.0f),
                14.1f + 0.1f * sin(seconds / 4.0f),
                18.5f + 0.5f * sin(seconds / 8.0f),
                steering,
                Math.max(0.0f, speed - turnDelta),
                Math.max(0.0f, speed + turnDelta),
                Math.max(0.0f, speed - turnDelta * 0.7f),
                Math.max(0.0f, speed + turnDelta * 0.7f),
                180.0f + 90.0f * sin(seconds / 1.7f),
                nowMs,
                nowMs,
                nowMs,
                nowMs,
                nowMs,
                driveModeAt(seconds));
    }

    private static float speedAt(float seconds) {
        for (int index = 1; index < SPEED_KEYFRAME_SECONDS.length; index++) {
            float endSeconds = SPEED_KEYFRAME_SECONDS[index];
            if (seconds <= endSeconds) {
                float startSeconds = SPEED_KEYFRAME_SECONDS[index - 1];
                float progress = (seconds - startSeconds)
                        / (endSeconds - startSeconds);
                return lerp(
                        SPEED_KEYFRAME_KPH[index - 1],
                        SPEED_KEYFRAME_KPH[index],
                        progress);
            }
        }
        return 0.0f;
    }

    private static int gearFor(float speed, float seconds) {
        if (seconds >= 27.0f) {
            return 0;
        }
        if (speed < 15.0f) {
            return 1;
        }
        if (speed < 35.0f) {
            return 2;
        }
        if (speed < 55.0f) {
            return 3;
        }
        if (speed <= 75.0f) {
            return 4;
        }
        if (speed < 95.0f) {
            return 5;
        }
        return 6;
    }

    private static String driveModeAt(float seconds) {
        if (seconds < 8.0f) {
            return "SPORT";
        }
        if (seconds < 20.0f) {
            return "NORMAL";
        }
        return "ECO";
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static float sin(float value) {
        return (float) Math.sin(value);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
