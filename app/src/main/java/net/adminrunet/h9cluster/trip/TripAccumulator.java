package net.adminrunet.h9cluster.trip;

/** Accumulates a confirmed engine run using monotonic telemetry samples. */
public final class TripAccumulator {
    private final long startedAtMs;
    private long lastUpdatedAtMs;
    private double distanceKm;
    private double lastJourneyOdometerKm;
    private boolean lastJourneyOdometerValid;
    private boolean distanceValid;
    private double fuelLiters;
    private float lastFuelConsumption;
    private boolean lastFuelConsumptionValid;
    private int lastSpeedKph;
    private boolean fuelReliable;
    private boolean hasFuelInterval;

    private TripAccumulator(TripSession session) {
        startedAtMs = session.startedAtMs;
        lastUpdatedAtMs = session.lastUpdatedAtMs;
        distanceKm = session.distanceKm;
        lastJourneyOdometerKm = session.lastJourneyOdometerKm;
        lastJourneyOdometerValid = session.lastJourneyOdometerValid;
        distanceValid = session.distanceValid;
        fuelLiters = session.fuelLiters;
        lastFuelConsumption = session.lastFuelConsumption;
        lastFuelConsumptionValid = session.lastFuelConsumptionValid;
        lastSpeedKph = session.lastSpeedKph;
        fuelReliable = session.fuelReliable;
        hasFuelInterval = session.hasFuelInterval;
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
                0.0,
                Float.NaN,
                false,
                0,
                true,
                false));
    }

    public static TripAccumulator restore(TripSession session) {
        return new TripAccumulator(session);
    }

    public void update(TripTelemetry telemetry) {
        if (telemetry.capturedAtMs < lastUpdatedAtMs) {
            return;
        }

        long elapsedMs = telemetry.capturedAtMs - lastUpdatedAtMs;
        double distanceDeltaKm = updateDistance(telemetry);
        if (elapsedMs > 0L) {
            updateFuel(telemetry, distanceDeltaKm, elapsedMs);
        }

        lastUpdatedAtMs = telemetry.capturedAtMs;
        lastFuelConsumption = telemetry.instantFuelConsumption;
        lastFuelConsumptionValid = telemetry.instantFuelConsumptionValid;
        lastSpeedKph = telemetry.speedKph;
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
                fuelLiters,
                lastFuelConsumption,
                lastFuelConsumptionValid,
                lastSpeedKph,
                fuelReliable,
                hasFuelInterval);
    }

    public TripSummary finish(long stoppedAtMs) {
        boolean durationValid =
                stoppedAtMs >= startedAtMs && startedAtMs >= 0L;
        long durationMs = durationValid ? stoppedAtMs - startedAtMs : 0L;
        boolean consumptionValid = distanceValid
                && distanceKm > 0.0
                && fuelReliable
                && hasFuelInterval
                && Double.isFinite(fuelLiters);
        double averageConsumption = consumptionValid
                ? fuelLiters / distanceKm * 100.0
                : Double.NaN;
        return new TripSummary(
                distanceKm,
                distanceValid,
                averageConsumption,
                consumptionValid,
                durationMs,
                durationValid);
    }

    private double updateDistance(TripTelemetry telemetry) {
        if (!telemetry.journeyOdometerValid) {
            lastJourneyOdometerValid = false;
            return Double.NaN;
        }

        double deltaKm = Double.NaN;
        if (lastJourneyOdometerValid) {
            double candidateDelta =
                    telemetry.journeyOdometerKm - lastJourneyOdometerKm;
            if (candidateDelta >= 0.0 && Double.isFinite(candidateDelta)) {
                deltaKm = candidateDelta;
                distanceKm += candidateDelta;
                distanceValid = true;
            }
        }
        lastJourneyOdometerKm = telemetry.journeyOdometerKm;
        lastJourneyOdometerValid = true;
        return deltaKm;
    }

    private void updateFuel(
            TripTelemetry telemetry,
            double distanceDeltaKm,
            long elapsedMs) {
        double intervalDistanceKm = telemetry.speedKph > 0
                ? distanceDeltaKm
                : 0.0;
        double intervalFuel = telemetry.instantFuelConsumptionValid
                ? TripTelemetryConverter.fuelLitersForInterval(
                        telemetry.instantFuelConsumption,
                        telemetry.speedKph,
                        intervalDistanceKm,
                        elapsedMs)
                : Double.NaN;
        if (!Double.isFinite(intervalFuel)) {
            fuelReliable = false;
            return;
        }
        fuelLiters += intervalFuel;
        hasFuelInterval = true;
    }
}
