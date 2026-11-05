package net.adminrunet.h9cluster;

/** One complete, immutable snapshot of values shown by the cluster. */
public final class ClusterState {
    public static final int ENGINE_STATE_UNKNOWN = -1;
    public static final int ENGINE_STATE_STOPPED = 0;
    public static final int ENGINE_STATE_RUNNING = 2;

    /** Measured speed in km/h; -1 means no trustworthy source is available. */
    public final int speedKph;
    public final int rpm;
    /** Independently decoded TBOX engine state: -1 unknown, 0 stopped, 2 running. */
    public final int engineState;
    public final long engineStateUpdatedAtMs;
    public final boolean parkStateValid;
    public final boolean inPark;
    public final long parkStateUpdatedAtMs;
    public final int currentGear;
    public final String gearSelector;
    public final int coolantC;
    public final float transmissionTemperatureC;
    public final float fuelLiters;
    public final int fuelTankCapacityLiters;
    public final int rangeKm;
    public final double odometerKm;
    public final float dayKm;
    public final float tripKm;
    public final float tyreFrontLeftBar;
    public final float tyreFrontRightBar;
    public final float tyreRearLeftBar;
    public final float tyreRearRightBar;
    public final float instantFuelConsumption;
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

    /** Creates a source snapshot with fuel expressed on the default 80-liter basis. */
    public ClusterState(
            int speedKph,
            int rpm,
            int currentGear,
            String gearSelector,
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
            float instantFuelConsumption,
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
        this.engineState = ENGINE_STATE_UNKNOWN;
        this.engineStateUpdatedAtMs = 0L;
        this.parkStateValid = false;
        this.inPark = false;
        this.parkStateUpdatedAtMs = 0L;
        this.currentGear = currentGear;
        this.gearSelector = gearSelector == null
                ? GearSelector.UNKNOWN
                : gearSelector;
        this.coolantC = coolantC;
        this.transmissionTemperatureC = transmissionTemperatureC;
        this.fuelLiters = fuelLiters;
        this.fuelTankCapacityLiters = FuelTankCapacity.DEFAULT_LITERS;
        this.rangeKm = rangeKm;
        this.odometerKm = odometerKm;
        this.dayKm = dayKm;
        this.tripKm = tripKm;
        this.tyreFrontLeftBar = tyreFrontLeftBar;
        this.tyreFrontRightBar = tyreFrontRightBar;
        this.tyreRearLeftBar = tyreRearLeftBar;
        this.tyreRearRightBar = tyreRearRightBar;
        this.instantFuelConsumption = instantFuelConsumption;
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

    private ClusterState(ClusterState source, int fuelTankCapacityLiters) {
        this(
                source,
                fuelTankCapacityLiters,
                source.engineState,
                source.engineStateUpdatedAtMs,
                source.parkStateValid,
                source.inPark,
                source.parkStateUpdatedAtMs);
    }

    private ClusterState(
            ClusterState source,
            int fuelTankCapacityLiters,
            int engineState,
            long engineStateUpdatedAtMs,
            boolean parkStateValid,
            boolean inPark,
            long parkStateUpdatedAtMs) {
        speedKph = source.speedKph;
        rpm = source.rpm;
        this.engineState = engineState;
        this.engineStateUpdatedAtMs = engineStateUpdatedAtMs;
        this.parkStateValid = parkStateValid;
        this.inPark = inPark;
        this.parkStateUpdatedAtMs = parkStateUpdatedAtMs;
        currentGear = source.currentGear;
        gearSelector = source.gearSelector;
        coolantC = source.coolantC;
        transmissionTemperatureC = source.transmissionTemperatureC;
        fuelLiters = source.fuelTankCapacityLiters == fuelTankCapacityLiters
                ? source.fuelLiters
                : source.fuelLiters / source.fuelTankCapacityLiters
                        * fuelTankCapacityLiters;
        this.fuelTankCapacityLiters = fuelTankCapacityLiters;
        rangeKm = source.rangeKm;
        odometerKm = source.odometerKm;
        dayKm = source.dayKm;
        tripKm = source.tripKm;
        tyreFrontLeftBar = source.tyreFrontLeftBar;
        tyreFrontRightBar = source.tyreFrontRightBar;
        tyreRearLeftBar = source.tyreRearLeftBar;
        tyreRearRightBar = source.tyreRearRightBar;
        instantFuelConsumption = source.instantFuelConsumption;
        consumptionLitersPer100Km = source.consumptionLitersPer100Km;
        journeyAverageFuelConsumption = source.journeyAverageFuelConsumption;
        voltage = source.voltage;
        outsideTemperatureC = source.outsideTemperatureC;
        steeringAngleDeg = source.steeringAngleDeg;
        wheelFrontLeftKph = source.wheelFrontLeftKph;
        wheelFrontRightKph = source.wheelFrontRightKph;
        wheelRearLeftKph = source.wheelRearLeftKph;
        wheelRearRightKph = source.wheelRearRightKph;
        engineFlywheelTorque = source.engineFlywheelTorque;
        rpmUpdatedAtMs = source.rpmUpdatedAtMs;
        journeyAverageFuelConsumptionUpdatedAtMs =
                source.journeyAverageFuelConsumptionUpdatedAtMs;
        journeyOdometerUpdatedAtMs = source.journeyOdometerUpdatedAtMs;
        steeringUpdatedAtMs = source.steeringUpdatedAtMs;
        transmissionTemperatureUpdatedAtMs =
                source.transmissionTemperatureUpdatedAtMs;
        driveMode = source.driveMode;
    }

    /**
     * Projects the same fuel percentage onto a selected tank size. Vehicle
     * range and consumption remain the independent readings from the source.
     */
    public ClusterState withFuelTankCapacity(int liters) {
        int normalized = FuelTankCapacity.normalize(liters);
        return normalized == fuelTankCapacityLiters
                ? this
                : new ClusterState(this, normalized);
    }

    /** Adds independent read-only engine/Park evidence without changing display telemetry. */
    public ClusterState withTboxVehicleState(
            int engineState,
            long engineStateUpdatedAtMs,
            boolean parkStateValid,
            boolean inPark,
            long parkStateUpdatedAtMs) {
        int normalizedEngineState = engineState == ENGINE_STATE_STOPPED
                || engineState == ENGINE_STATE_RUNNING
                ? engineState
                : ENGINE_STATE_UNKNOWN;
        long normalizedEngineUpdatedAtMs = normalizedEngineState == ENGINE_STATE_UNKNOWN
                ? 0L
                : Math.max(0L, engineStateUpdatedAtMs);
        boolean normalizedParkValid = parkStateValid && parkStateUpdatedAtMs > 0L;
        long normalizedParkUpdatedAtMs = normalizedParkValid
                ? parkStateUpdatedAtMs
                : 0L;
        if (this.engineState == normalizedEngineState
                && this.engineStateUpdatedAtMs == normalizedEngineUpdatedAtMs
                && this.parkStateValid == normalizedParkValid
                && this.inPark == (normalizedParkValid && inPark)
                && this.parkStateUpdatedAtMs == normalizedParkUpdatedAtMs) {
            return this;
        }
        return new ClusterState(
                this,
                fuelTankCapacityLiters,
                normalizedEngineState,
                normalizedEngineUpdatedAtMs,
                normalizedParkValid,
                normalizedParkValid && inPark,
                normalizedParkUpdatedAtMs);
    }

    public boolean hasTransmissionTemperature() {
        return !Float.isNaN(transmissionTemperatureC);
    }

    public static ClusterState empty() {
        return new ClusterState(
                0,
                0,
                0,
                GearSelector.UNKNOWN,
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
                Float.NaN,
                0.0f,
                Float.NaN,
                0.0f,
                Float.NaN,
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
