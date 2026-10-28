package net.adminrunet.h9cluster;

/** Supported tank sizes shared by settings and every cluster skin. */
public final class FuelTankCapacity {
    public static final int DEFAULT_LITERS = 80;
    public static final int SMALL_LITERS = 60;

    private FuelTankCapacity() {
    }

    public static int normalize(int liters) {
        return liters == SMALL_LITERS ? SMALL_LITERS : DEFAULT_LITERS;
    }
}
