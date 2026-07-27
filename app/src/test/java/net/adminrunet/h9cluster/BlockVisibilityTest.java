package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public final class BlockVisibilityTest {
    @Test
    public void catalogContainsExactlyFifteenBlocks() {
        assertEquals(15, BlockVisibility.Block.values().length);
    }

    @Test
    public void allVisibleEnablesEveryBlock() {
        BlockVisibility visibility = BlockVisibility.allVisible();

        for (BlockVisibility.Block block : BlockVisibility.Block.values()) {
            assertTrue(block.name(), visibility.isVisible(block));
        }
    }

    @Test
    public void noneVisibleDisablesEveryBlock() {
        BlockVisibility visibility = BlockVisibility.noneVisible();

        for (BlockVisibility.Block block : BlockVisibility.Block.values()) {
            assertFalse(block.name(), visibility.isVisible(block));
        }
    }

    @Test
    public void withReturnsChangedCopyWithoutMutatingOriginal() {
        BlockVisibility original = BlockVisibility.allVisible();

        BlockVisibility changed =
                original.with(BlockVisibility.Block.ATF_TEMPERATURE, false);

        assertNotSame(original, changed);
        assertTrue(original.isVisible(BlockVisibility.Block.ATF_TEMPERATURE));
        assertFalse(changed.isVisible(BlockVisibility.Block.ATF_TEMPERATURE));
    }

    @Test
    public void missingValuesDefaultToVisible() {
        BlockVisibility visibility =
                BlockVisibility.from(Collections.<String, Object>emptyMap());

        assertTrue(visibility.isVisible(BlockVisibility.Block.BATTERY_VOLTAGE));
    }

    @Test
    public void malformedValuesDefaultToVisible() {
        Map<String, Object> values = new HashMap<>();
        values.put(BlockVisibility.Block.CLOCK.preferenceKey(), "false");

        BlockVisibility visibility = BlockVisibility.from(values);

        assertTrue(visibility.isVisible(BlockVisibility.Block.CLOCK));
    }

    @Test
    public void booleanValuesAreReadByStablePreferenceKey() {
        Map<String, Object> values = new HashMap<>();
        values.put(BlockVisibility.Block.CLOCK.preferenceKey(), false);

        BlockVisibility visibility = BlockVisibility.from(values);

        assertFalse(visibility.isVisible(BlockVisibility.Block.CLOCK));
        assertTrue(visibility.isVisible(BlockVisibility.Block.SPEEDOMETER));
    }

    @Test
    public void exportedMapContainsEveryStableKey() {
        Map<String, Boolean> values = BlockVisibility.allVisible().asMap();

        assertEquals(15, values.size());
        assertEquals(
                Boolean.TRUE,
                values.get(BlockVisibility.Block.SPEEDOMETER.preferenceKey()));
        assertEquals(
                Boolean.TRUE,
                values.get(BlockVisibility.Block.BATTERY_VOLTAGE.preferenceKey()));
    }
}
