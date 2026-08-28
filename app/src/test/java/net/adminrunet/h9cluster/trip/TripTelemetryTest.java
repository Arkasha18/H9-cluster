package net.adminrunet.h9cluster.trip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.adminrunet.h9cluster.ClusterState;

import org.junit.Test;

public final class TripTelemetryTest {
    @Test
    public void acceptsFreshFiniteTripValues() {
        TripTelemetry telemetry = TripTelemetry.from(
                state(62, 42.75f, 9_000L, 8.4f, 9_000L, 9.0f),
                10_000L);

        assertEquals(62, telemetry.speedKph);
        assertTrue(telemetry.journeyOdometerValid);
        assertEquals(42.75, telemetry.journeyOdometerKm, 0.0001);
        assertTrue(telemetry.averageFuelConsumptionValid);
        assertEquals(8.4f, telemetry.averageFuelConsumption, 0.0001f);
    }

    @Test
    public void usesJourneyAverageAndNeverTheClusterFallbackValue() {
        TripTelemetry telemetry = TripTelemetry.from(
                state(62, 42.75f, 9_000L, 8.4f, 9_000L, 9.0f),
                10_000L);

        assertEquals(8.4f, telemetry.averageFuelConsumption, 0.0001f);
    }

    @Test
    public void rejectsUnavailableJourneyAverageWithoutSubstitutingCluster() {
        TripTelemetry nanAverage = TripTelemetry.from(
                state(62, 42.75f, 9_000L, Float.NaN, 9_000L, 9.0f),
                10_000L);
        assertFalse(nanAverage.averageFuelConsumptionValid);
        assertTrue(nanAverage.journeyOdometerValid);

        TripTelemetry neverPublished = TripTelemetry.from(
                state(62, 42.75f, 9_000L, 8.4f, 0L, 9.0f),
                10_000L);
        assertFalse(neverPublished.averageFuelConsumptionValid);

        TripTelemetry nonPositive = TripTelemetry.from(
                state(62, 42.75f, 9_000L, 0.0f, 9_000L, 9.0f),
                10_000L);
        assertFalse(nonPositive.averageFuelConsumptionValid);
    }

    @Test
    public void keepsObservedValuesValidWhileTheyAreRepublishedOnChange() {
        TripTelemetry telemetry = TripTelemetry.from(
                state(0, 42.75f, 1_000L, 8.4f, 1_000L, 9.0f),
                20_000L);

        assertTrue(telemetry.journeyOdometerValid);
        assertTrue(telemetry.averageFuelConsumptionValid);
    }

    @Test
    public void rejectsValuesThatWentSilentBeyondTheStalenessCeiling() {
        TripTelemetry telemetry = TripTelemetry.from(
                state(0, 42.75f, 1_000L, 8.4f, 1_000L, 9.0f),
                61_001L);

        assertFalse(telemetry.journeyOdometerValid);
        assertFalse(telemetry.averageFuelConsumptionValid);
    }

    @Test
    public void rejectsNeverObservedJourneyOdometer() {
        TripTelemetry telemetry = TripTelemetry.from(
                state(0, 42.75f, 0L, 8.4f, 9_000L, 9.0f),
                10_000L);

        assertFalse(telemetry.journeyOdometerValid);
        assertTrue(telemetry.averageFuelConsumptionValid);
    }

    @Test
    public void rejectsFutureAndNonFiniteValuesIndependently() {
        TripTelemetry futureFuel = TripTelemetry.from(
                state(0, 42.75f, 9_000L, 8.4f, 10_001L, 9.0f),
                10_000L);
        assertTrue(futureFuel.journeyOdometerValid);
        assertFalse(futureFuel.averageFuelConsumptionValid);

        TripTelemetry nonFinite = TripTelemetry.from(
                state(
                        0,
                        Float.NaN,
                        9_000L,
                        Float.POSITIVE_INFINITY,
                        9_000L,
                        9.0f),
                10_000L);
        assertFalse(nonFinite.journeyOdometerValid);
        assertFalse(nonFinite.averageFuelConsumptionValid);
    }

    private static ClusterState state(
            int speedKph,
            float journeyOdometer,
            long journeyUpdatedAtMs,
            float journeyAverageFuel,
            long journeyAverageFuelUpdatedAtMs,
            float clusterConsumption) {
        return new ClusterState(
                speedKph,
                800,
                1,
                "D",
                80,
                Float.NaN,
                50.0f,
                500,
                20_000.0,
                journeyOdometer,
                100.0f,
                2.3f,
                2.3f,
                2.3f,
                2.3f,
                clusterConsumption,
                clusterConsumption,
                journeyAverageFuel,
                14.0f,
                20.0f,
                0.0f,
                speedKph,
                speedKph,
                speedKph,
                speedKph,
                100.0f,
                10_000L,
                journeyAverageFuelUpdatedAtMs,
                journeyUpdatedAtMs,
                10_000L,
                10_000L,
                "NORMAL");
    }
}
