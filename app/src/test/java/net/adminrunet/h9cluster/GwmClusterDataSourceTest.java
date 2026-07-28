package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class GwmClusterDataSourceTest {
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
