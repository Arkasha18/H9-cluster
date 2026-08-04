package net.adminrunet.h9cluster.trip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TripSummaryFormatterTest {
    @Test
    public void exposesDurationNumbersSeparatelyFromUnits() {
        TripSummaryFormatter.DurationParts zero =
                TripSummaryFormatter.durationParts(new TripSummary(
                        0.0,
                        false,
                        0.0,
                        false,
                        0L,
                        true));
        assertTrue(zero.valid);
        assertEquals(0L, zero.hours);
        assertEquals(0L, zero.minutes);

        TripSummaryFormatter.DurationParts longTrip =
                TripSummaryFormatter.durationParts(new TripSummary(
                        0.0,
                        false,
                        0.0,
                        false,
                        4_680_000L,
                        true));
        assertTrue(longTrip.valid);
        assertEquals(1L, longTrip.hours);
        assertEquals(18L, longTrip.minutes);

        TripSummaryFormatter.DurationParts invalid =
                TripSummaryFormatter.durationParts(new TripSummary(
                        0.0,
                        false,
                        0.0,
                        false,
                        0L,
                        false));
        assertFalse(invalid.valid);
    }

    @Test
    public void formatsValidMetricsForTheCluster() {
        TripSummary summary = new TripSummary(
                42.74,
                true,
                11.36,
                true,
                4_680_000L,
                true);

        assertEquals("42.7", TripSummaryFormatter.distance(summary));
        assertEquals("11.4", TripSummaryFormatter.consumption(summary));
        assertEquals("1 ч 18 мин", TripSummaryFormatter.duration(summary));
    }

    @Test
    public void formatsShortAndZeroDurationsWithoutAnHourPlaceholder() {
        assertEquals(
                "18 мин",
                TripSummaryFormatter.duration(new TripSummary(
                        0.0,
                        false,
                        0.0,
                        false,
                        1_080_000L,
                        true)));
        assertEquals(
                "0 мин",
                TripSummaryFormatter.duration(new TripSummary(
                        0.0,
                        false,
                        0.0,
                        false,
                        0L,
                        true)));
    }

    @Test
    public void formatsFuelUsedWithIndependentValidity() {
        assertEquals(
                "0.03",
                TripSummaryFormatter.fuelUsed(new TripSummary(
                        0.0,
                        false,
                        0.0,
                        false,
                        0L,
                        false,
                        0.026,
                        true)));
        assertEquals(
                "—",
                TripSummaryFormatter.fuelUsed(new TripSummary(
                        0.0,
                        false,
                        0.0,
                        false,
                        0L,
                        false,
                        Double.NaN,
                        false)));
    }

    @Test
    public void invalidMetricsRenderOnlyAnEmDash() {
        TripSummary summary = new TripSummary(
                Double.NaN,
                false,
                Double.NaN,
                false,
                0L,
                false);

        assertEquals("—", TripSummaryFormatter.distance(summary));
        assertEquals("—", TripSummaryFormatter.consumption(summary));
        assertEquals("—", TripSummaryFormatter.duration(summary));
    }
}
