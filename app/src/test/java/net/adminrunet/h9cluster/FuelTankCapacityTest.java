package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.junit.Test;

public final class FuelTankCapacityTest {
    @Test
    public void acceptsNumericCapacitiesAndDefaultsInvalidValuesToEightyLiters() {
        assertEquals(80, FuelTankCapacity.DEFAULT_LITERS);
        assertEquals(20, FuelTankCapacity.MIN_LITERS);
        assertEquals(200, FuelTankCapacity.MAX_LITERS);
        assertEquals(80, ClusterState.empty().fuelTankCapacityLiters);
        for (int capacity : new int[] {20, 42, 55, 60, 80, 100, 200}) {
            assertEquals(capacity, FuelTankCapacity.normalize(capacity));
        }
        for (int capacity : new int[] {0, -60, 19, 201, Integer.MAX_VALUE}) {
            assertEquals(80, FuelTankCapacity.normalize(capacity));
        }
        assertEquals(55, FuelTankCapacity.parse(" 55 "));
        assertEquals(200, FuelTankCapacity.parse("200"));
        assertEquals(-1, FuelTankCapacity.parse(""));
        assertEquals(-1, FuelTankCapacity.parse("55.5"));
        assertEquals(-1, FuelTankCapacity.parse("201"));
        assertEquals(-1, FuelTankCapacity.parse(null));
    }

    @Test
    public void emptyHalfAndFullKeepTheSameFractionForEveryTankSize() {
        for (float baselineLiters : new float[] {0.0f, 40.0f, 80.0f}) {
            ClusterState source = state(baselineLiters);
            for (int capacity : new int[] {20, 55, 60, 80, 100, 200}) {
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
        ClusterState small = source.withFuelTankCapacity(55);
        assertEquals(32.3125f, small.fuelLiters, 0.00001f);
        assertSame(small, small.withFuelTankCapacity(55));
        assertSame(source, source.withFuelTankCapacity(80));

        ClusterState restored = small.withFuelTankCapacity(80);
        assertEquals(47.0f, restored.fuelLiters, 0.00001f);
        assertEquals(80, restored.fuelTankCapacityLiters);
        assertEquals(32.3125f, restored.withFuelTankCapacity(55).fuelLiters, 0.00001f);
        assertEquals(47.0f, small.withFuelTankCapacity(-1).fuelLiters, 0.00001f);
    }

    @Test
    public void projectionPreservesEveryOtherTelemetryValueAndTimestamp() throws Exception {
        ClusterState source = state(40.0f).withTboxVehicleState(
                ClusterState.ENGINE_STATE_STOPPED,
                2_001L,
                true,
                true,
                2_002L);
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
        ClusterState small = state(Float.NaN).withFuelTankCapacity(55);
        assertTrue(Float.isNaN(small.fuelLiters));
        assertTrue(Float.isNaN(small.withFuelTankCapacity(80).fuelLiters));
    }

    @Test
    public void movingAndStoppedDemoSnapshotsUseTheSameProjection() {
        DemoScenario scenario = new DemoScenario();
        for (long elapsedMs : new long[] {5_000L, 12_000L, 15_000L}) {
            ClusterState moving = scenario.snapshot(elapsedMs, 20_000L);
            ClusterState stopped = scenario.stoppedSnapshot(elapsedMs, 20_000L, false);
            assertEquals(moving.fuelLiters * 55.0f / 80.0f,
                    moving.withFuelTankCapacity(55).fuelLiters, 0.00001f);
            assertEquals(moving.withFuelTankCapacity(55).fuelLiters,
                    stopped.withFuelTankCapacity(55).fuelLiters, 0.00001f);
            assertEquals(moving.rangeKm, moving.withFuelTankCapacity(55).rangeKm);
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
