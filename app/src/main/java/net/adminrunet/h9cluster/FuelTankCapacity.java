package net.adminrunet.h9cluster;

/** User-configurable tank size shared by settings and every cluster skin. */
public final class FuelTankCapacity {
    public static final int DEFAULT_LITERS = 80;
    public static final int MIN_LITERS = 20;
    public static final int MAX_LITERS = 200;

    private FuelTankCapacity() {
    }

    public static int normalize(int liters) {
        return isValid(liters) ? liters : DEFAULT_LITERS;
    }

    public static boolean isValid(int liters) {
        return liters >= MIN_LITERS && liters <= MAX_LITERS;
    }

    /** Returns {@code -1} when a settings value is not a supported integer. */
    public static int parse(String value) {
        if (value == null) {
            return -1;
        }
        try {
            int liters = Integer.parseInt(value.trim());
            return isValid(liters) ? liters : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
