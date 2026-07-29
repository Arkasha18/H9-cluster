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
    public final float averageFuelConsumption;
    public final boolean averageFuelConsumptionValid;

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
                instantFuelConsumptionValid,
                Float.NaN,
                false);
    }

    public TripTelemetry(
            long capturedAtMs,
            int speedKph,
            double journeyOdometerKm,
            boolean journeyOdometerValid,
            float instantFuelConsumption,
            boolean instantFuelConsumptionValid,
            float averageFuelConsumption,
            boolean averageFuelConsumptionValid) {
        this(
                capturedAtMs,
                speedKph,
                (float) journeyOdometerKm,
                journeyOdometerKm,
                journeyOdometerValid,
                instantFuelConsumption,
                instantFuelConsumptionValid,
                averageFuelConsumption,
                averageFuelConsumptionValid);
    }

    public TripTelemetry(
            long capturedAtMs,
            int speedKph,
            float rawJourneyOdometer,
            double journeyOdometerKm,
            boolean journeyOdometerValid,
            float instantFuelConsumption,
            boolean instantFuelConsumptionValid) {
        this(
                capturedAtMs,
                speedKph,
                rawJourneyOdometer,
                journeyOdometerKm,
                journeyOdometerValid,
                instantFuelConsumption,
                instantFuelConsumptionValid,
                Float.NaN,
                false);
    }

    public TripTelemetry(
            long capturedAtMs,
            int speedKph,
            float rawJourneyOdometer,
            double journeyOdometerKm,
            boolean journeyOdometerValid,
            float instantFuelConsumption,
            boolean instantFuelConsumptionValid,
            float averageFuelConsumption,
            boolean averageFuelConsumptionValid) {
        this.capturedAtMs = capturedAtMs;
        this.speedKph = speedKph;
        this.rawJourneyOdometer = rawJourneyOdometer;
        this.journeyOdometerKm = journeyOdometerKm;
        this.journeyOdometerValid = journeyOdometerValid;
        this.instantFuelConsumption = instantFuelConsumption;
        this.instantFuelConsumptionValid = instantFuelConsumptionValid;
        this.averageFuelConsumption = averageFuelConsumption;
        this.averageFuelConsumptionValid = averageFuelConsumptionValid;
    }

    public static TripTelemetry from(ClusterState state, long nowMs) {
        double journeyKm =
                TripTelemetryConverter.journeyOdometerKm(state.dayKm);
        boolean journeyValid = Double.isFinite(journeyKm)
                && isObserved(state.journeyOdometerUpdatedAtMs, nowMs);
        boolean consumptionValid =
                Float.isFinite(state.instantFuelConsumption)
                        && state.instantFuelConsumption >= 0.0f
                        && isFresh(
                                state.instantFuelConsumptionUpdatedAtMs,
                                nowMs);
        boolean averageConsumptionValid =
                Float.isFinite(state.consumptionLitersPer100Km)
                        && state.consumptionLitersPer100Km > 0.0f;
        return new TripTelemetry(
                nowMs,
                Math.max(0, state.speedKph),
                state.dayKm,
                journeyKm,
                journeyValid,
                state.instantFuelConsumption,
                consumptionValid,
                state.consumptionLitersPer100Km,
                averageConsumptionValid);
    }

    private static boolean isFresh(long updatedAtMs, long nowMs) {
        return isObserved(updatedAtMs, nowMs)
                && nowMs - updatedAtMs <= FRESH_MS;
    }

    private static boolean isObserved(long updatedAtMs, long nowMs) {
        return updatedAtMs > 0L && nowMs >= updatedAtMs;
    }
}
