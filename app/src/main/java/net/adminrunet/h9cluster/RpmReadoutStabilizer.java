package net.adminrunet.h9cluster;

/** Readable RPM digits alongside a continuously animated tachometer. */
public final class RpmReadoutStabilizer {
    private static final long MIN_UPDATE_MS = 160L;
    private static final float DEADBAND_RPM = 8.0f;
    private boolean initialized;
    private int displayed;
    private long changedAtMs;

    public int update(float animatedRpm, long nowMs) {
        float rpm = bounded(animatedRpm);
        int rounded = Math.round(rpm / 10.0f) * 10;
        if (!initialized || nowMs < changedAtMs) {
            initialized = true;
            displayed = rounded;
            changedAtMs = nowMs;
        } else if (rpm == 0.0f || (needsUpdate(rpm) && nowMs - changedAtMs >= MIN_UPDATE_MS)) {
            if (displayed != rounded) {
                displayed = rounded;
                changedAtMs = nowMs;
            }
        }
        return displayed;
    }

    public boolean needsUpdate(float animatedRpm) {
        float rpm = bounded(animatedRpm);
        return !initialized || (Math.round(rpm / 10.0f) * 10 != displayed
                && (rpm == 0.0f || Math.abs(rpm - displayed) > DEADBAND_RPM));
    }

    private static float bounded(float rpm) {
        return Float.isFinite(rpm) ? Math.max(0.0f, Math.min(8000.0f, rpm)) : 0.0f;
    }
}
