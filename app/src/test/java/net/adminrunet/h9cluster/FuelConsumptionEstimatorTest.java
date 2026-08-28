package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FuelConsumptionEstimatorTest {
    @Test
    public void derivesLitersPerHourFromCounterDelta() {
        FuelConsumptionEstimator estimator = new FuelConsumptionEstimator();
        assertTrue(Float.isNaN(estimator.update(1000, 1_000L)));

        assertEquals(1.5624f, estimator.update(1217, 2_000L), 0.0001f);
    }

    @Test
    public void handlesUnsignedCounterWrap() {
        FuelConsumptionEstimator estimator = new FuelConsumptionEstimator();
        assertTrue(Float.isNaN(estimator.update(65_500, 1_000L)));

        assertEquals(0.5184f, estimator.update(36, 2_000L), 0.0001f);
    }

    @Test
    public void rejectsImplausibleResetSpike() {
        FuelConsumptionEstimator estimator = new FuelConsumptionEstimator();
        estimator.update(40_000, 1_000L);

        assertTrue(Float.isNaN(estimator.update(0, 1_020L)));
        assertTrue(Float.isNaN(estimator.update(10, 1_100L)));
    }

    @Test
    public void convertsFlowForStoppedAndMovingDisplayUnits() {
        assertEquals(
                1.2f,
                FuelConsumptionEstimator.forClusterDisplay(1.2f, 0),
                0.0f);
        assertEquals(
                8.0f,
                FuelConsumptionEstimator.forClusterDisplay(4.0f, 50),
                0.0f);
        assertTrue(Float.isNaN(
                FuelConsumptionEstimator.forClusterDisplay(Float.NaN, 50)));
    }

    @Test
    public void decodesEcm2FuelCounterAfterChecksumByte() {
        byte[] frame = new byte[] {
                0x00,
                (byte) 0xff,
                (byte) 0xf8,
                0x00,
                0x0a,
                0x1e,
                0x02,
                0x40
        };

        assertEquals(65_528, FdbusRpmReader.decodeFuelConsumptionCounter(frame));
        assertEquals(-1, FdbusRpmReader.decodeFuelConsumptionCounter(null));
        assertEquals(-1, FdbusRpmReader.decodeFuelConsumptionCounter(new byte[2]));
    }
}
