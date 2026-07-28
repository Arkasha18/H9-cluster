package net.adminrunet.h9cluster;

/** Immutable visibility snapshot used only by the Classic Custom skin. */
public final class BlockVisibility {
    private static final String PREFERENCE_PREFIX = "block_visible_";

    public enum Block {
        SPEEDOMETER("speedometer"),
        TACHOMETER("tachometer"),
        FUEL_AND_RANGE("fuel_and_range"),
        ENGINE_TEMPERATURE("engine_temperature"),
        WHEEL_SPEEDS("wheel_speeds"),
        ENGINE_TORQUE("engine_torque"),
        CLOCK("clock"),
        STEERING_ANGLE("steering_angle"),
        OUTSIDE_TEMPERATURE("outside_temperature"),
        ATF_TEMPERATURE("atf_temperature"),
        CURRENT_GEAR("current_gear"),
        ODOMETERS("odometers"),
        FUEL_CONSUMPTION("fuel_consumption"),
        TYRE_PRESSURE("tyre_pressure"),
        BATTERY_VOLTAGE("battery_voltage");

        private final String storageName;

        Block(String storageName) {
            this.storageName = storageName;
        }

        public String storageName() {
            return storageName;
        }

        public String preferenceKey() {
            return PREFERENCE_PREFIX + storageName;
        }
    }

    private static final long VALID_MASK = (1L << Block.values().length) - 1L;

    private final long mask;

    private BlockVisibility(long mask) {
        this.mask = mask;
    }

    public static BlockVisibility allVisible() {
        return new BlockVisibility(VALID_MASK);
    }

    public static BlockVisibility noneVisible() {
        return new BlockVisibility(0L);
    }

    public static BlockVisibility fromMask(long mask) {
        if (mask < 0L || (mask & ~VALID_MASK) != 0L) {
            return allVisible();
        }
        return new BlockVisibility(mask);
    }

    public static BlockVisibility fromStoredValue(Object storedValue) {
        if (!(storedValue instanceof Long)) {
            return allVisible();
        }
        return fromMask(((Long) storedValue).longValue());
    }

    public static long validMask() {
        return VALID_MASK;
    }

    public long toMask() {
        return mask;
    }

    public boolean isVisible(Block block) {
        return block != null && (mask & bit(block)) != 0L;
    }

    public boolean hasVisibleBlocks() {
        return mask != 0L;
    }

    public BlockVisibility with(Block block, boolean visible) {
        if (block == null) {
            return this;
        }
        long bit = bit(block);
        return new BlockVisibility(visible ? mask | bit : mask & ~bit);
    }

    private static long bit(Block block) {
        return 1L << block.ordinal();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlockVisibility)) {
            return false;
        }
        return mask == ((BlockVisibility) other).mask;
    }

    @Override
    public int hashCode() {
        return Long.valueOf(mask).hashCode();
    }
}
