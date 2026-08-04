package net.adminrunet.h9cluster.trip;

/** Isolates vehicle-unit assumptions used by trip accumulation. */
public final class TripTelemetryConverter {
    private TripTelemetryConverter() {
    }

    public static double journeyOdometerKm(float rawValue) {
        return Float.isFinite(rawValue) && rawValue >= 0.0f
                ? rawValue
                : Double.NaN;
    }
}
