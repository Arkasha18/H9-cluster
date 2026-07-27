package net.adminrunet.h9cluster;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable visibility snapshot shared by every instrument skin. */
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

        private final String preferenceKey;

        Block(String storageName) {
            preferenceKey = PREFERENCE_PREFIX + storageName;
        }

        public String preferenceKey() {
            return preferenceKey;
        }
    }

    private final EnumMap<Block, Boolean> values;

    private BlockVisibility(EnumMap<Block, Boolean> values) {
        this.values = new EnumMap<>(values);
    }

    public static BlockVisibility allVisible() {
        return filled(true);
    }

    public static BlockVisibility noneVisible() {
        return filled(false);
    }

    public static BlockVisibility from(Map<String, ?> storedValues) {
        EnumMap<Block, Boolean> values = new EnumMap<>(Block.class);
        for (Block block : Block.values()) {
            Object stored = storedValues == null
                    ? null
                    : storedValues.get(block.preferenceKey());
            values.put(block, stored instanceof Boolean ? (Boolean) stored : true);
        }
        return new BlockVisibility(values);
    }

    private static BlockVisibility filled(boolean visible) {
        EnumMap<Block, Boolean> values = new EnumMap<>(Block.class);
        for (Block block : Block.values()) {
            values.put(block, visible);
        }
        return new BlockVisibility(values);
    }

    public boolean isVisible(Block block) {
        return Boolean.TRUE.equals(values.get(block));
    }

    public BlockVisibility with(Block block, boolean visible) {
        EnumMap<Block, Boolean> changed = new EnumMap<>(values);
        changed.put(block, visible);
        return new BlockVisibility(changed);
    }

    public Map<String, Boolean> asMap() {
        Map<String, Boolean> storedValues = new LinkedHashMap<>();
        for (Block block : Block.values()) {
            storedValues.put(block.preferenceKey(), isVisible(block));
        }
        return Collections.unmodifiableMap(storedValues);
    }
}
