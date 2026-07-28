package net.adminrunet.h9cluster.trip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TripTelemetryConverterTest {
    @Test
    public void preservesConfirmedJourneyOdometerScale() {
        assertEquals(
                12.3,
                TripTelemetryConverter.journeyOdometerKm(12.3f),
                0.0001);
    }

    @Test
    public void integratesMovingConsumptionAsLitresPerHundredKilometres() {
        assertEquals(
                0.01,
                TripTelemetryConverter.fuelLitersForInterval(
                        10.0f,
                        60,
                        0.1,
                        6_000L),
                0.0001);
    }

    @Test
    public void integratesIdleConsumptionAsLitresPerHour() {
        assertEquals(
                0.001,
                TripTelemetryConverter.fuelLitersForInterval(
                        1.0f,
                        0,
                        0.0,
                        3_600L),
                0.0001);
    }

    @Test
    public void rejectsNonFiniteNegativeAndIncompleteIntervals() {
        assertTrue(Double.isNaN(
                TripTelemetryConverter.journeyOdometerKm(Float.NaN)));
        assertTrue(Double.isNaN(
                TripTelemetryConverter.journeyOdometerKm(-1.0f)));
        assertTrue(Double.isNaN(
                TripTelemetryConverter.fuelLitersForInterval(
                        Float.POSITIVE_INFINITY,
                        60,
                        0.1,
                        1_000L)));
        assertTrue(Double.isNaN(
                TripTelemetryConverter.fuelLitersForInterval(
                        10.0f,
                        60,
                        -0.1,
                        1_000L)));
        assertTrue(Double.isNaN(
                TripTelemetryConverter.fuelLitersForInterval(
                        10.0f,
                        60,
                        0.1,
                        -1L)));
    }
}
