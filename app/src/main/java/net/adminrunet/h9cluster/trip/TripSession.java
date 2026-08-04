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
            float lastAverageFuelConsumption,
            boolean lastAverageFuelConsumptionValid) {
        this.active = active;
        this.startedAtMs = startedAtMs;
        this.lastUpdatedAtMs = lastUpdatedAtMs;
        this.distanceKm = distanceKm;
        this.lastJourneyOdometerKm = lastJourneyOdometerKm;
        this.lastJourneyOdometerValid = lastJourneyOdometerValid;
        this.distanceValid = distanceValid;
        this.lastAverageFuelConsumption = lastAverageFuelConsumption;
        this.lastAverageFuelConsumptionValid =
                lastAverageFuelConsumptionValid;
    }
}
