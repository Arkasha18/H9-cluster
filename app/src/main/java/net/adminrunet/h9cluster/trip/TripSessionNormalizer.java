package net.adminrunet.h9cluster.trip;

/** Rejects cross-boot sessions and prevents malformed values from becoming valid. */
public final class TripSessionNormalizer {
    private TripSessionNormalizer() {
    }

    public static TripSession normalize(
            TripSession candidate,
            long currentElapsedRealtimeMs) {
        if (candidate == null
                || !candidate.active
                || candidate.startedAtMs < 0L
                || candidate.lastUpdatedAtMs < candidate.startedAtMs
                || currentElapsedRealtimeMs < candidate.startedAtMs
                || currentElapsedRealtimeMs < candidate.lastUpdatedAtMs) {
            return null;
        }

        boolean distanceFinite =
                isFiniteNonNegative(candidate.distanceKm);
        double distanceKm = distanceFinite ? candidate.distanceKm : 0.0;
        boolean distanceValid =
                distanceFinite && candidate.distanceValid;

        boolean journeyBaselineFinite =
                isFiniteNonNegative(candidate.lastJourneyOdometerKm);
        boolean journeyBaselineValid =
                candidate.lastJourneyOdometerValid
                        && journeyBaselineFinite;
        double journeyBaseline = journeyBaselineFinite
                ? candidate.lastJourneyOdometerKm
                : Double.NaN;

        boolean averageFuelFinite =
                Float.isFinite(candidate.lastAverageFuelConsumption)
                        && candidate.lastAverageFuelConsumption > 0.0f;
        boolean averageFuelValid =
                candidate.lastAverageFuelConsumptionValid
                        && averageFuelFinite;
        float averageFuel = averageFuelFinite
                ? candidate.lastAverageFuelConsumption
                : Float.NaN;

        return new TripSession(
                true,
                candidate.startedAtMs,
                candidate.lastUpdatedAtMs,
                distanceKm,
                journeyBaseline,
                journeyBaselineValid,
                distanceValid,
                averageFuel,
                averageFuelValid);
    }

    private static boolean isFiniteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }
}
