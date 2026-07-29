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
        assertEquals(8.5f, normalized.lastAverageFuelConsumption, 0.0f);
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
                8.5f,
                true);

        TripSession normalized =
                TripSessionNormalizer.normalize(malformed, 20_000L);

        assertNotNull(normalized);
        assertFalse(normalized.lastJourneyOdometerValid);
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
                8.5f,
                true);
    }

    private static TripSession withActive(TripSession source, boolean active) {
        return copy(
                source,
                active,
                source.lastUpdatedAtMs,
                source.distanceKm,
                source.distanceValid);
    }

    private static TripSession withLastUpdated(
            TripSession source,
            long lastUpdatedAtMs) {
        return copy(
                source,
                source.active,
                lastUpdatedAtMs,
                source.distanceKm,
                source.distanceValid);
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
                distanceValid);
    }

    private static TripSession copy(
            TripSession source,
            boolean active,
            long lastUpdatedAtMs,
            double distanceKm,
            boolean distanceValid) {
        return new TripSession(
                active,
                source.startedAtMs,
                lastUpdatedAtMs,
                distanceKm,
                source.lastJourneyOdometerKm,
                source.lastJourneyOdometerValid,
                distanceValid,
                source.lastAverageFuelConsumption,
                source.lastAverageFuelConsumptionValid);
    }
}
