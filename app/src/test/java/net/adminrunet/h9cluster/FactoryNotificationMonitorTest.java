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
    public void dismissWithTheOriginalWarningIdClosesTheHoleImmediately() {
        FactoryNotificationMonitor monitor = new FactoryNotificationMonitor();
        assertTrue(monitor.updateWarning("[287,2,1]", 1_000L));
        assertFalse(monitor.updateWarning("[287,2,2]", 1_500L));
        assertEquals(0, monitor.remainingWarningMs(1_500L));
        assertTrue(monitor.updateWarning("[287,2,1]", 2_000L));
    }

    @Test
    public void bareZeroClearPacketAlsoRestoresTheSkin() {
        FactoryNotificationMonitor monitor = new FactoryNotificationMonitor();
        assertTrue(monitor.updateWarning("[287,2,1]", 1_000L));
        assertFalse(monitor.updateWarning("0", 1_500L));
        assertTrue(monitor.updateWarning("[287,2,1]", 2_000L));
    }

    @Test
    public void cachedShowPacketAtStartupIsNotReplayedAsANewNotification() {
        FactoryNotificationMonitor monitor = new FactoryNotificationMonitor();
        monitor.seedWarningSnapshot("[287,2,1]");
        assertFalse(monitor.isVisibleAt(1_000L));
        assertFalse(monitor.updateWarning("[287,2,1]", 1_100L));
        assertFalse(monitor.updateWarning("[287,2,2]", 1_200L));
        assertTrue(monitor.updateWarning("[287,2,1]", 1_300L));
    }

    @Test
    public void repeatedCachedEventCannotKeepTheHoleOpenPastItsFailSafe() {
        FactoryNotificationMonitor monitor = new FactoryNotificationMonitor();
        assertTrue(monitor.updateWarning("[287,2,1]", 1_000L));
        for (long now = 2000; now < 11_000; now += 1000) {
            assertTrue(monitor.updateWarning("[287,2,1]", now));
        }
        assertFalse(monitor.updateWarning("[287,2,1]", 11_000L));
    }

    @Test
    public void unknownActionsAndIncompletePacketsCannotOpenAWindow() {
        FactoryNotificationMonitor monitor = new FactoryNotificationMonitor();
        for (String value : new String[]{"287", "[287,2]", "[287,2,0]", "[287,2,3]", "[287,2,2]"}) {
            assertFalse(monitor.updateWarning(value, 1000));
        }
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
