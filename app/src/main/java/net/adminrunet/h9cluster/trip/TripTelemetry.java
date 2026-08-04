package net.adminrunet.h9cluster.trip;

import net.adminrunet.h9cluster.ClusterState;

/** One normalized, freshness-aware telemetry sample used by a trip. */
public final class TripTelemetry {
    /**
     * The head unit republishes journey values on change, so a parked car can
     * legitimately go a long time without a new sample. This ceiling only has
     * to catch a source that went away entirely, not a value that is steady.
     */
    private static final long STALE_MS = 60_000L;

    public final long capturedAtMs;
    public final int speedKph;
    public final float rawJourneyOdometer;
    public final double journeyOdometerKm;
    public final boolean journeyOdometerValid;
    public final float averageFuelConsumption;
    public final boolean averageFuelConsumptionValid;

    public TripTelemetry(
            long capturedAtMs,
            int speedKph,
            double journeyOdometerKm,
            boolean journeyOdometerValid,
            float averageFuelConsumption,
            boolean averageFuelConsumptionValid) {
        this(
                capturedAtMs,
                speedKph,
                (float) journeyOdometerKm,
                journeyOdometerKm,
                journeyOdometerValid,
                averageFuelConsumption,
                averageFuelConsumptionValid);
    }

    public TripTelemetry(
            long capturedAtMs,
            int speedKph,
            float rawJourneyOdometer,
            double journeyOdometerKm,
            boolean journeyOdometerValid,
            float averageFuelConsumption,
            boolean averageFuelConsumptionValid) {
        this.capturedAtMs = capturedAtMs;
        this.speedKph = speedKph;
        this.rawJourneyOdometer = rawJourneyOdometer;
        this.journeyOdometerKm = journeyOdometerKm;
        this.journeyOdometerValid = journeyOdometerValid;
        this.averageFuelConsumption = averageFuelConsumption;
        this.averageFuelConsumptionValid = averageFuelConsumptionValid;
    }

    public static TripTelemetry from(ClusterState state, long nowMs) {
        double journeyKm =
                TripTelemetryConverter.journeyOdometerKm(state.dayKm);
        boolean journeyValid = Double.isFinite(journeyKm)
                && isUsable(state.journeyOdometerUpdatedAtMs, nowMs);
        // Deliberately the journey average alone: the cluster's own value may
        // fall back to indicator B or to a carried-over reading, which must
        // never stand in for cur_journey_avg_fuel_consumption_a here.
        float journeyAverage = state.journeyAverageFuelConsumption;
        boolean averageConsumptionValid = Float.isFinite(journeyAverage)
                && journeyAverage > 0.0f
                && isUsable(
                        state.journeyAverageFuelConsumptionUpdatedAtMs,
                        nowMs);
        return new TripTelemetry(
                nowMs,
                Math.max(0, state.speedKph),
                state.dayKm,
                journeyKm,
                journeyValid,
                journeyAverage,
                averageConsumptionValid);
    }

    private static boolean isUsable(long updatedAtMs, long nowMs) {
        return updatedAtMs > 0L
                && nowMs >= updatedAtMs
                && nowMs - updatedAtMs <= STALE_MS;
    }
}
