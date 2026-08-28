package net.adminrunet.h9cluster.skins;

import static org.junit.Assert.assertEquals;

import net.adminrunet.h9cluster.ClusterState;

import org.junit.Test;

public final class FuelConsumptionFormatterTest {
    @Test
    public void instantConsumptionUsesLitersPerHourAtIdle() {
        ClusterState state = stateWithConsumption(0, 0.9f);

        assertEquals("0.9 L/h", FuelConsumptionFormatter.instant(state));
    }

    @Test
    public void instantConsumptionUsesLitersPerHundredWhenMoving() {
        ClusterState state = stateWithConsumption(70, 11.4f);

        assertEquals("11.4 L/100", FuelConsumptionFormatter.instant(state));
    }

    @Test
    public void unavailableValuesAreNeverRenderedAsNan() {
        ClusterState state = stateWithConsumption(70, Float.NaN);

        assertEquals("\u2014", FuelConsumptionFormatter.instant(state));
        assertEquals("\u2014", FuelConsumptionFormatter.average(Float.NaN));
    }

    private static ClusterState stateWithConsumption(
            int speedKph,
            float instantConsumption) {
        ClusterState empty = ClusterState.empty();
        return new ClusterState(
                speedKph,
                empty.rpm,
                empty.currentGear,
                empty.gearSelector,
                empty.coolantC,
                empty.transmissionTemperatureC,
                empty.fuelLiters,
                empty.rangeKm,
                empty.odometerKm,
                empty.dayKm,
                empty.tripKm,
                empty.tyreFrontLeftBar,
                empty.tyreFrontRightBar,
                empty.tyreRearLeftBar,
                empty.tyreRearRightBar,
                instantConsumption,
                9.5f,
                empty.journeyAverageFuelConsumption,
                empty.voltage,
                empty.outsideTemperatureC,
                empty.steeringAngleDeg,
                empty.wheelFrontLeftKph,
                empty.wheelFrontRightKph,
                empty.wheelRearLeftKph,
                empty.wheelRearRightKph,
                empty.engineFlywheelTorque,
                empty.rpmUpdatedAtMs,
                empty.journeyAverageFuelConsumptionUpdatedAtMs,
                empty.journeyOdometerUpdatedAtMs,
                empty.steeringUpdatedAtMs,
                empty.transmissionTemperatureUpdatedAtMs,
                empty.driveMode);
    }
}
