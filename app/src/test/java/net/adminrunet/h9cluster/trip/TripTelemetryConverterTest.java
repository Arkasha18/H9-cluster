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
    public void rejectsNonFiniteAndNegativeJourneyValues() {
        assertTrue(Double.isNaN(
                TripTelemetryConverter.journeyOdometerKm(Float.NaN)));
        assertTrue(Double.isNaN(
                TripTelemetryConverter.journeyOdometerKm(-1.0f)));
    }
}
