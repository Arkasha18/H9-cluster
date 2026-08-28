package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import net.adminrunet.h9cluster.trip.TripSession;
import net.adminrunet.h9cluster.trip.TripSessionPersistence;
import net.adminrunet.h9cluster.trip.TripSummary;
import net.adminrunet.h9cluster.trip.TripSummaryCoordinator;

import org.junit.Test;

public final class DemoClusterDataSourceTest {
    @Test
    public void emitsImmediatelyAndThenAtTwentyFramesPerSecond() {
        FakeClock clock = new FakeClock(1_000L);
        FakeScheduler scheduler = new FakeScheduler();
        DemoClusterDataSource source = new DemoClusterDataSource(
                new DemoScenario(),
                clock,
                scheduler);
        List<ClusterState> received = new ArrayList<>();

        source.start(received::add);
        scheduler.runImmediate();

        assertEquals(1, received.size());
        assertEquals(0, received.get(0).speedKph);
        assertEquals(50L, scheduler.lastDelayMs);

        clock.nowMs = 4_000L;
        scheduler.runDelayed();

        assertEquals(2, received.size());
        assertEquals(35, received.get(1).speedKph);
        assertEquals(50L, scheduler.lastDelayMs);
    }

    @Test
    public void repeatedStartDoesNotCreateAnotherUpdateLoop() {
        FakeClock clock = new FakeClock(1_000L);
        FakeScheduler scheduler = new FakeScheduler();
        DemoClusterDataSource source = new DemoClusterDataSource(
                new DemoScenario(),
                clock,
                scheduler);
        List<ClusterState> firstListenerValues = new ArrayList<>();
        List<ClusterState> secondListenerValues = new ArrayList<>();

        source.start(firstListenerValues::add);
        source.start(secondListenerValues::add);
        scheduler.runImmediate();

        assertEquals(1, scheduler.postCount);
        assertEquals(1, firstListenerValues.size());
        assertTrue(secondListenerValues.isEmpty());
    }

    @Test
    public void stopCancelsPendingDeliveryAndIsIdempotent() {
        FakeClock clock = new FakeClock(1_000L);
        FakeScheduler scheduler = new FakeScheduler();
        DemoClusterDataSource source = new DemoClusterDataSource(
                new DemoScenario(),
                clock,
                scheduler);
        List<ClusterState> received = new ArrayList<>();

        source.start(received::add);
        scheduler.runImmediate();
        source.stop();
        source.stop();
        scheduler.runDelayedIfPresent();

        assertEquals(1, received.size());
        assertTrue(scheduler.removed);
        assertNull(scheduler.delayedTask);
    }

    @Test
    public void restartBeginsScenarioFromTheStart() {
        FakeClock clock = new FakeClock(1_000L);
        FakeScheduler scheduler = new FakeScheduler();
        DemoClusterDataSource source = new DemoClusterDataSource(
                new DemoScenario(),
                clock,
                scheduler);
        List<ClusterState> received = new ArrayList<>();

        source.start(received::add);
        scheduler.runImmediate();
        clock.nowMs = 9_000L;
        source.stop();
        source.start(received::add);
        scheduler.runImmediate();

        assertEquals(2, received.size());
        assertEquals(0, received.get(0).speedKph);
        assertEquals(0, received.get(1).speedKph);
        assertEquals(9_000L, received.get(1).rpmUpdatedAtMs);
    }

    @Test
    public void nullListenerIsRejectedWithoutSchedulingWork() {
        FakeScheduler scheduler = new FakeScheduler();
        DemoClusterDataSource source = new DemoClusterDataSource(
                new DemoScenario(),
                new FakeClock(1_000L),
                scheduler);

        try {
            source.start(null);
            fail("Expected a null listener to be rejected");
        } catch (NullPointerException expected) {
            assertEquals("listener", expected.getMessage());
        }

        assertEquals(0, scheduler.postCount);
    }

    @Test
    public void engineStopRequestIsIdempotentAndKeepsFreshTicks() {
        FakeClock clock = new FakeClock(1_000L);
        FakeScheduler scheduler = new FakeScheduler();
        DemoClusterDataSource source = new DemoClusterDataSource(
                new DemoScenario(),
                clock,
                scheduler);
        List<ClusterState> received = new ArrayList<>();
        source.start(received::add);
        scheduler.runImmediate();
        clock.nowMs = 5_000L;
        scheduler.runDelayed();
        float distanceAtRequest = received.get(received.size() - 1).dayKm;

        assertTrue(source.requestEngineStop());
        assertFalse(source.requestEngineStop());
        clock.nowMs = 8_000L;
        scheduler.runDelayed();

        ClusterState stopped = received.get(received.size() - 1);
        assertEquals(0, stopped.speedKph);
        assertEquals(0, stopped.rpm);
        assertEquals(0, stopped.currentGear);
        assertEquals(distanceAtRequest, stopped.dayKm, 0.0f);
        assertEquals(8_000L, stopped.rpmUpdatedAtMs);
        assertEquals(50L, scheduler.lastDelayMs);
    }

    @Test
    public void stopRequestUsesRealOneAndHalfSecondCoordinatorHold() {
        FakeClock clock = new FakeClock(1_000L);
        FakeScheduler scheduler = new FakeScheduler();
        DemoClusterDataSource source = new DemoClusterDataSource(
                new DemoScenario(),
                clock,
                scheduler);
        MemoryPersistence persistence = new MemoryPersistence();
        List<TripSummary> summaries = new ArrayList<>();
        TripSummaryCoordinator coordinator = new TripSummaryCoordinator(
                persistence,
                summaries::add,
                clock::nowMs);
        source.start(coordinator::onClusterState);
        scheduler.runImmediate();
        clock.nowMs = 2_000L;
        scheduler.runDelayed();
        assertTrue(coordinator.isTripActive());

        assertTrue(source.requestEngineStop());
        scheduler.runDelayed();
        clock.nowMs = 3_499L;
        scheduler.runDelayed();
        assertTrue(summaries.isEmpty());

        clock.nowMs = 3_500L;
        scheduler.runDelayed();
        assertEquals(1, summaries.size());
    }

    @Test
    public void invalidConsumptionModeProducesIndependentInvalidMetric() {
        FakeClock clock = new FakeClock(1_000L);
        FakeScheduler scheduler = new FakeScheduler();
        DemoClusterDataSource source = new DemoClusterDataSource(
                new DemoScenario(),
                clock,
                scheduler,
                true);
        List<ClusterState> received = new ArrayList<>();
        source.start(received::add);
        scheduler.runImmediate();

        assertTrue(Float.isNaN(
                received.get(0).journeyAverageFuelConsumption));
        assertTrue(Float.isNaN(
                received.get(0).instantFuelConsumption));
        assertTrue(Float.isNaN(
                received.get(0).consumptionLitersPer100Km));
        assertTrue(received.get(0).dayKm > 0.0f);
    }

    private static final class FakeClock
            implements DemoClusterDataSource.Clock {
        private long nowMs;

        FakeClock(long nowMs) {
            this.nowMs = nowMs;
        }

        @Override
        public long nowMs() {
            return nowMs;
        }
    }

    private static final class FakeScheduler
            implements DemoClusterDataSource.Scheduler {
        private Runnable immediateTask;
        private Runnable delayedTask;
        private long lastDelayMs = -1L;
        private int postCount;
        private boolean removed;

        @Override
        public void post(Runnable task) {
            postCount++;
            immediateTask = task;
        }

        @Override
        public void postDelayed(Runnable task, long delayMs) {
            delayedTask = task;
            lastDelayMs = delayMs;
        }

        @Override
        public void removeCallbacks(Runnable task) {
            removed = true;
            if (immediateTask == task) {
                immediateTask = null;
            }
            if (delayedTask == task) {
                delayedTask = null;
            }
        }

        void runImmediate() {
            Runnable task = immediateTask;
            immediateTask = null;
            if (task != null) {
                task.run();
            }
        }

        void runDelayed() {
            Runnable task = delayedTask;
            delayedTask = null;
            if (task == null) {
                fail("No delayed task was scheduled");
            }
            task.run();
        }

        void runDelayedIfPresent() {
            Runnable task = delayedTask;
            delayedTask = null;
            if (task != null) {
                task.run();
            }
        }
    }

    private static final class MemoryPersistence
            implements TripSessionPersistence {
        private TripSession session;

        @Override
        public TripSession load(long nowMs) {
            return session;
        }

        @Override
        public void saveAsync(TripSession session) {
            this.session = session;
        }

        @Override
        public boolean saveSync(TripSession session) {
            this.session = session;
            return true;
        }

        @Override
        public void clearAsync() {
            session = null;
        }

        @Override
        public boolean clearSync() {
            session = null;
            return true;
        }
    }
}
