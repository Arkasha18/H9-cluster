package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public final class ClusterWindowRegistryTest {
    private static final class RecordingWindow
            implements ClusterWindowRegistry.Window {
        private int closeCount;

        @Override
        public void closeClusterWindow() {
            closeCount++;
            ClusterWindowRegistry.unregister(this);
        }
    }

    @After
    public void clearRegistry() {
        ClusterWindowRegistry.closeAll();
    }

    @Test
    public void closingWithoutOpenWindowsReportsNothingToClose() {
        assertFalse(ClusterWindowRegistry.hasOpenWindow());
        assertFalse(ClusterWindowRegistry.closeAll());
    }

    @Test
    public void everyRegisteredWindowIsClosedOnce() {
        RecordingWindow mainDisplay = new RecordingWindow();
        RecordingWindow clusterDisplay = new RecordingWindow();
        ClusterWindowRegistry.register(mainDisplay);
        ClusterWindowRegistry.register(clusterDisplay);

        assertTrue(ClusterWindowRegistry.hasOpenWindow());
        assertTrue(ClusterWindowRegistry.closeAll());

        assertEquals(1, mainDisplay.closeCount);
        assertEquals(1, clusterDisplay.closeCount);
        assertFalse(ClusterWindowRegistry.hasOpenWindow());
    }

    @Test
    public void repeatedExitDoesNotCloseTheSameWindowTwice() {
        RecordingWindow window = new RecordingWindow();
        ClusterWindowRegistry.register(window);

        ClusterWindowRegistry.closeAll();
        assertFalse(ClusterWindowRegistry.closeAll());

        assertEquals(1, window.closeCount);
    }

    @Test
    public void registeringTheSameWindowTwiceKeepsOneEntry() {
        RecordingWindow window = new RecordingWindow();
        ClusterWindowRegistry.register(window);
        ClusterWindowRegistry.register(window);

        ClusterWindowRegistry.closeAll();

        assertEquals(1, window.closeCount);
    }

    @Test
    public void aDestroyedWindowIsNoLongerClosed() {
        RecordingWindow window = new RecordingWindow();
        ClusterWindowRegistry.register(window);
        ClusterWindowRegistry.unregister(window);

        assertFalse(ClusterWindowRegistry.closeAll());
        assertEquals(0, window.closeCount);
    }
}
