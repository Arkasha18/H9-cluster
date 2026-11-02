package net.adminrunet.h9cluster;

/** All calls run on the main thread; timestamps use elapsedRealtime(). */
final class VehicleSpeedSelector {
    enum Source { GWM, MCU, UNAVAILABLE }

    private static final long GWM_STALE_MS = 2000;
    static final long MCU_STREAM_STALE_MS = 1000;
    private static final long FROZEN_MS = 1000;
    private static final long DISAGREEMENT_MS = 400;
    private static final long RECOVERY_MS = 1000;
    private static final float DISAGREEMENT_KPH = 1.0f;

    private float gwmSpeed = Float.NaN;
    private long gwmAt;
    private long gwmChangedAt;
    private float mcuSpeed = Float.NaN;
    private long mcuAt;
    private long disagreementSince;
    private long rejectedAt;
    private long agreementSince;
    private long gwmChanges;
    private long rejectedGwmChanges;
    private Source source = Source.UNAVAILABLE;

    void reset() {
        gwmSpeed = mcuSpeed = Float.NaN;
        gwmAt = gwmChangedAt = mcuAt = disagreementSince = rejectedAt = agreementSince = 0;
        gwmChanges = rejectedGwmChanges = 0;
        source = Source.UNAVAILABLE;
    }

    void onGwm(float speed, long now, boolean liveCallback) {
        if (!valid(speed)) {
            gwmAt = 0;
            return;
        }
        boolean changed = !Float.isFinite(gwmSpeed) || Math.abs(speed - gwmSpeed) >= 0.05f;
        if (changed) {
            gwmChangedAt = now;
            gwmChanges++;
        }
        // Re-reading the same adapter cache is not a new measurement.
        if (changed || liveCallback) gwmAt = now;
        gwmSpeed = speed;
    }

    void onMcu(float speed, long now) {
        mcuSpeed = valid(speed) ? speed : Float.NaN;
        mcuAt = valid(speed) ? now : 0;
    }

    int select(long now, long mcuStreamAt) {
        boolean gwmFresh = valid(gwmSpeed) && fresh(now, gwmAt, GWM_STALE_MS);
        // The MCU publishes changed signals, not periodic ABS3 heartbeats.
        // Retain its last explicit speed (including zero) only within a live session.
        boolean mcuFresh = valid(mcuSpeed) && mcuAt > 0 && mcuAt <= mcuStreamAt
                && fresh(now, mcuStreamAt, MCU_STREAM_STALE_MS);
        if (gwmFresh && mcuFresh) {
            boolean agrees = Math.abs(gwmSpeed - mcuSpeed) < DISAGREEMENT_KPH;
            if (rejectedAt > 0) {
                if (agrees && gwmChangedAt > rejectedAt) {
                    if (agreementSince == 0) agreementSince = now;
                    if (now - agreementSince >= RECOVERY_MS) {
                        rejectedAt = 0;
                        disagreementSince = 0;
                    }
                } else {
                    agreementSince = 0;
                }
            } else if (!agrees && now - gwmChangedAt >= FROZEN_MS) {
                if (disagreementSince == 0) disagreementSince = now;
                if (now - disagreementSince >= DISAGREEMENT_MS) {
                    rejectedAt = now;
                    rejectedGwmChanges = gwmChanges;
                    agreementSince = 0;
                }
            } else {
                disagreementSince = 0;
            }
        } else if (rejectedAt > 0 && gwmFresh && !mcuFresh
                && gwmChanges - rejectedGwmChanges >= 2) {
            // If MCU disappears, two changed GWM measurements also prove that
            // the previous freeze ended. Never recover from repeated cached zeros.
            if (agreementSince == 0) agreementSince = now;
            if (now - agreementSince >= RECOVERY_MS) {
                rejectedAt = disagreementSince = 0;
            }
        } else {
            disagreementSince = agreementSince = 0;
        }
        if (gwmFresh && rejectedAt == 0) {
            source = Source.GWM;
            return Math.round(gwmSpeed);
        }
        if (mcuFresh) {
            source = Source.MCU;
            return Math.round(mcuSpeed);
        }
        source = Source.UNAVAILABLE;
        return -1;
    }

    Source source() { return source; }

    private static boolean valid(float speed) {
        return Float.isFinite(speed) && speed >= 0 && speed <= 300;
    }

    private static boolean fresh(long now, long timestamp, long maximumAge) {
        return timestamp > 0 && timestamp <= now && now - timestamp <= maximumAge;
    }
}
