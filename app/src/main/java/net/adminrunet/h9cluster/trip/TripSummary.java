package net.adminrunet.h9cluster.trip;

/** Final independently-valid trip metrics shown after confirmed shutdown. */
public final class TripSummary {
    public final double distanceKm;
    public final boolean distanceValid;
    public final double averageConsumptionLitersPer100Km;
    public final boolean consumptionValid;
    public final long durationMs;
    public final boolean durationValid;
    public final double fuelUsedLiters;
    public final boolean fuelUsedValid;

    public TripSummary(
            double distanceKm,
            boolean distanceValid,
            double averageConsumptionLitersPer100Km,
            boolean consumptionValid,
            long durationMs,
            boolean durationValid) {
        this(
                distanceKm,
                distanceValid,
                averageConsumptionLitersPer100Km,
                consumptionValid,
                durationMs,
                durationValid,
                Double.NaN,
                false);
    }

    public TripSummary(
            double distanceKm,
            boolean distanceValid,
            double averageConsumptionLitersPer100Km,
            boolean consumptionValid,
            long durationMs,
            boolean durationValid,
            double fuelUsedLiters,
            boolean fuelUsedValid) {
        this.distanceKm = distanceKm;
        this.distanceValid = distanceValid;
        this.averageConsumptionLitersPer100Km =
                averageConsumptionLitersPer100Km;
        this.consumptionValid = consumptionValid;
        this.durationMs = durationMs;
        this.durationValid = durationValid;
        this.fuelUsedLiters = fuelUsedLiters;
        this.fuelUsedValid = fuelUsedValid;
    }
}
