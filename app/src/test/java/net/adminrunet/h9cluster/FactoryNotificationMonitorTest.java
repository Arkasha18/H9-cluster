package net.adminrunet.h9cluster;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FactoryNotificationMonitorTest {
    @Test
    public void doorStatusStaysVisibleUntilEveryOpeningIsClosed() {
        FactoryNotificationMonitor monitor = new FactoryNotificationMonitor();

        assertTrue(monitor.updateDoorStatus("[1,0,0,0,0,0]", 100L));
        assertTrue(monitor.updateDoorStatus("[0,0,0,0,0,1]", 200L));
        assertFalse(monitor.updateDoorStatus("[0,0,0,0,0,0]", 300L));
    }

    @Test
    public void unavailableDoorValuesDoNotOpenTheWindow() {
        assertFalse(FactoryNotificationMonitor.containsPositiveValue(
                "[-1,-1,-1,-1,-1,-1]"));
        assertFalse(FactoryNotificationMonitor.containsPositiveValue(null));
        assertFalse(FactoryNotificationMonitor.containsPositiveValue("invalid"));
    }

    @Test
    public void qnxWarningDismissesOnClearEvent() {
        FactoryNotificationMonitor monitor = new FactoryNotificationMonitor();

        assertTrue(monitor.updateWarning("[287,2,1]", 1_000L));
        assertFalse(monitor.updateWarning("[0,0,2]", 1_500L));
    }

    @Test
    public void qnxWarningTimesOutIfDismissEventIsLost() {
        FactoryNotificationMonitor monitor = new FactoryNotificationMonitor();

        assertTrue(monitor.updateWarning("[287,2,1]", 1_000L));
        assertTrue(monitor.isVisibleAt(
                1_000L + FactoryNotificationMonitor.WARNING_TIMEOUT_MS - 1L));
        assertFalse(monitor.isVisibleAt(
                1_000L + FactoryNotificationMonitor.WARNING_TIMEOUT_MS));
    }

    @Test
    public void openDoorOutlivesGenericWarningTimeout() {
        FactoryNotificationMonitor monitor = new FactoryNotificationMonitor();

        monitor.updateDoorStatus("[0,1,0,0,0,0]", 0L);
        monitor.updateWarning("[42,1,1]", 0L);

        assertTrue(monitor.isVisibleAt(
                FactoryNotificationMonitor.WARNING_TIMEOUT_MS + 1L));
    }

    @Test
    public void warningParserUsesOnlyTheWarningId() {
        assertEquals(
                Integer.valueOf(18),
                FactoryNotificationMonitor.firstInteger("[18,3,1]"));
        assertEquals(
                Integer.valueOf(0),
                FactoryNotificationMonitor.firstInteger("[0,0,2]"));
        assertNull(FactoryNotificationMonitor.firstInteger("invalid"));
    }
}
