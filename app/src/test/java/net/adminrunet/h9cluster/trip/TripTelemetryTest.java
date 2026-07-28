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
                state(62, 42.75f, 9_000L, 8.4f, 9_000L),
                10_000L);

        assertEquals(62, telemetry.speedKph);
        assertTrue(telemetry.journeyOdometerValid);
        assertEquals(42.75, telemetry.journeyOdometerKm, 0.0001);
        assertTrue(telemetry.instantFuelConsumptionValid);
        assertEquals(8.4f, telemetry.instantFuelConsumption, 0.0001f);
    }

    @Test
    public void rejectsStaleFutureAndNonFiniteValuesIndependently() {
        TripTelemetry staleJourney = TripTelemetry.from(
                state(0, 42.75f, 8_499L, 8.4f, 9_000L),
                10_000L);
        assertFalse(staleJourney.journeyOdometerValid);
        assertTrue(staleJourney.instantFuelConsumptionValid);

        TripTelemetry futureFuel = TripTelemetry.from(
                state(0, 42.75f, 9_000L, 8.4f, 10_001L),
                10_000L);
        assertTrue(futureFuel.journeyOdometerValid);
        assertFalse(futureFuel.instantFuelConsumptionValid);

        TripTelemetry nonFinite = TripTelemetry.from(
                state(0, Float.NaN, 9_000L, Float.POSITIVE_INFINITY, 9_000L),
                10_000L);
        assertFalse(nonFinite.journeyOdometerValid);
        assertFalse(nonFinite.instantFuelConsumptionValid);
    }

    private static ClusterState state(
            int speedKph,
            float journeyOdometer,
            long journeyUpdatedAtMs,
            float instantFuel,
            long fuelUpdatedAtMs) {
        return new ClusterState(
                speedKph,
                800,
                1,
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
                9.0f,
                instantFuel,
                14.0f,
                20.0f,
                0.0f,
                speedKph,
                speedKph,
                speedKph,
                speedKph,
                100.0f,
                10_000L,
                fuelUpdatedAtMs,
                journeyUpdatedAtMs,
                10_000L,
                10_000L,
                "NORMAL");
    }
}
