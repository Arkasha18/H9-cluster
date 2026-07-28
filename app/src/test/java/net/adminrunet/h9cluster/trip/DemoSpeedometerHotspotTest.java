package net.adminrunet.h9cluster.trip;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DemoSpeedometerHotspotTest {
    @Test
    public void containsOnlyReferenceSpeedometerRegion() {
        assertTrue(DemoSpeedometerHotspot.contains(40.0f, 140.0f, 1_920, 720));
        assertTrue(DemoSpeedometerHotspot.contains(320.0f, 400.0f, 1_920, 720));
        assertTrue(DemoSpeedometerHotspot.contains(600.0f, 650.0f, 1_920, 720));

        assertFalse(DemoSpeedometerHotspot.contains(39.9f, 400.0f, 1_920, 720));
        assertFalse(DemoSpeedometerHotspot.contains(600.1f, 400.0f, 1_920, 720));
        assertFalse(DemoSpeedometerHotspot.contains(320.0f, 139.9f, 1_920, 720));
        assertFalse(DemoSpeedometerHotspot.contains(320.0f, 650.1f, 1_920, 720));
    }

    @Test
    public void scalesWithTheDisplay() {
        assertTrue(DemoSpeedometerHotspot.contains(160.0f, 200.0f, 960, 360));
        assertFalse(DemoSpeedometerHotspot.contains(301.0f, 200.0f, 960, 360));
    }

    @Test
    public void rejectsMissingDisplayDimensions() {
        assertFalse(DemoSpeedometerHotspot.contains(100.0f, 100.0f, 0, 720));
        assertFalse(DemoSpeedometerHotspot.contains(100.0f, 100.0f, 1_920, 0));
    }
}
