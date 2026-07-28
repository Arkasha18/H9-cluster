package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

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
    public void maskRoundTripPreservesEveryBlock() {
        BlockVisibility visibility = BlockVisibility.allVisible()
                .with(BlockVisibility.Block.CLOCK, false)
                .with(BlockVisibility.Block.ATF_TEMPERATURE, false)
                .with(BlockVisibility.Block.BATTERY_VOLTAGE, false);

        assertEquals(visibility, BlockVisibility.fromMask(visibility.toMask()));
    }

    @Test
    public void allVisibleUsesExactlyTheFifteenSupportedBits() {
        assertEquals(0x7FFFL, BlockVisibility.allVisible().toMask());
        assertEquals(0L, BlockVisibility.noneVisible().toMask());
    }

    @Test
    public void missingOrWrongTypedStoredMaskDefaultsToVisible() {
        assertEquals(
                BlockVisibility.allVisible(),
                BlockVisibility.fromStoredValue(null));
        assertEquals(
                BlockVisibility.allVisible(),
                BlockVisibility.fromStoredValue("0"));
        assertEquals(
                BlockVisibility.allVisible(),
                BlockVisibility.fromStoredValue(Integer.valueOf(0)));
    }

    @Test
    public void negativeOrExtraStoredBitsDefaultToVisible() {
        assertEquals(
                BlockVisibility.allVisible(),
                BlockVisibility.fromStoredValue(Long.valueOf(-1L)));
        assertEquals(
                BlockVisibility.allVisible(),
                BlockVisibility.fromStoredValue(Long.valueOf(1L << 15)));
    }
}
