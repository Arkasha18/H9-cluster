package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.junit.Test;

public final class FuelTankCapacityTest {
    @Test
    public void defaultAndUnsupportedCapacitiesUseEightyLiters() {
        assertEquals(80, FuelTankCapacity.DEFAULT_LITERS);
        assertEquals(60, FuelTankCapacity.SMALL_LITERS);
        assertEquals(80, ClusterState.empty().fuelTankCapacityLiters);
        assertEquals(60, FuelTankCapacity.normalize(60));
        for (int capacity : new int[] {80, 0, -60, 59, 61, 100, Integer.MAX_VALUE}) {
            assertEquals(80, FuelTankCapacity.normalize(capacity));
        }
    }

    @Test
    public void emptyHalfAndFullKeepTheSameFractionForBothTankSizes() {
        for (float baselineLiters : new float[] {0.0f, 40.0f, 80.0f}) {
            ClusterState source = state(baselineLiters);
            for (int capacity : new int[] {60, 80}) {
                ClusterState display = source.withFuelTankCapacity(capacity);
                assertEquals(capacity, display.fuelTankCapacityLiters);
                assertEquals(baselineLiters / 80.0f * capacity, display.fuelLiters, 0.0f);
                assertEquals(baselineLiters / 80.0f,
                        display.fuelLiters / display.fuelTankCapacityLiters, 0.0f);
            }
            assertEquals(80, source.fuelTankCapacityLiters);
            assertEquals(baselineLiters, source.fuelLiters, 0.0f);
        }
    }

    @Test
    public void repeatedPreviewAndRoundTripDoNotScaleFuelTwice() {
        ClusterState source = state(47.0f);
        ClusterState small = source.withFuelTankCapacity(60);
        assertEquals(35.25f, small.fuelLiters, 0.00001f);
        assertSame(small, small.withFuelTankCapacity(60));
        assertSame(source, source.withFuelTankCapacity(80));

        ClusterState restored = small.withFuelTankCapacity(80);
        assertEquals(47.0f, restored.fuelLiters, 0.00001f);
        assertEquals(80, restored.fuelTankCapacityLiters);
        assertEquals(35.25f, restored.withFuelTankCapacity(60).fuelLiters, 0.00001f);
        assertEquals(47.0f, small.withFuelTankCapacity(-1).fuelLiters, 0.00001f);
    }

    @Test
    public void projectionPreservesEveryOtherTelemetryValueAndTimestamp() throws Exception {
        ClusterState source = state(40.0f);
        ClusterState small = source.withFuelTankCapacity(60);
        // Include new fields automatically: a fuel projection must not drop telemetry.
        for (Field field : ClusterState.class.getFields()) {
            if (!field.getName().equals("fuelLiters")
                    && !field.getName().equals("fuelTankCapacityLiters")) {
                assertEquals(field.getName(), field.get(source), field.get(small));
            }
        }
        assertEquals(421, small.rangeKm);
        assertEquals(12.6f, small.instantFuelConsumption, 0.0f);
        assertEquals(14.8f, small.consumptionLitersPer100Km, 0.0f);
        assertEquals(15.3f, small.journeyAverageFuelConsumption, 0.0f);
    }

    @Test
    public void missingFuelRemainsMissingForEitherTankSize() {
        ClusterState small = state(Float.NaN).withFuelTankCapacity(60);
        assertTrue(Float.isNaN(small.fuelLiters));
        assertTrue(Float.isNaN(small.withFuelTankCapacity(80).fuelLiters));
    }

    @Test
    public void movingAndStoppedDemoSnapshotsUseTheSameProjection() {
        DemoScenario scenario = new DemoScenario();
        for (long elapsedMs : new long[] {5_000L, 12_000L, 15_000L}) {
            ClusterState moving = scenario.snapshot(elapsedMs, 20_000L);
            ClusterState stopped = scenario.stoppedSnapshot(elapsedMs, 20_000L, false);
            assertEquals(moving.fuelLiters * 0.75f,
                    moving.withFuelTankCapacity(60).fuelLiters, 0.00001f);
            assertEquals(moving.withFuelTankCapacity(60).fuelLiters,
                    stopped.withFuelTankCapacity(60).fuelLiters, 0.00001f);
            assertEquals(moving.rangeKm, moving.withFuelTankCapacity(60).rangeKm);
        }
    }

    private static ClusterState state(float fuelLiters) {
        return new ClusterState(
                86,
                2_400,
                8,
                GearSelector.DRIVE,
                92,
                78.0f,
                fuelLiters,
                421,
                28_642.25,
                42.3f,
                167.8f,
                2.35f,
                2.37f,
                2.42f,
                2.40f,
                12.6f,
                14.8f,
                15.3f,
                13.8f,
                -12.5f,
                150.0f,
                84.5f,
                87.5f,
                84.95f,
                87.05f,
                220.0f,
                1_001L,
                1_002L,
                1_003L,
                1_004L,
                1_005L,
                "SPORT");
    }
}
