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

        boolean fuelFinite = isFiniteNonNegative(candidate.fuelLiters);
        double fuelLiters = fuelFinite ? candidate.fuelLiters : 0.0;
        boolean fuelSampleFinite =
                Float.isFinite(candidate.lastFuelConsumption)
                        && candidate.lastFuelConsumption >= 0.0f;
        boolean malformedClaimedFuelSample =
                candidate.lastFuelConsumptionValid && !fuelSampleFinite;
        boolean fuelSampleValid =
                candidate.lastFuelConsumptionValid && fuelSampleFinite;
        float fuelSample = fuelSampleFinite
                ? candidate.lastFuelConsumption
                : Float.NaN;
        boolean speedValid = candidate.lastSpeedKph >= 0;
        int speedKph = speedValid ? candidate.lastSpeedKph : 0;
        boolean fuelReliable = candidate.fuelReliable
                && fuelFinite
                && distanceFinite
                && !malformedClaimedFuelSample
                && speedValid;
        boolean hasFuelInterval =
                candidate.hasFuelInterval && fuelReliable;

        return new TripSession(
                true,
                candidate.startedAtMs,
                candidate.lastUpdatedAtMs,
                distanceKm,
                journeyBaseline,
                journeyBaselineValid,
                distanceValid,
                fuelLiters,
                fuelSample,
                fuelSampleValid,
                speedKph,
                fuelReliable,
                hasFuelInterval);
    }

    private static boolean isFiniteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }
}
