package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GwmClusterDataSourceTest {
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
    public void forwardGearsOneThroughEightRemainUnchanged() {
        for (int gear = 1; gear <= 8; gear++) {
            assertEquals(gear, GwmClusterDataSource.normalizeCurrentGear(gear));
        }
    }

    @Test
    public void vehicleCodeNineIsDisplayedAsEighthGear() {
        assertEquals(8, GwmClusterDataSource.normalizeCurrentGear(9));
    }

    @Test
    public void invalidGearCodesAreHidden() {
        assertEquals(0, GwmClusterDataSource.normalizeCurrentGear(-1));
        assertEquals(0, GwmClusterDataSource.normalizeCurrentGear(0));
        assertEquals(0, GwmClusterDataSource.normalizeCurrentGear(10));
        assertEquals(0, GwmClusterDataSource.normalizeCurrentGear(15));
    }
}
