package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ClusterDisplayPolicyTest {
    @Test
    public void productionRejectsLaunchWhenClusterDisplayIsMissing() {
        assertEquals(
                ClusterDisplayPolicy.NO_DISPLAY,
                ClusterDisplayPolicy.resolveTargetDisplay(false, false, 0));
    }

    @Test
    public void demoFallsBackToCurrentDisplayWhenClusterDisplayIsMissing() {
        assertEquals(0, ClusterDisplayPolicy.resolveTargetDisplay(true, false, 0));
        assertTrue(ClusterDisplayPolicy.isSingleDisplayFallback(true, 0));
    }

    @Test
    public void availableClusterDisplayAlwaysHasPriority() {
        assertEquals(2, ClusterDisplayPolicy.resolveTargetDisplay(false, true, 0));
        assertEquals(2, ClusterDisplayPolicy.resolveTargetDisplay(true, true, 0));
        assertFalse(ClusterDisplayPolicy.isSingleDisplayFallback(true, 2));
    }

    @Test
    public void productionLaunchIsNeverMarkedAsSingleDisplayFallback() {
        assertFalse(ClusterDisplayPolicy.isSingleDisplayFallback(false, 0));
    }

    @Test
    public void onlyDemoFallbackReturnsToSettingsOnInteraction() {
        assertTrue(ClusterDisplayPolicy.shouldReturnToSettingsOnInteraction(true, true));
        assertFalse(ClusterDisplayPolicy.shouldReturnToSettingsOnInteraction(true, false));
        assertFalse(ClusterDisplayPolicy.shouldReturnToSettingsOnInteraction(false, true));
        assertFalse(ClusterDisplayPolicy.shouldReturnToSettingsOnInteraction(false, false));
    }
}
