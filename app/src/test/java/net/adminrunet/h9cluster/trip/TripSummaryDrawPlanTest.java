package net.adminrunet.h9cluster.trip;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class TripSummaryDrawPlanTest {
    @Test
    public void usesApprovedCentralReferenceGeometry() {
        TripSummaryDrawPlan plan = TripSummaryDrawPlan.forSize(1_920, 720);

        assertBox(plan.panel, 653.0f, 101.0f, 1_267.0f, 691.0f);
        assertBox(plan.title, 653.0f, 101.0f, 1_267.0f, 207.0f);
        assertBox(plan.metricRows[0], 653.0f, 207.0f, 1_267.0f, 328.0f);
        assertBox(plan.metricRows[1], 653.0f, 328.0f, 1_267.0f, 449.0f);
        assertBox(plan.metricRows[2], 653.0f, 449.0f, 1_267.0f, 570.0f);
        assertBox(plan.metricRows[3], 653.0f, 570.0f, 1_267.0f, 691.0f);
        assertEquals(739.0f, plan.innerDividerLeft, 0.001f);
        assertEquals(1_181.0f, plan.innerDividerRight, 0.001f);
        assertEquals(960.0f, plan.panel.centerX(), 0.001f);
    }

    @Test
    public void scalesReferenceGeometryProportionally() {
        TripSummaryDrawPlan plan = TripSummaryDrawPlan.forSize(960, 360);

        assertBox(plan.panel, 326.5f, 50.5f, 633.5f, 345.5f);
        assertBox(plan.title, 326.5f, 50.5f, 633.5f, 103.5f);
        assertBox(plan.metricRows[0], 326.5f, 103.5f, 633.5f, 164.0f);
        assertBox(plan.metricRows[1], 326.5f, 164.0f, 633.5f, 224.5f);
        assertBox(plan.metricRows[2], 326.5f, 224.5f, 633.5f, 285.0f);
        assertBox(plan.metricRows[3], 326.5f, 285.0f, 633.5f, 345.5f);
        assertEquals(369.5f, plan.innerDividerLeft, 0.001f);
        assertEquals(590.5f, plan.innerDividerRight, 0.001f);
        assertEquals(480.0f, plan.panel.centerX(), 0.001f);
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
