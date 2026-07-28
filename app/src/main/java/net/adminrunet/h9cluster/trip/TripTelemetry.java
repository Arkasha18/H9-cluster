package net.adminrunet.h9cluster.trip;

import net.adminrunet.h9cluster.ClusterState;

/** One normalized, freshness-aware telemetry sample used by a trip. */
public final class TripTelemetry {
    private static final long FRESH_MS = 1_500L;

    public final long capturedAtMs;
    public final int speedKph;
    public final float rawJourneyOdometer;
    public final double journeyOdometerKm;
    public final boolean journeyOdometerValid;
    public final float instantFuelConsumption;
    public final boolean instantFuelConsumptionValid;

    public TripTelemetry(
            long capturedAtMs,
            int speedKph,
            double journeyOdometerKm,
            boolean journeyOdometerValid,
            float instantFuelConsumption,
            boolean instantFuelConsumptionValid) {
        this(
                capturedAtMs,
                speedKph,
                (float) journeyOdometerKm,
                journeyOdometerKm,
                journeyOdometerValid,
                instantFuelConsumption,
                instantFuelConsumptionValid);
    }

    public TripTelemetry(
            long capturedAtMs,
            int speedKph,
            float rawJourneyOdometer,
            double journeyOdometerKm,
            boolean journeyOdometerValid,
            float instantFuelConsumption,
            boolean instantFuelConsumptionValid) {
        this.capturedAtMs = capturedAtMs;
        this.speedKph = speedKph;
        this.rawJourneyOdometer = rawJourneyOdometer;
        this.journeyOdometerKm = journeyOdometerKm;
        this.journeyOdometerValid = journeyOdometerValid;
        this.instantFuelConsumption = instantFuelConsumption;
        this.instantFuelConsumptionValid = instantFuelConsumptionValid;
    }

    public static TripTelemetry from(ClusterState state, long nowMs) {
        double journeyKm =
                TripTelemetryConverter.journeyOdometerKm(state.dayKm);
        boolean journeyValid = Double.isFinite(journeyKm)
                && isFresh(state.journeyOdometerUpdatedAtMs, nowMs);
        boolean consumptionValid =
                Float.isFinite(state.instantFuelConsumption)
                        && state.instantFuelConsumption >= 0.0f
                        && isFresh(
                                state.instantFuelConsumptionUpdatedAtMs,
                                nowMs);
        return new TripTelemetry(
                nowMs,
                Math.max(0, state.speedKph),
                state.dayKm,
                journeyKm,
                journeyValid,
                state.instantFuelConsumption,
                consumptionValid);
    }

    private static boolean isFresh(long updatedAtMs, long nowMs) {
        return updatedAtMs > 0L
                && nowMs >= updatedAtMs
                && nowMs - updatedAtMs <= FRESH_MS;
    }
}
