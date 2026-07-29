package net.adminrunet.h9cluster.trip;

/** Accumulates a confirmed engine run using monotonic telemetry samples. */
public final class TripAccumulator {
    private final long startedAtMs;
    private long lastUpdatedAtMs;
    private double distanceKm;
    private double lastJourneyOdometerKm;
    private boolean lastJourneyOdometerValid;
    private boolean distanceValid;
    private float lastAverageFuelConsumption;
    private boolean lastAverageFuelConsumptionValid;

    private TripAccumulator(TripSession session) {
        startedAtMs = session.startedAtMs;
        lastUpdatedAtMs = session.lastUpdatedAtMs;
        distanceKm = session.distanceKm;
        lastJourneyOdometerKm = session.lastJourneyOdometerKm;
        lastJourneyOdometerValid = session.lastJourneyOdometerValid;
        distanceValid = session.distanceValid;
        lastAverageFuelConsumption = session.lastAverageFuelConsumption;
        lastAverageFuelConsumptionValid =
                session.lastAverageFuelConsumptionValid;
    }

    public static TripAccumulator start(long startedAtMs) {
        return new TripAccumulator(new TripSession(
                true,
                startedAtMs,
                startedAtMs,
                0.0,
                Double.NaN,
                false,
                false,
                Float.NaN,
                false));
    }

    public static TripAccumulator restore(TripSession session) {
        return new TripAccumulator(session);
    }

    public void update(TripTelemetry telemetry) {
        if (telemetry.capturedAtMs < lastUpdatedAtMs) {
            return;
        }

        updateDistance(telemetry);
        lastUpdatedAtMs = telemetry.capturedAtMs;
        if (telemetry.averageFuelConsumptionValid) {
            lastAverageFuelConsumption = telemetry.averageFuelConsumption;
            lastAverageFuelConsumptionValid = true;
        }
    }

    public TripSession snapshot() {
        return new TripSession(
                true,
                startedAtMs,
                lastUpdatedAtMs,
                distanceKm,
                lastJourneyOdometerKm,
                lastJourneyOdometerValid,
                distanceValid,
                lastAverageFuelConsumption,
                lastAverageFuelConsumptionValid);
    }

    public TripSummary finish(long stoppedAtMs) {
        boolean durationValid =
                stoppedAtMs >= startedAtMs && startedAtMs >= 0L;
        long durationMs = durationValid ? stoppedAtMs - startedAtMs : 0L;
        boolean consumptionValid = distanceValid
                && distanceKm > 0.0
                && lastAverageFuelConsumptionValid
                && Float.isFinite(lastAverageFuelConsumption)
                && lastAverageFuelConsumption > 0.0f;
        double summaryFuelLiters = consumptionValid
                ? distanceKm * lastAverageFuelConsumption / 100.0
                : Double.NaN;
        double averageConsumption = consumptionValid
                ? lastAverageFuelConsumption
                : Double.NaN;
        return new TripSummary(
                distanceKm,
                distanceValid,
                averageConsumption,
                consumptionValid,
                durationMs,
                durationValid,
                summaryFuelLiters,
                consumptionValid);
    }

    private void updateDistance(TripTelemetry telemetry) {
        if (!telemetry.journeyOdometerValid) {
            lastJourneyOdometerValid = false;
            return;
        }

        if (lastJourneyOdometerValid) {
            double candidateDelta =
                    telemetry.journeyOdometerKm - lastJourneyOdometerKm;
            if (candidateDelta >= 0.0 && Double.isFinite(candidateDelta)) {
                distanceKm += candidateDelta;
                distanceValid = true;
            }
        }
        lastJourneyOdometerKm = telemetry.journeyOdometerKm;
        lastJourneyOdometerValid = true;
    }
}
