package net.adminrunet.h9cluster;

/**
 * Stabilizes the visible tachometer value without altering the raw RPM used by
 * trip detection and other vehicle logic.
 */
public final class RpmDisplaySmoother {
    private static final int MAX_RPM = 8000;
    private static final int IDLE_MIN_RPM = 500;
    private static final int IDLE_MAX_RPM = 1200;
    private static final int IDLE_MAX_SPEED_KPH = 2;
    private static final float IDLE_STEP_RPM = 100.0f;
    private static final float IDLE_HYSTERESIS_RPM = 75.0f;
    private static final float IDLE_TIME_CONSTANT_MS = 700.0f;
    private static final float MOVING_TIME_CONSTANT_MS = 140.0f;

    private boolean initialized;
    private boolean idleMode;
    private long lastUpdatedAtMs;
    private float filteredRpm;
    private float displayedRpm;

    public float update(int rawRpm, int speedKph, long nowMs) {
        float targetRpm = clamp(rawRpm, 0, MAX_RPM);
        boolean idle = isIdle(targetRpm, speedKph);
        if (!initialized || nowMs <= lastUpdatedAtMs) {
            initialized = true;
            idleMode = idle;
            lastUpdatedAtMs = nowMs;
            filteredRpm = targetRpm;
            displayedRpm = idle ? quantizeIdle(targetRpm) : targetRpm;
            return displayedRpm;
        }

        if (targetRpm == 0.0f) {
            idleMode = false;
            lastUpdatedAtMs = nowMs;
            filteredRpm = 0.0f;
            displayedRpm = 0.0f;
            return 0.0f;
        }

        float elapsedMs = Math.min(100.0f, nowMs - lastUpdatedAtMs);
        lastUpdatedAtMs = nowMs;
        if (idle && !idleMode) {
            filteredRpm = targetRpm;
            displayedRpm = quantizeIdle(targetRpm);
        } else {
            float timeConstantMs = idle
                    ? IDLE_TIME_CONSTANT_MS
                    : MOVING_TIME_CONSTANT_MS;
            float blend = 1.0f
                    - (float) Math.exp(-elapsedMs / timeConstantMs);
            filteredRpm += (targetRpm - filteredRpm) * blend;
            if (idle) {
                updateIdleBucket();
            } else {
                displayedRpm = filteredRpm;
            }
        }
        idleMode = idle;
        return displayedRpm;
    }

    public boolean needsAnimationFrame(int rawRpm, int speedKph) {
        if (!initialized || isIdle(rawRpm, speedKph)) {
            return false;
        }
        return Math.abs(clamp(rawRpm, 0, MAX_RPM) - displayedRpm) > 0.5f;
    }

    private void updateIdleBucket() {
        while (filteredRpm >= displayedRpm + IDLE_HYSTERESIS_RPM
                && displayedRpm < IDLE_MAX_RPM) {
            displayedRpm += IDLE_STEP_RPM;
        }
        while (filteredRpm <= displayedRpm - IDLE_HYSTERESIS_RPM
                && displayedRpm > IDLE_MIN_RPM) {
            displayedRpm -= IDLE_STEP_RPM;
        }
    }

    private static boolean isIdle(float rpm, int speedKph) {
        return speedKph <= IDLE_MAX_SPEED_KPH
                && rpm >= IDLE_MIN_RPM
                && rpm <= IDLE_MAX_RPM;
    }

    private static float quantizeIdle(float rpm) {
        return Math.round(rpm / IDLE_STEP_RPM) * IDLE_STEP_RPM;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
