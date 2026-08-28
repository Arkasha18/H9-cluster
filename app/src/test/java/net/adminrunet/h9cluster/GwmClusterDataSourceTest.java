package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.adminrunet.h9cluster.trip.EngineRunDetector;

import org.junit.Test;

public final class GwmClusterDataSourceTest {
    @Test
    public void invalidBinderRpmDoesNotReplaceLastFdbusSample() {
        GwmClusterDataSource.RpmSample sample =
                GwmClusterDataSource.selectRpmSample(
                        2_000L,
                        748,
                        1_000L,
                        "-1",
                        1_900L,
                        748,
                        1_000L);

        assertEquals(748, sample.rpm);
        assertEquals(1_000L, sample.updatedAtMs);
    }

    @Test
    public void invalidBinderFallbackCannotStopRunningTripAfterFdbusDrop() {
        EngineRunDetector detector = new EngineRunDetector(true);

        GwmClusterDataSource.RpmSample first =
                GwmClusterDataSource.selectRpmSample(
                        2_000L,
                        748,
                        1_000L,
                        "-1",
                        1_900L,
                        748,
                        1_000L);
        assertEquals(
                EngineRunDetector.Event.NONE,
                detector.update(first.rpm, 0, first.updatedAtMs, 2_000L));

        GwmClusterDataSource.RpmSample later =
                GwmClusterDataSource.selectRpmSample(
                        3_500L,
                        748,
                        1_000L,
                        "-1",
                        1_900L,
                        first.rpm,
                        first.updatedAtMs);
        assertEquals(
                EngineRunDetector.Event.NONE,
                detector.update(later.rpm, 0, later.updatedAtMs, 3_500L));
    }

    @Test
    public void newerValidBinderZeroCanConfirmShutdown() {
        GwmClusterDataSource.RpmSample sample =
                GwmClusterDataSource.selectRpmSample(
                        2_000L,
                        748,
                        1_000L,
                        "0",
                        1_900L,
                        748,
                        1_000L);

        assertEquals(0, sample.rpm);
        assertEquals(1_900L, sample.updatedAtMs);
    }

    @Test
    public void oldBinderZeroDoesNotReplaceNewerFdbusSample() {
        GwmClusterDataSource.RpmSample sample =
                GwmClusterDataSource.selectRpmSample(
                        2_000L,
                        748,
                        1_000L,
                        "0",
                        900L,
                        748,
                        1_000L);

        assertEquals(748, sample.rpm);
        assertEquals(1_000L, sample.updatedAtMs);
    }

    @Test
    public void invalidRpmValuesAreUnavailableInsteadOfClampedToZero() {
        assertEquals(-1, GwmClusterDataSource.engineRpm("-1"));
        assertEquals(-1, GwmClusterDataSource.engineRpm("NaN"));
        assertEquals(-1, GwmClusterDataSource.engineRpm("Infinity"));
        assertEquals(-1, GwmClusterDataSource.engineRpm("waiting"));
    }

    @Test
    public void invalidJourneyOdometerKeepsDisplayValueButClearsFreshness() {
        String[] invalidValues = {null, "NaN", "Infinity", "-1", "waiting"};
        for (String invalidValue : invalidValues) {
            GwmClusterDataSource.JourneyOdometerSample sample =
                    GwmClusterDataSource.selectJourneyOdometerSample(
                            invalidValue,
                            9_000L,
                            42.75f);

            assertEquals(42.75f, sample.displayValue, 0.0001f);
            assertEquals(0L, sample.updatedAtMs);
        }
    }

    @Test
    public void validJourneyOdometerPublishesItsOwnFreshness() {
        GwmClusterDataSource.JourneyOdometerSample sample =
                GwmClusterDataSource.selectJourneyOdometerSample(
                        "26.5",
                        9_000L,
                        42.75f);

        assertEquals(26.5f, sample.displayValue, 0.0001f);
        assertEquals(9_000L, sample.updatedAtMs);
    }

    @Test
    public void journeyAverageConsumptionComesOnlyFromIndicatorA() {
        assertEquals(
                8.4f,
                GwmClusterDataSource.journeyAverageConsumption("8.4"),
                0.0001f);
    }

    @Test
    public void unavailableIndicatorAIsNeverReplacedByIndicatorB() {
        assertTrue(Float.isNaN(
                GwmClusterDataSource.journeyAverageConsumption(null)));
        assertTrue(Float.isNaN(
                GwmClusterDataSource.journeyAverageConsumption("")));

        // The cluster gauge may still fall back to B, the trip may not.
        assertEquals(
                11.2f,
                GwmClusterDataSource.clusterConsumption(null, "11.2", 7.0f),
                0.0001f);
        assertTrue(Float.isNaN(
                GwmClusterDataSource.journeyAverageConsumption(null)));
    }

    @Test
    public void clusterConsumptionKeepsPreviousValueWhenBothIndicatorsAreGone() {
        assertEquals(
                7.0f,
                GwmClusterDataSource.clusterConsumption(null, null, 7.0f),
                0.0001f);
    }

    @Test
    public void instantConsumptionAcceptsOnlyAvailableNonNegativeValues() {
        assertEquals(
                14.7f,
                GwmClusterDataSource.instantFuelConsumption("14.7"),
                0.0001f);
        assertTrue(Float.isNaN(
                GwmClusterDataSource.instantFuelConsumption(null)));
        assertTrue(Float.isNaN(
                GwmClusterDataSource.instantFuelConsumption("-1")));
        assertTrue(Float.isNaN(
                GwmClusterDataSource.instantFuelConsumption("waiting")));
    }

    @Test
    public void forwardGearsOneThroughSevenRemainUnchanged() {
        for (int gear = 1; gear <= 7; gear++) {
            assertEquals(gear, GwmClusterDataSource.normalizeCurrentGear(gear));
        }
    }

    @Test
    public void vehicleCodeNineIsDisplayedAsEighthGear() {
        assertEquals(8, GwmClusterDataSource.normalizeCurrentGear(9));
    }

    @Test
    public void reverseGearCodeEightIsHidden() {
        assertEquals(0, GwmClusterDataSource.normalizeCurrentGear(8));
    }

    @Test
    public void invalidGearCodesAreHidden() {
        assertEquals(0, GwmClusterDataSource.normalizeCurrentGear(-1));
        assertEquals(0, GwmClusterDataSource.normalizeCurrentGear(0));
        assertEquals(0, GwmClusterDataSource.normalizeCurrentGear(10));
        assertEquals(0, GwmClusterDataSource.normalizeCurrentGear(15));
    }
}
