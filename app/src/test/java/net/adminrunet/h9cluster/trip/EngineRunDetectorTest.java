package net.adminrunet.h9cluster.trip;

import static net.adminrunet.h9cluster.trip.EngineRunDetector.Event.NONE;
import static net.adminrunet.h9cluster.trip.EngineRunDetector.Event.STARTED;
import static net.adminrunet.h9cluster.trip.EngineRunDetector.Event.STARTING;
import static net.adminrunet.h9cluster.trip.EngineRunDetector.Event.STOPPED;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class EngineRunDetectorTest {
    @Test
    public void startsAtExactThresholdAfterContinuousHold() {
        EngineRunDetector detector = new EngineRunDetector(false);

        assertEquals(STARTING, detector.update(400, 0, 1_000L, 1_000L));
        assertEquals(NONE, detector.update(400, 0, 1_999L, 1_999L));
        assertEquals(STARTED, detector.update(400, 0, 2_000L, 2_000L));
        assertEquals(NONE, detector.update(400, 0, 2_001L, 2_001L));
    }

    @Test
    public void startCandidateResetsAfterThresholdViolation() {
        EngineRunDetector detector = new EngineRunDetector(false);

        assertEquals(STARTING, detector.update(400, 0, 1_000L, 1_000L));
        assertEquals(NONE, detector.update(399, 0, 1_500L, 1_500L));
        assertEquals(STARTING, detector.update(400, 0, 2_000L, 2_000L));
        assertEquals(NONE, detector.update(400, 0, 2_999L, 2_999L));
        assertEquals(STARTED, detector.update(400, 0, 3_000L, 3_000L));
    }

    @Test
    public void staleOrStuckRpmCannotCompleteStartHold() {
        EngineRunDetector detector = new EngineRunDetector(false);

        assertEquals(STARTING, detector.update(900, 0, 1_000L, 1_000L));
        assertEquals(NONE, detector.update(900, 0, 1_000L, 2_001L));
        assertEquals(STARTING, detector.update(900, 0, 2_001L, 2_001L));
        assertEquals(STARTED, detector.update(900, 0, 3_001L, 3_001L));
    }

    @Test
    public void stopsAtExactThresholdAfterContinuousZeroSpeedHold() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(50, 0, 10_000L, 10_000L));
        assertEquals(NONE, detector.update(50, 0, 11_499L, 11_499L));
        assertEquals(STOPPED, detector.update(50, 0, 11_500L, 11_500L));
        assertEquals(NONE, detector.update(0, 0, 11_501L, 11_501L));
    }

    @Test
    public void stopCandidateResetsForRpmAndSpeedViolations() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(50, 0, 1_000L, 1_000L));
        assertEquals(NONE, detector.update(51, 0, 2_000L, 2_000L));
        assertEquals(NONE, detector.update(50, 0, 3_000L, 3_000L));
        assertEquals(NONE, detector.update(50, 1, 4_000L, 4_000L));
        assertEquals(NONE, detector.update(50, 0, 5_000L, 5_000L));
        assertEquals(NONE, detector.update(50, 0, 6_499L, 6_499L));
        assertEquals(STOPPED, detector.update(50, 0, 6_500L, 6_500L));
    }

    @Test
    public void idlingAtZeroSpeedWithStaleRpmKeepsTripRunning() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(800, 0, 1_000L, 2_001L));
        assertEquals(NONE, detector.update(800, 0, 1_000L, 5_000L));
        assertEquals(NONE, detector.update(800, 0, 1_000L, 15_999L));
    }

    @Test
    public void recoveredRpmCancelsProlongedSilenceShutdownCandidate() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(800, 0, 1_000L, 16_000L));
        assertEquals(NONE, detector.update(800, 0, 16_500L, 16_500L));
        assertEquals(NONE, detector.update(800, 0, 17_500L, 17_500L));
    }

    @Test
    public void prolongedRpmSilenceAtZeroSpeedConfirmsShutdown() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(800, 0, 1_000L, 16_000L));
        assertEquals(NONE, detector.update(800, 0, 1_000L, 17_499L));
        assertEquals(STOPPED, detector.update(800, 0, 1_000L, 17_500L));
    }

    @Test
    public void staleRpmBelowStopThresholdStillConfirmsShutdown() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(0, 0, 1_000L, 2_001L));
        assertEquals(STOPPED, detector.update(0, 0, 1_000L, 3_501L));
    }

    @Test
    public void movementCancelsProlongedSilenceShutdownCandidate() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(800, 0, 1_000L, 16_000L));
        assertEquals(NONE, detector.update(800, 1, 1_000L, 16_500L));
        assertEquals(NONE, detector.update(800, 0, 1_000L, 17_000L));
        assertEquals(STOPPED, detector.update(800, 0, 1_000L, 18_500L));
    }

    @Test
    public void neverObservedRpmCannotStopTrip() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(0, 0, 0L, 10_000L));
        assertEquals(NONE, detector.update(0, 0, 0L, 20_000L));
    }

    @Test
    public void restoredRunningDetectorStopsWithoutAnotherStart() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(0, 0, 20_000L, 20_000L));
        assertEquals(STOPPED, detector.update(0, 0, 22_500L, 22_500L));
    }
}
