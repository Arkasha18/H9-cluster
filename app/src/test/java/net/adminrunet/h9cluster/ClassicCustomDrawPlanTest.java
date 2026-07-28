package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ClassicCustomDrawPlanTest {
    @Test
    public void allDisabledProducesNoDrawableModules() {
        ClassicCustomDrawPlan plan =
                ClassicCustomDrawPlan.forVisibility(
                        BlockVisibility.noneVisible());

        assertFalse(plan.hasAnyBlock());
        for (BlockVisibility.Block block : BlockVisibility.Block.values()) {
            assertFalse(block.name(), plan.shouldDraw(block));
        }
    }

    @Test
    public void allVisibleProducesEveryDrawableModule() {
        ClassicCustomDrawPlan plan =
                ClassicCustomDrawPlan.forVisibility(
                        BlockVisibility.allVisible());

        assertTrue(plan.hasAnyBlock());
        assertEquals(15, plan.visibleBlockCount());
        for (BlockVisibility.Block block : BlockVisibility.Block.values()) {
            assertTrue(block.name(), plan.shouldDraw(block));
        }
    }

    @Test
    public void oneDisabledBlockDoesNotAffectItsNeighbors() {
        BlockVisibility visibility = BlockVisibility.allVisible()
                .with(BlockVisibility.Block.TYRE_PRESSURE, false);

        ClassicCustomDrawPlan plan =
                ClassicCustomDrawPlan.forVisibility(visibility);

        assertFalse(plan.shouldDraw(BlockVisibility.Block.TYRE_PRESSURE));
        assertTrue(plan.shouldDraw(BlockVisibility.Block.TACHOMETER));
        assertTrue(plan.shouldDraw(BlockVisibility.Block.BATTERY_VOLTAGE));
        assertEquals(14, plan.visibleBlockCount());
    }

    @Test
    public void nullVisibilityFailsSafeToAllVisible() {
        ClassicCustomDrawPlan plan =
                ClassicCustomDrawPlan.forVisibility(null);

        assertEquals(15, plan.visibleBlockCount());
    }
}
