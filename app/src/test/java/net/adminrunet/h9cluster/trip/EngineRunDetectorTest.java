package net.adminrunet.h9cluster.trip;

import static net.adminrunet.h9cluster.trip.EngineRunDetector.Event.NONE;
import static net.adminrunet.h9cluster.trip.EngineRunDetector.Event.STARTED;
import static net.adminrunet.h9cluster.trip.EngineRunDetector.Event.STARTING;
import static net.adminrunet.h9cluster.trip.EngineRunDetector.Event.STOPPED;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class EngineRunDetectorTest {
    @Test
    public void pendingStopSurvivesSpeedExpiry() {
        EngineRunDetector detector = new EngineRunDetector(true);
        assertEquals(NONE, detector.update(0, 0, 10_000L, 10_000L));
        assertEquals(NONE, detector.update(0, -1, 10_000L, 11_100L));
        assertEquals(STOPPED, detector.update(0, -1, 10_000L, 11_500L));
    }

    @Test
    public void recentStationaryEvidenceAllowsFinalRpmPacketToArriveAfterSpeedExpiry() {
        EngineRunDetector detector = new EngineRunDetector(true);
        assertEquals(NONE, detector.update(800, 0, 10_000L, 10_000L));
        assertEquals(NONE, detector.update(0, -1, 11_000L, 11_000L));
        assertEquals(STOPPED, detector.update(0, -1, 11_000L, 12_500L));
    }

    @Test
    public void yuriyTraceKeepsZeroSpeedUntilRpmZeroArrivesTwentyFiveSecondsLater() {
        EngineRunDetector detector = new EngineRunDetector(true);
        assertEquals(NONE, detector.update(800, 0, 10_000L, 10_000L));
        assertEquals(NONE, detector.update(0, -1, 35_000L, 35_000L));
        assertEquals(STOPPED, detector.update(0, -1, 35_000L, 36_500L));
    }

    @Test
    public void unknownSpeedAloneCannotStartAStopCandidate() {
        EngineRunDetector detector = new EngineRunDetector(true);
        assertEquals(NONE, detector.update(0, -1, 10_000L, 10_000L));
        assertEquals(NONE, detector.update(0, -1, 10_000L, 60_000L));
    }

    @Test
    public void oldStationaryEvidenceCannotStartAStopAfterProlongedDataLoss() {
        EngineRunDetector detector = new EngineRunDetector(true);
        assertEquals(NONE, detector.update(800, 0, 1_000L, 1_000L));
        assertEquals(NONE, detector.update(0, -1, 61_001L, 61_001L));
        assertEquals(NONE, detector.update(0, -1, 61_001L, 70_000L));
    }

    @Test
    public void newMovementRevokesStationaryEvidenceEvenIfSpeedDisappearsAgain() {
        EngineRunDetector detector = new EngineRunDetector(true);
        assertEquals(NONE, detector.update(0, 0, 1_000L, 1_000L));
        assertEquals(NONE, detector.update(0, 1, 1_000L, 1_500L));
        assertEquals(NONE, detector.update(0, -1, 1_000L, 3_000L));
        assertEquals(NONE, detector.update(0, -1, 1_000L, 10_000L));
    }

    @Test
    public void runningRpmStillCancelsStopAndSilenceNeverManufacturesStoppedRpm() {
        EngineRunDetector detector = new EngineRunDetector(true);
        assertEquals(NONE, detector.update(0, 0, 1_000L, 1_000L));
        assertEquals(NONE, detector.update(800, -1, 1_500L, 1_500L));
        assertEquals(NONE, detector.update(800, -1, 1_500L, 60_000L));
    }

    @Test
    public void nextEngineRunCannotReusePreviousRunsStationaryEvidence() {
        EngineRunDetector detector = new EngineRunDetector(true);
        assertEquals(NONE, detector.update(0, 0, 1_000L, 1_000L));
        assertEquals(STOPPED, detector.update(0, -1, 1_000L, 2_500L));
        assertEquals(STARTING, detector.update(800, -1, 3_000L, 3_000L));
        assertEquals(STARTED, detector.update(800, -1, 4_000L, 4_000L));
        assertEquals(NONE, detector.update(0, -1, 4_500L, 4_500L));
        assertEquals(NONE, detector.update(0, -1, 4_500L, 6_000L));
    }

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
        assertEquals(NONE, detector.update(800, 0, 1_000L, 16_000L));
        assertEquals(NONE, detector.update(800, 0, 1_000L, 31_000L));
        assertEquals(NONE, detector.update(800, 0, 1_000L, 61_000L));
    }

    @Test
    public void rpmSilenceOfAnyLengthNeverStopsTrip() {
        EngineRunDetector detector = new EngineRunDetector(true);

        for (long nowMs = 2_000L; nowMs <= 600_000L; nowMs += 1_000L) {
            assertEquals(NONE, detector.update(800, 0, 1_000L, nowMs));
        }
    }

    @Test
    public void tripStopsOnceRpmDropsToStopThresholdAfterLongSilence() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(800, 0, 1_000L, 61_000L));
        assertEquals(NONE, detector.update(50, 0, 62_000L, 62_000L));
        assertEquals(NONE, detector.update(50, 0, 63_499L, 63_499L));
        assertEquals(STOPPED, detector.update(50, 0, 63_500L, 63_500L));
    }

    @Test
    public void staleRpmBelowStopThresholdStillConfirmsShutdown() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(0, 0, 1_000L, 2_001L));
        assertEquals(STOPPED, detector.update(0, 0, 1_000L, 3_501L));
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

    @Test
    public void confirmedTboxEngineStopInParkStopsWithUnknownSpeedAndRpm() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(
                800, -1, 1_000L,
                0, 2_000L, true, true, 2_000L,
                2_000L));
        assertEquals(STOPPED, detector.update(
                800, -1, 1_000L,
                0, 2_000L, true, true, 2_000L,
                3_500L));
    }

    @Test
    public void automaticEngineStopInDriveCannotEndTrip() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(
                0, 0, 2_000L,
                0, 2_000L, true, false, 2_000L,
                2_000L));
        assertEquals(NONE, detector.update(
                0, 0, 3_500L,
                0, 3_500L, true, false, 3_500L,
                3_500L));
    }

    @Test
    public void measuredMovementVetoesContradictoryTboxStopAndPark() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(
                800, 5, 1_000L,
                0, 2_000L, true, true, 2_000L,
                2_000L));
        assertEquals(NONE, detector.update(
                800, 5, 1_000L,
                0, 2_000L, true, true, 2_000L,
                5_000L));
    }

    @Test
    public void staleTboxEvidenceCannotEndTrip() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(
                800, -1, 1_000L,
                0, 1_000L, true, true, 1_000L,
                16_001L));
    }

    @Test
    public void tboxEngineStopWithMeasuredZeroStillRequiresPark() {
        EngineRunDetector detector = new EngineRunDetector(true);

        assertEquals(NONE, detector.update(
                800, 0, 1_000L,
                0, 2_000L, false, false, 0L,
                2_000L));
        assertEquals(NONE, detector.update(
                800, 0, 1_000L,
                0, 3_500L, true, true, 3_500L,
                3_500L));
        assertEquals(STOPPED, detector.update(
                800, 0, 1_000L,
                0, 3_500L, true, true, 3_500L,
                5_000L));
    }
}
