package net.adminrunet.h9cluster.trip;

import static net.adminrunet.h9cluster.trip.EngineRunDetector.Event.NONE;
import static net.adminrunet.h9cluster.trip.EngineRunDetector.Event.STARTED;
import static net.adminrunet.h9cluster.trip.EngineRunDetector.Event.STOPPED;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class EngineRunDetectorTest {
    @Test
    public void startsAtExactThresholdAfterContinuousHold() {
        EngineRunDetector detector = new EngineRunDetector(false);

        assertEquals(NONE, detector.update(400, 0, 1_000L, 1_000L));
        assertEquals(NONE, detector.update(400, 0, 1_999L, 1_999L));
        assertEquals(STARTED, detector.update(400, 0, 2_000L, 2_000L));
        assertEquals(NONE, detector.update(400, 0, 2_001L, 2_001L));
    }

    @Test
    public void startCandidateResetsAfterThresholdViolation() {
        EngineRunDetector detector = new EngineRunDetector(false);

        assertEquals(NONE, detector.update(400, 0, 1_000L, 1_000L));
        assertEquals(NONE, detector.update(399, 0, 1_500L, 1_500L));
        assertEquals(NONE, detector.update(400, 0, 2_000L, 2_000L));
        assertEquals(NONE, detector.update(400, 0, 2_999L, 2_999L));
        assertEquals(STARTED, detector.update(400, 0, 3_000L, 3_000L));
    }

    @Test
    public void staleOrStuckRpmCannotCompleteStartHold() {
        EngineRunDetector detector = new EngineRunDetector(false);

        assertEquals(NONE, detector.update(900, 0, 1_000L, 1_000L));
        assertEquals(NONE, detector.update(900, 0, 1_000L, 2_001L));
        assertEquals(NONE, detector.update(900, 0, 2_001L, 2_001L));
        assertEquals(STARTED, detector.update(900, 0, 3_001L, 3_001L));
    }

    @Test
    public void stopsAtExactThresholdAfterContinuousZeroSpeedHold() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(50, 0, 10_000L, 10_000L));
        assertEquals(NONE, detector.update(50, 0, 12_499L, 12_499L));
        assertEquals(STOPPED, detector.update(50, 0, 12_500L, 12_500L));
        assertEquals(NONE, detector.update(0, 0, 12_501L, 12_501L));
    }

    @Test
    public void stopCandidateResetsForRpmSpeedAndFreshnessViolations() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(50, 0, 1_000L, 1_000L));
        assertEquals(NONE, detector.update(51, 0, 2_000L, 2_000L));
        assertEquals(NONE, detector.update(50, 0, 3_000L, 3_000L));
        assertEquals(NONE, detector.update(50, 1, 4_000L, 4_000L));
        assertEquals(NONE, detector.update(50, 0, 5_000L, 5_000L));
        assertEquals(NONE, detector.update(50, 0, 5_000L, 6_001L));
        assertEquals(NONE, detector.update(50, 0, 7_000L, 7_000L));
        assertEquals(STOPPED, detector.update(50, 0, 9_500L, 9_500L));
    }

    @Test
    public void restoredRunningDetectorStopsWithoutAnotherStart() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(0, 0, 20_000L, 20_000L));
        assertEquals(STOPPED, detector.update(0, 0, 22_500L, 22_500L));
    }
}
