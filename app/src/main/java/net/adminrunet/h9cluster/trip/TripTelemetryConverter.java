package net.adminrunet.h9cluster.trip;

/** Isolates vehicle-unit assumptions used by trip accumulation. */
public final class TripTelemetryConverter {
    private static final double KILOMETRES_PER_CONSUMPTION_UNIT = 100.0;
    private static final double MILLIS_PER_HOUR = 3_600_000.0;

    private TripTelemetryConverter() {
    }

    public static double journeyOdometerKm(float rawValue) {
        return Float.isFinite(rawValue) && rawValue >= 0.0f
                ? rawValue
                : Double.NaN;
    }

    public static double fuelLitersForInterval(
            float rawConsumption,
            int speedKph,
            double distanceDeltaKm,
            long elapsedMs) {
        if (!Float.isFinite(rawConsumption)
                || rawConsumption < 0.0f
                || speedKph < 0
                || !Double.isFinite(distanceDeltaKm)
                || distanceDeltaKm < 0.0
                || elapsedMs < 0L) {
            return Double.NaN;
        }
        if (speedKph > 0) {
            return rawConsumption
                    * distanceDeltaKm
                    / KILOMETRES_PER_CONSUMPTION_UNIT;
        }
        return rawConsumption * elapsedMs / MILLIS_PER_HOUR;
    }
}
