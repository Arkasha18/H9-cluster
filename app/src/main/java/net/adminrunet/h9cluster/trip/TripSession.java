package net.adminrunet.h9cluster.trip;

/** Serializable snapshot of an active trip accumulator. */
public final class TripSession {
    public final boolean active;
    public final long startedAtMs;
    public final long lastUpdatedAtMs;
    public final double distanceKm;
    public final double lastJourneyOdometerKm;
    public final boolean lastJourneyOdometerValid;
    public final boolean distanceValid;
    public final double fuelLiters;
    public final float lastFuelConsumption;
    public final boolean lastFuelConsumptionValid;
    public final int lastSpeedKph;
    public final boolean fuelReliable;
    public final boolean hasFuelInterval;
    public final float lastAverageFuelConsumption;
    public final boolean lastAverageFuelConsumptionValid;

    public TripSession(
            boolean active,
            long startedAtMs,
            long lastUpdatedAtMs,
            double distanceKm,
            double lastJourneyOdometerKm,
            boolean lastJourneyOdometerValid,
            boolean distanceValid,
            double fuelLiters,
            float lastFuelConsumption,
            boolean lastFuelConsumptionValid,
            int lastSpeedKph,
            boolean fuelReliable,
            boolean hasFuelInterval) {
        this(
                active,
                startedAtMs,
                lastUpdatedAtMs,
                distanceKm,
                lastJourneyOdometerKm,
                lastJourneyOdometerValid,
                distanceValid,
                fuelLiters,
                lastFuelConsumption,
                lastFuelConsumptionValid,
                lastSpeedKph,
                fuelReliable,
                hasFuelInterval,
                Float.NaN,
                false);
    }

    public TripSession(
            boolean active,
            long startedAtMs,
            long lastUpdatedAtMs,
            double distanceKm,
            double lastJourneyOdometerKm,
            boolean lastJourneyOdometerValid,
            boolean distanceValid,
            double fuelLiters,
            float lastFuelConsumption,
            boolean lastFuelConsumptionValid,
            int lastSpeedKph,
            boolean fuelReliable,
            boolean hasFuelInterval,
            float lastAverageFuelConsumption,
            boolean lastAverageFuelConsumptionValid) {
        this.active = active;
        this.startedAtMs = startedAtMs;
        this.lastUpdatedAtMs = lastUpdatedAtMs;
        this.distanceKm = distanceKm;
        this.lastJourneyOdometerKm = lastJourneyOdometerKm;
        this.lastJourneyOdometerValid = lastJourneyOdometerValid;
        this.distanceValid = distanceValid;
        this.fuelLiters = fuelLiters;
        this.lastFuelConsumption = lastFuelConsumption;
        this.lastFuelConsumptionValid = lastFuelConsumptionValid;
        this.lastSpeedKph = lastSpeedKph;
        this.fuelReliable = fuelReliable;
        this.hasFuelInterval = hasFuelInterval;
        this.lastAverageFuelConsumption = lastAverageFuelConsumption;
        this.lastAverageFuelConsumptionValid =
                lastAverageFuelConsumptionValid;
    }
}
