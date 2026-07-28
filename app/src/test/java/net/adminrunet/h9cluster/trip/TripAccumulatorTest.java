package net.adminrunet.h9cluster.trip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TripAccumulatorTest {
    @Test
    public void accumulatesPositiveJourneyDeltas() {
        TripAccumulator accumulator = TripAccumulator.start(1_000L);

        accumulator.update(telemetry(1_000L, 60, 10.0, true, 10.0f, true));
        accumulator.update(telemetry(2_000L, 60, 10.2, true, 10.0f, true));
        accumulator.update(telemetry(3_000L, 60, 10.5, true, 10.0f, true));

        TripSummary summary = accumulator.finish(4_000L);
        assertTrue(summary.distanceValid);
        assertEquals(0.5, summary.distanceKm, 0.0001);
    }

    @Test
    public void rebasesAfterManualJourneyCounterReset() {
        TripAccumulator accumulator = TripAccumulator.start(1_000L);

        accumulator.update(telemetry(1_000L, 60, 10.0, true, 10.0f, true));
        accumulator.update(telemetry(2_000L, 60, 10.4, true, 10.0f, true));
        accumulator.update(telemetry(3_000L, 60, 0.0, true, 10.0f, true));
        accumulator.update(telemetry(4_000L, 60, 0.3, true, 10.0f, true));

        assertEquals(0.7, accumulator.finish(5_000L).distanceKm, 0.0001);
    }

    @Test
    public void needsTwoValidSamplesBeforeDistanceIsReliable() {
        TripAccumulator accumulator = TripAccumulator.start(1_000L);

        accumulator.update(telemetry(1_000L, 0, 0.0, false, 1.0f, true));
        accumulator.update(telemetry(2_000L, 0, 10.0, true, 1.0f, true));
        assertFalse(accumulator.finish(2_000L).distanceValid);

        accumulator.update(telemetry(3_000L, 0, 10.0, true, 1.0f, true));
        assertTrue(accumulator.finish(3_000L).distanceValid);
    }

    @Test
    public void integratesMovingAndIdleFuelIntervals() {
        TripAccumulator moving = TripAccumulator.start(0L);
        moving.update(telemetry(0L, 60, 10.0, true, 10.0f, true));
        moving.update(telemetry(6_000L, 60, 10.1, true, 10.0f, true));

        TripSession movingSession = moving.snapshot();
        assertEquals(0.01, movingSession.fuelLiters, 0.0001);
        assertTrue(moving.finish(6_000L).consumptionValid);
        assertEquals(
                10.0,
                moving.finish(6_000L).averageConsumptionLitersPer100Km,
                0.0001);

        TripAccumulator idle = TripAccumulator.start(0L);
        idle.update(telemetry(0L, 0, 10.0, true, 1.0f, true));
        idle.update(telemetry(3_600L, 0, 10.0, true, 1.0f, true));

        assertEquals(0.001, idle.snapshot().fuelLiters, 0.0001);
    }

    @Test
    public void staleConsumptionInvalidatesOnlyConsumptionMetric() {
        TripAccumulator accumulator = TripAccumulator.start(1_000L);
        accumulator.update(telemetry(1_000L, 60, 10.0, true, 10.0f, true));
        accumulator.update(telemetry(2_000L, 60, 10.2, true, 0.0f, false));

        TripSummary summary = accumulator.finish(3_000L);
        assertTrue(summary.distanceValid);
        assertFalse(summary.consumptionValid);
        assertTrue(summary.durationValid);
    }

    @Test
    public void zeroDistanceCannotProduceAverageConsumption() {
        TripAccumulator accumulator = TripAccumulator.start(1_000L);
        accumulator.update(telemetry(1_000L, 0, 10.0, true, 1.0f, true));
        accumulator.update(telemetry(2_000L, 0, 10.0, true, 1.0f, true));

        TripSummary summary = accumulator.finish(3_000L);
        assertTrue(summary.distanceValid);
        assertEquals(0.0, summary.distanceKm, 0.0);
        assertFalse(summary.consumptionValid);
    }

    @Test
    public void durationUsesConfirmedMonotonicBoundaries() {
        TripAccumulator accumulator = TripAccumulator.start(10_000L);

        TripSummary summary = accumulator.finish(25_500L);

        assertTrue(summary.durationValid);
        assertEquals(15_500L, summary.durationMs);
    }

    @Test
    public void snapshotRestoreContinuesWithoutDoubleCounting() {
        TripAccumulator original = TripAccumulator.start(1_000L);
        original.update(telemetry(1_000L, 60, 10.0, true, 10.0f, true));
        original.update(telemetry(2_000L, 60, 10.2, true, 10.0f, true));

        TripAccumulator restored = TripAccumulator.restore(original.snapshot());
        restored.update(telemetry(3_000L, 60, 10.5, true, 10.0f, true));

        assertEquals(0.5, restored.finish(4_000L).distanceKm, 0.0001);
    }

    private static TripTelemetry telemetry(
            long capturedAtMs,
            int speedKph,
            double journeyOdometerKm,
            boolean journeyOdometerValid,
            float instantFuelConsumption,
            boolean instantFuelConsumptionValid) {
        return new TripTelemetry(
                capturedAtMs,
                speedKph,
                journeyOdometerKm,
                journeyOdometerValid,
                instantFuelConsumption,
                instantFuelConsumptionValid);
    }
}
