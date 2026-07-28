package net.adminrunet.h9cluster.trip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TripSummaryDrawPlanTest {
    @Test
    public void usesCompactSystemSafeReferenceGeometry() {
        TripSummaryDrawPlan plan = TripSummaryDrawPlan.forSize(1_920, 720);

        assertBox(plan.leftPanel, 96.0f, 420.0f, 600.0f, 650.0f);
        assertBox(plan.rightPanel, 1_320.0f, 420.0f, 1_880.0f, 650.0f);
        assertBox(plan.leftCritical, 330.0f, 440.0f, 570.0f, 625.0f);
        assertBox(plan.rightCritical, 1_350.0f, 440.0f, 1_590.0f, 625.0f);

        assertTrue(plan.leftPanel.left >= 96.0f);
        assertTrue(plan.leftPanel.top >= 420.0f);
        assertTrue(plan.leftPanel.right <= 600.0f);
        assertTrue(plan.rightPanel.left >= 1_320.0f);
        assertTrue(plan.rightPanel.top >= 420.0f);
        assertTrue(plan.leftCritical.left >= 330.0f);
        assertTrue(plan.rightCritical.right <= 1_590.0f);
    }

    @Test
    public void scalesReferenceGeometryProportionally() {
        TripSummaryDrawPlan plan = TripSummaryDrawPlan.forSize(960, 360);

        assertBox(plan.leftPanel, 48.0f, 210.0f, 300.0f, 325.0f);
        assertBox(plan.rightPanel, 660.0f, 210.0f, 940.0f, 325.0f);
        assertBox(plan.leftCritical, 165.0f, 220.0f, 285.0f, 312.5f);
        assertBox(plan.rightCritical, 675.0f, 220.0f, 795.0f, 312.5f);
    }

    private static void assertBox(
            TripSummaryDrawPlan.Box actual,
            float left,
            float top,
            float right,
            float bottom) {
        assertEquals(left, actual.left, 0.001f);
        assertEquals(top, actual.top, 0.001f);
        assertEquals(right, actual.right, 0.001f);
        assertEquals(bottom, actual.bottom, 0.001f);
    }
}
