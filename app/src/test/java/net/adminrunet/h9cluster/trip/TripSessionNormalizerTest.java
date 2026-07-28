package net.adminrunet.h9cluster.trip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TripSessionNormalizerTest {
    @Test
    public void preservesValidSameBootSession() {
        TripSession normalized =
                TripSessionNormalizer.normalize(validSession(), 20_000L);

        assertNotNull(normalized);
        assertTrue(normalized.active);
        assertEquals(10_000L, normalized.startedAtMs);
        assertEquals(15_000L, normalized.lastUpdatedAtMs);
        assertEquals(2.5, normalized.distanceKm, 0.0);
        assertEquals(0.25, normalized.fuelLiters, 0.0);
    }

    @Test
    public void discardsInactiveAndCrossBootSessions() {
        TripSession valid = validSession();
        assertNull(TripSessionNormalizer.normalize(withActive(valid, false), 20_000L));
        assertNull(TripSessionNormalizer.normalize(valid, 9_999L));
        assertNull(TripSessionNormalizer.normalize(
                withLastUpdated(valid, 21_000L),
                20_000L));
    }

    @Test
    public void discardsImpossibleTimestampOrdering() {
        TripSession valid = validSession();

        assertNull(TripSessionNormalizer.normalize(
                withLastUpdated(valid, 9_999L),
                20_000L));
    }

    @Test
    public void invalidDistanceNeverBecomesZeroValid() {
        TripSession normalized = TripSessionNormalizer.normalize(
                withDistance(validSession(), -2.0, true),
                20_000L);

        assertNotNull(normalized);
        assertEquals(0.0, normalized.distanceKm, 0.0);
        assertFalse(normalized.distanceValid);
        assertFalse(normalized.fuelReliable);
    }

    @Test
    public void invalidFuelNeverBecomesZeroValid() {
        TripSession normalized = TripSessionNormalizer.normalize(
                withFuel(validSession(), Double.POSITIVE_INFINITY, true, true),
                20_000L);

        assertNotNull(normalized);
        assertEquals(0.0, normalized.fuelLiters, 0.0);
        assertFalse(normalized.fuelReliable);
        assertFalse(normalized.hasFuelInterval);
    }

    @Test
    public void clearsMalformedBaselinesWithoutDiscardingRunningSession() {
        TripSession malformed = new TripSession(
                true,
                10_000L,
                15_000L,
                2.5,
                Double.NaN,
                true,
                true,
                0.25,
                Float.NaN,
                true,
                50,
                true,
                true);

        TripSession normalized =
                TripSessionNormalizer.normalize(malformed, 20_000L);

        assertNotNull(normalized);
        assertFalse(normalized.lastJourneyOdometerValid);
        assertFalse(normalized.lastFuelConsumptionValid);
        assertFalse(normalized.fuelReliable);
    }

    private static TripSession validSession() {
        return new TripSession(
                true,
                10_000L,
                15_000L,
                2.5,
                42.5,
                true,
                true,
                0.25,
                8.5f,
                true,
                50,
                true,
                true);
    }

    private static TripSession withActive(TripSession source, boolean active) {
        return copy(
                source,
                active,
                source.lastUpdatedAtMs,
                source.distanceKm,
                source.distanceValid,
                source.fuelLiters,
                source.fuelReliable,
                source.hasFuelInterval);
    }

    private static TripSession withLastUpdated(
            TripSession source,
            long lastUpdatedAtMs) {
        return copy(
                source,
                source.active,
                lastUpdatedAtMs,
                source.distanceKm,
                source.distanceValid,
                source.fuelLiters,
                source.fuelReliable,
                source.hasFuelInterval);
    }

    private static TripSession withDistance(
            TripSession source,
            double distanceKm,
            boolean distanceValid) {
        return copy(
                source,
                source.active,
                source.lastUpdatedAtMs,
                distanceKm,
                distanceValid,
                source.fuelLiters,
                source.fuelReliable,
                source.hasFuelInterval);
    }

    private static TripSession withFuel(
            TripSession source,
            double fuelLiters,
            boolean fuelReliable,
            boolean hasFuelInterval) {
        return copy(
                source,
                source.active,
                source.lastUpdatedAtMs,
                source.distanceKm,
                source.distanceValid,
                fuelLiters,
                fuelReliable,
                hasFuelInterval);
    }

    private static TripSession copy(
            TripSession source,
            boolean active,
            long lastUpdatedAtMs,
            double distanceKm,
            boolean distanceValid,
            double fuelLiters,
            boolean fuelReliable,
            boolean hasFuelInterval) {
        return new TripSession(
                active,
                source.startedAtMs,
                lastUpdatedAtMs,
                distanceKm,
                source.lastJourneyOdometerKm,
                source.lastJourneyOdometerValid,
                distanceValid,
                fuelLiters,
                source.lastFuelConsumption,
                source.lastFuelConsumptionValid,
                source.lastSpeedKph,
                fuelReliable,
                hasFuelInterval);
    }
}
