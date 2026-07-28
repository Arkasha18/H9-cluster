package net.adminrunet.h9cluster.trip;

import java.util.Locale;

/** Builds a redacted, rate-limited line for one vehicle unit-validation run. */
public final class TripTelemetryDiagnostics {
    private static final long MINIMUM_INTERVAL_MS = 1_000L;

    private TripTelemetryDiagnostics() {
    }

    public static String format(
            TripTelemetry telemetry,
            double convertedJourneyKm) {
        return String.format(
                Locale.US,
                "journeyRaw=%.3f journeyKm=%.3f "
                        + "instantFuelRaw=%.3f speedKph=%d elapsedMs=%d",
                telemetry.rawJourneyOdometer,
                convertedJourneyKm,
                telemetry.instantFuelConsumption,
                telemetry.speedKph,
                telemetry.capturedAtMs);
    }

    public static boolean shouldLog(long lastLoggedAtMs, long nowMs) {
        return lastLoggedAtMs < 0L
                || nowMs < lastLoggedAtMs
                || nowMs - lastLoggedAtMs >= MINIMUM_INTERVAL_MS;
    }
}
