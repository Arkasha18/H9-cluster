package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DemoScenarioTest {
    @Test
    public void snapshotsStayInsidePlausibleRendererRanges() {
        DemoScenario scenario = new DemoScenario();

        for (long elapsed = 0L;
                elapsed <= DemoScenario.CYCLE_MS;
                elapsed += 250L) {
            ClusterState state = scenario.snapshot(elapsed, 50_000L + elapsed);

            assertTrue(state.speedKph >= 0 && state.speedKph <= 120);
            assertTrue(state.rpm >= 700 && state.rpm <= 4_500);
            assertTrue(state.currentGear >= 0 && state.currentGear <= 6);
            assertTrue(state.coolantC >= 70 && state.coolantC <= 125);
            assertTrue(state.transmissionTemperatureC >= 55.0f);
            assertTrue(state.transmissionTemperatureC <= 125.0f);
            assertTrue(state.fuelLiters >= 0.0f && state.fuelLiters <= 80.0f);
            assertTrue(state.rangeKm >= 0);
            assertTrue(state.tyreFrontLeftBar >= 1.8f);
            assertTrue(state.tyreFrontRightBar >= 2.0f);
            assertTrue(state.tyreRearLeftBar >= 2.0f);
            assertTrue(state.tyreRearRightBar >= 2.0f);
            assertTrue(state.consumptionLitersPer100Km >= 0.0f);
            assertTrue(state.voltage >= 11.5f && state.voltage <= 15.0f);
            assertTrue(state.outsideTemperatureC >= -50.0f);
            assertTrue(state.outsideTemperatureC <= 60.0f);
            assertTrue(state.steeringAngleDeg >= -360.0f);
            assertTrue(state.steeringAngleDeg <= 360.0f);
            assertTrue(state.wheelFrontLeftKph >= 0.0f);
            assertTrue(state.wheelFrontRightKph >= 0.0f);
            assertTrue(state.wheelRearLeftKph >= 0.0f);
            assertTrue(state.wheelRearRightKph >= 0.0f);
            assertTrue(state.engineFlywheelTorque >= -2_000.0f);
            assertTrue(state.engineFlywheelTorque <= 2_000.0f);
        }
    }

    @Test
    public void cycleContainsAccelerationCruiseBrakingAndStop() {
        DemoScenario scenario = new DemoScenario();

        assertEquals(0, scenario.snapshot(0L, 1_000L).speedKph);
        assertEquals(35, scenario.snapshot(3_000L, 4_000L).speedKph);
        assertEquals(75, scenario.snapshot(8_000L, 9_000L).speedKph);
        assertEquals(95, scenario.snapshot(13_000L, 14_000L).speedKph);
        assertEquals(95, scenario.snapshot(18_000L, 19_000L).speedKph);
        assertEquals(20, scenario.snapshot(24_000L, 25_000L).speedKph);
        assertEquals(0, scenario.snapshot(27_000L, 28_000L).speedKph);
        assertTrue(scenario.snapshot(24_000L, 25_000L).speedKph
                < scenario.snapshot(18_000L, 19_000L).speedKph);
    }

    @Test
    public void cycleExercisesSteeringGearsAndDriveModes() {
        DemoScenario scenario = new DemoScenario();

        assertNotEquals(
                Math.signum(scenario.snapshot(10_000L, 11_000L).steeringAngleDeg),
                Math.signum(scenario.snapshot(16_000L, 17_000L).steeringAngleDeg),
                0.0f);
        assertEquals(1, scenario.snapshot(1_000L, 2_000L).currentGear);
        assertEquals(4, scenario.snapshot(8_000L, 9_000L).currentGear);
        assertEquals(6, scenario.snapshot(15_000L, 16_000L).currentGear);
        assertEquals(0, scenario.snapshot(28_000L, 29_000L).currentGear);
        assertEquals("SPORT", scenario.snapshot(4_000L, 5_000L).driveMode);
        assertEquals("NORMAL", scenario.snapshot(12_000L, 13_000L).driveMode);
        assertEquals("ECO", scenario.snapshot(24_000L, 25_000L).driveMode);
    }

    @Test
    public void cycleExercisesNormalWarningAndCriticalTelemetry() {
        DemoScenario scenario = new DemoScenario();
        ClusterState normal = scenario.snapshot(5_000L, 6_000L);
        ClusterState warning = scenario.snapshot(15_000L, 16_000L);
        ClusterState critical = scenario.snapshot(25_000L, 26_000L);

        assertTrue(normal.consumptionLitersPer100Km <= 20.0f);
        assertTrue(normal.coolantC <= 110);
        assertTrue(normal.transmissionTemperatureC <= 110.0f);
        assertTrue(normal.voltage >= 12.0f);
        assertTrue(normal.tyreFrontLeftBar >= 2.0f);
        assertTrue(normal.fuelLiters >= 8.0f);

        assertTrue(warning.consumptionLitersPer100Km > 20.0f);
        assertTrue(warning.coolantC > 110 && warning.coolantC <= 120);
        assertTrue(warning.transmissionTemperatureC > 110.0f);
        assertTrue(warning.transmissionTemperatureC <= 120.0f);
        assertTrue(warning.voltage < 12.0f);
        assertTrue(warning.tyreFrontLeftBar < 2.0f);
        assertTrue(warning.fuelLiters < 8.0f);
        assertTrue(warning.fuelLiters >= 2.0f);

        assertTrue(critical.coolantC > 120);
        assertTrue(critical.transmissionTemperatureC > 120.0f);
        assertTrue(critical.fuelLiters < 2.0f);
    }

    @Test
    public void snapshotPopulatesEveryLiveLookingDashboardValue() {
        DemoScenario scenario = new DemoScenario();
        ClusterState state = scenario.snapshot(12_000L, 91_000L);

        assertTrue(state.speedKph > 0);
        assertTrue(state.rpm > 0);
        assertTrue(state.currentGear > 0);
        assertTrue(state.coolantC > 0);
        assertTrue(state.hasTransmissionTemperature());
        assertTrue(state.fuelLiters > 0.0f);
        assertTrue(state.rangeKm > 0);
        assertTrue(state.odometerKm > 0.0);
        assertTrue(state.dayKm > 0.0f);
        assertTrue(state.tripKm > 0.0f);
        assertTrue(state.tyreFrontLeftBar > 0.0f);
        assertTrue(state.tyreFrontRightBar > 0.0f);
        assertTrue(state.tyreRearLeftBar > 0.0f);
        assertTrue(state.tyreRearRightBar > 0.0f);
        assertTrue(state.consumptionLitersPer100Km > 0.0f);
        assertTrue(state.journeyAverageFuelConsumption > 0.0f);
        assertTrue(state.voltage > 0.0f);
        assertTrue(state.outsideTemperatureC != 0.0f);
        assertTrue(state.steeringAngleDeg != 0.0f);
        assertTrue(state.wheelFrontLeftKph > 0.0f);
        assertTrue(state.wheelFrontRightKph > 0.0f);
        assertTrue(state.wheelRearLeftKph > 0.0f);
        assertTrue(state.wheelRearRightKph > 0.0f);
        assertTrue(state.engineFlywheelTorque != 0.0f);
        assertEquals(91_000L, state.rpmUpdatedAtMs);
        assertEquals(
                91_000L,
                state.journeyAverageFuelConsumptionUpdatedAtMs);
        assertEquals(91_000L, state.journeyOdometerUpdatedAtMs);
        assertEquals(91_000L, state.steeringUpdatedAtMs);
        assertEquals(91_000L, state.transmissionTemperatureUpdatedAtMs);
    }

    @Test
    public void wheelDifferencesStaySmallAndDistanceAdvances() {
        DemoScenario scenario = new DemoScenario();
        ClusterState earlier = scenario.snapshot(5_000L, 6_000L);
        ClusterState later = scenario.snapshot(20_000L, 21_000L);

        for (long elapsed = 0L;
                elapsed < DemoScenario.CYCLE_MS;
                elapsed += 250L) {
            ClusterState state = scenario.snapshot(elapsed, elapsed + 1_000L);
            assertTrue(Math.abs(
                    state.wheelFrontLeftKph
                            - state.wheelFrontRightKph) <= 3.1f);
            assertTrue(Math.abs(
                    state.wheelFrontLeftKph
                            - state.wheelRearLeftKph) <= 1.0f);
            assertTrue(Math.abs(
                    state.wheelFrontRightKph
                            - state.wheelRearRightKph) <= 1.0f);
        }
        assertTrue(later.odometerKm > earlier.odometerKm);
        assertTrue(later.dayKm > earlier.dayKm);
        assertTrue(later.tripKm > earlier.tripKm);
    }

    @Test
    public void movementPatternWrapsAtCycleBoundary() {
        DemoScenario scenario = new DemoScenario();
        ClusterState start = scenario.snapshot(0L, 1_000L);
        ClusterState wrapped = scenario.snapshot(
                DemoScenario.CYCLE_MS,
                DemoScenario.CYCLE_MS + 1_000L);

        assertEquals(start.speedKph, wrapped.speedKph);
        assertEquals(start.rpm, wrapped.rpm);
        assertEquals(start.currentGear, wrapped.currentGear);
        assertEquals(start.steeringAngleDeg, wrapped.steeringAngleDeg, 0.001f);
    }

    @Test
    public void stoppedSnapshotFreezesDistanceAndPublishesFreshZeroMotion() {
        DemoScenario scenario = new DemoScenario();
        ClusterState frozen = scenario.snapshot(12_000L, 20_000L);
        ClusterState stopped = scenario.stoppedSnapshot(
                12_000L,
                25_000L,
                false);

        assertEquals(0, stopped.speedKph);
        assertEquals(0, stopped.rpm);
        assertEquals(0, stopped.currentGear);
        assertEquals(0.0f, stopped.wheelFrontLeftKph, 0.0f);
        assertEquals(0.0f, stopped.wheelFrontRightKph, 0.0f);
        assertEquals(0.0f, stopped.wheelRearLeftKph, 0.0f);
        assertEquals(0.0f, stopped.wheelRearRightKph, 0.0f);
        assertEquals(frozen.dayKm, stopped.dayKm, 0.0f);
        assertEquals(25_000L, stopped.rpmUpdatedAtMs);
        assertEquals(25_000L, stopped.journeyOdometerUpdatedAtMs);
        assertEquals(
                25_000L,
                stopped.journeyAverageFuelConsumptionUpdatedAtMs);
        assertTrue(stopped.journeyAverageFuelConsumption > 0.0f);
    }

    @Test
    public void invalidConsumptionModeKeepsOtherTelemetryLive() {
        DemoScenario scenario = new DemoScenario();
        ClusterState state = scenario.snapshot(12_000L, 20_000L, true);

        assertTrue(Float.isNaN(state.journeyAverageFuelConsumption));
        assertTrue(Float.isNaN(state.consumptionLitersPer100Km));
        assertTrue(state.dayKm > 0.0f);
        assertEquals(
                20_000L,
                state.journeyAverageFuelConsumptionUpdatedAtMs);
    }
}
