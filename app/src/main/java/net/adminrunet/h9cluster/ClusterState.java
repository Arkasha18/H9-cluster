package net.adminrunet.h9cluster;

/** One complete, immutable snapshot of values shown by the cluster. */
public final class ClusterState {
    public final int speedKph;
    public final int rpm;
    public final int currentGear;
    public final int coolantC;
    public final float transmissionTemperatureC;
    public final float fuelLiters;
    public final int rangeKm;
    public final double odometerKm;
    public final float dayKm;
    public final float tripKm;
    public final float tyreFrontLeftBar;
    public final float tyreFrontRightBar;
    public final float tyreRearLeftBar;
    public final float tyreRearRightBar;
    public final float consumptionLitersPer100Km;
    public final float journeyAverageFuelConsumption;
    public final float voltage;
    public final float outsideTemperatureC;
    public final float steeringAngleDeg;
    public final float wheelFrontLeftKph;
    public final float wheelFrontRightKph;
    public final float wheelRearLeftKph;
    public final float wheelRearRightKph;
    public final float engineFlywheelTorque;
    public final long rpmUpdatedAtMs;
    public final long journeyAverageFuelConsumptionUpdatedAtMs;
    public final long journeyOdometerUpdatedAtMs;
    public final long steeringUpdatedAtMs;
    public final long transmissionTemperatureUpdatedAtMs;
    public final String driveMode;

    public ClusterState(
            int speedKph,
            int rpm,
            int currentGear,
            int coolantC,
            float transmissionTemperatureC,
            float fuelLiters,
            int rangeKm,
            double odometerKm,
            float dayKm,
            float tripKm,
            float tyreFrontLeftBar,
            float tyreFrontRightBar,
            float tyreRearLeftBar,
            float tyreRearRightBar,
            float consumptionLitersPer100Km,
            float journeyAverageFuelConsumption,
            float voltage,
            float outsideTemperatureC,
            float steeringAngleDeg,
            float wheelFrontLeftKph,
            float wheelFrontRightKph,
            float wheelRearLeftKph,
            float wheelRearRightKph,
            float engineFlywheelTorque,
            long rpmUpdatedAtMs,
            long journeyAverageFuelConsumptionUpdatedAtMs,
            long journeyOdometerUpdatedAtMs,
            long steeringUpdatedAtMs,
            long transmissionTemperatureUpdatedAtMs,
            String driveMode) {
        this.speedKph = speedKph;
        this.rpm = rpm;
        this.currentGear = currentGear;
        this.coolantC = coolantC;
        this.transmissionTemperatureC = transmissionTemperatureC;
        this.fuelLiters = fuelLiters;
        this.rangeKm = rangeKm;
        this.odometerKm = odometerKm;
        this.dayKm = dayKm;
        this.tripKm = tripKm;
        this.tyreFrontLeftBar = tyreFrontLeftBar;
        this.tyreFrontRightBar = tyreFrontRightBar;
        this.tyreRearLeftBar = tyreRearLeftBar;
        this.tyreRearRightBar = tyreRearRightBar;
        this.consumptionLitersPer100Km = consumptionLitersPer100Km;
        this.journeyAverageFuelConsumption =
                journeyAverageFuelConsumption;
        this.voltage = voltage;
        this.outsideTemperatureC = outsideTemperatureC;
        this.steeringAngleDeg = steeringAngleDeg;
        this.wheelFrontLeftKph = wheelFrontLeftKph;
        this.wheelFrontRightKph = wheelFrontRightKph;
        this.wheelRearLeftKph = wheelRearLeftKph;
        this.wheelRearRightKph = wheelRearRightKph;
        this.engineFlywheelTorque = engineFlywheelTorque;
        this.rpmUpdatedAtMs = rpmUpdatedAtMs;
        this.journeyAverageFuelConsumptionUpdatedAtMs =
                journeyAverageFuelConsumptionUpdatedAtMs;
        this.journeyOdometerUpdatedAtMs = journeyOdometerUpdatedAtMs;
        this.steeringUpdatedAtMs = steeringUpdatedAtMs;
        this.transmissionTemperatureUpdatedAtMs =
                transmissionTemperatureUpdatedAtMs;
        this.driveMode = driveMode == null ? "ECO" : driveMode;
    }

    public boolean hasTransmissionTemperature() {
        return !Float.isNaN(transmissionTemperatureC);
    }

    public static ClusterState empty() {
        return new ClusterState(
                0,
                0,
                0,
                40,
                Float.NaN,
                0.0f,
                0,
                0.0,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                Float.NaN,
                0.0f,
                0.0f,
                0.0f,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                0L,
                0L,
                0L,
                0L,
                0L,
                "ECO");
    }
}
