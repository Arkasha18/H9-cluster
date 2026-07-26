package net.adminrunet.h9cluster;

/** One complete, immutable snapshot of values shown by the cluster. */
public final class ClusterState {
    public final int speedKph;
    public final int rpm;
    public final int coolantC;
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
    public final float voltage;
    public final float outsideTemperatureC;
    public final float steeringAngleDeg;
    public final float wheelFrontLeftKph;
    public final float wheelFrontRightKph;
    public final float wheelRearLeftKph;
    public final float wheelRearRightKph;
    public final float engineFlywheelTorque;
    public final long rpmUpdatedAtMs;
    public final long steeringUpdatedAtMs;
    public final String driveMode;

    public ClusterState(
            int speedKph,
            int rpm,
            int coolantC,
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
            float voltage,
            float outsideTemperatureC,
            float steeringAngleDeg,
            float wheelFrontLeftKph,
            float wheelFrontRightKph,
            float wheelRearLeftKph,
            float wheelRearRightKph,
            float engineFlywheelTorque,
            long rpmUpdatedAtMs,
            long steeringUpdatedAtMs,
            String driveMode) {
        this.speedKph = speedKph;
        this.rpm = rpm;
        this.coolantC = coolantC;
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
        this.voltage = voltage;
        this.outsideTemperatureC = outsideTemperatureC;
        this.steeringAngleDeg = steeringAngleDeg;
        this.wheelFrontLeftKph = wheelFrontLeftKph;
        this.wheelFrontRightKph = wheelFrontRightKph;
        this.wheelRearLeftKph = wheelRearLeftKph;
        this.wheelRearRightKph = wheelRearRightKph;
        this.engineFlywheelTorque = engineFlywheelTorque;
        this.rpmUpdatedAtMs = rpmUpdatedAtMs;
        this.steeringUpdatedAtMs = steeringUpdatedAtMs;
        this.driveMode = driveMode == null ? "ECO" : driveMode;
    }

    public static ClusterState empty() {
        return new ClusterState(
                0,
                0,
                40,
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
                "ECO");
    }
}
