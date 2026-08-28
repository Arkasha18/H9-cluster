package net.adminrunet.h9cluster;

/**
 * Stabilizes the visible tachometer value without altering the raw RPM used by
 * trip detection and other vehicle logic.
 */
public final class RpmDisplaySmoother {
    private static final int MAX_RPM = 8000;
    private static final int IDLE_MAX_SPEED_KPH = 2;
    private static final float IDLE_JITTER_MIN_RPM = 775.0f;
    private static final float IDLE_JITTER_MAX_RPM = 1025.0f;
    private static final float IDLE_MIN_RPM = 800.0f;
    private static final float IDLE_MAX_RPM = 1000.0f;
    private static final float IDLE_STEP_RPM = 100.0f;
    private static final long IDLE_CHANGE_CONFIRM_MS = 250L;

    private boolean initialized;
    private float displayedRpm;
    private float pendingIdleRpm;
    private long pendingIdleSinceMs;

    public float update(int rawRpm, int speedKph, long nowMs) {
        float targetRpm = clamp(rawRpm, 0, MAX_RPM);
        if (!initialized) {
            initialized = true;
            displayedRpm = isIdleJitterBand(targetRpm, speedKph)
                    ? nearestIdleValue(targetRpm)
                    : targetRpm;
            clearPendingIdleChange();
            return displayedRpm;
        }

        if (!isIdleJitterBand(targetRpm, speedKph)) {
            displayedRpm = targetRpm;
            clearPendingIdleChange();
            return displayedRpm;
        }

        float idleRpm = nearestIdleValue(targetRpm);
        if (!isIdleBucket(displayedRpm)) {
            displayedRpm = idleRpm;
            clearPendingIdleChange();
        } else if (idleRpm == displayedRpm) {
            clearPendingIdleChange();
        } else if (pendingIdleRpm != idleRpm || nowMs < pendingIdleSinceMs) {
            pendingIdleRpm = idleRpm;
            pendingIdleSinceMs = nowMs;
        } else if (nowMs - pendingIdleSinceMs >= IDLE_CHANGE_CONFIRM_MS) {
            displayedRpm = idleRpm;
            clearPendingIdleChange();
        }
        return displayedRpm;
    }

    public boolean needsAnimationFrame(int rawRpm, int speedKph) {
        return false;
    }

    private static boolean isIdleJitterBand(float rpm, int speedKph) {
        return speedKph <= IDLE_MAX_SPEED_KPH
                && rpm >= IDLE_JITTER_MIN_RPM
                && rpm <= IDLE_JITTER_MAX_RPM;
    }

    private static float nearestIdleValue(float rpm) {
        float rounded = Math.round(rpm / IDLE_STEP_RPM) * IDLE_STEP_RPM;
        return clamp(rounded, IDLE_MIN_RPM, IDLE_MAX_RPM);
    }

    private static boolean isIdleBucket(float rpm) {
        return rpm >= IDLE_MIN_RPM
                && rpm <= IDLE_MAX_RPM
                && rpm % IDLE_STEP_RPM == 0.0f;
    }

    private void clearPendingIdleChange() {
        pendingIdleRpm = -1.0f;
        pendingIdleSinceMs = 0L;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
