package net.adminrunet.h9cluster;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Objects;

/** Main-thread source that feeds the renderers with a repeating demo drive. */
final class DemoClusterDataSource implements ClusterDataSource {
    interface Clock {
        long nowMs();
    }

    interface Scheduler {
        void post(Runnable task);

        void postDelayed(Runnable task, long delayMs);

        void removeCallbacks(Runnable task);
    }

    private static final long FRAME_MS = 50L;

    private final DemoScenario scenario;
    private final Clock clock;
    private final Scheduler scheduler;
    private final boolean invalidConsumption;

    private Listener listener;
    private boolean started;
    private long startedAtMs;
    private boolean engineStopRequested;
    private long frozenElapsedMs;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!started) {
                return;
            }
            long nowMs = clock.nowMs();
            Listener currentListener = listener;
            if (currentListener == null) {
                return;
            }
            ClusterState state = engineStopRequested
                    ? scenario.stoppedSnapshot(
                            frozenElapsedMs,
                            nowMs,
                            invalidConsumption)
                    : scenario.snapshot(
                            nowMs - startedAtMs,
                            nowMs,
                            invalidConsumption);
            currentListener.onClusterState(state);
            if (started) {
                scheduler.postDelayed(this, FRAME_MS);
            }
        }
    };

    DemoClusterDataSource(Context context) {
        this(context, false);
    }

    DemoClusterDataSource(Context context, boolean invalidConsumption) {
        this(
                new DemoScenario(),
                SystemClock::elapsedRealtime,
                mainThreadScheduler(),
                invalidConsumption);
    }

    DemoClusterDataSource(
            DemoScenario scenario,
            Clock clock,
            Scheduler scheduler) {
        this(scenario, clock, scheduler, false);
    }

    DemoClusterDataSource(
            DemoScenario scenario,
            Clock clock,
            Scheduler scheduler,
            boolean invalidConsumption) {
        this.scenario = Objects.requireNonNull(scenario, "scenario");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.invalidConsumption = invalidConsumption;
    }

    @Override
    public void start(Listener listener) {
        Objects.requireNonNull(listener, "listener");
        if (started) {
            return;
        }
        this.listener = listener;
        started = true;
        startedAtMs = clock.nowMs();
        engineStopRequested = false;
        frozenElapsedMs = 0L;
        scheduler.post(tick);
    }

    @Override
    public void stop() {
        started = false;
        scheduler.removeCallbacks(tick);
        listener = null;
    }

    boolean requestEngineStop() {
        if (!started || engineStopRequested) {
            return false;
        }
        frozenElapsedMs = Math.max(0L, clock.nowMs() - startedAtMs);
        engineStopRequested = true;
        return true;
    }

    private static Scheduler mainThreadScheduler() {
        Handler handler = new Handler(Looper.getMainLooper());
        return new Scheduler() {
            @Override
            public void post(Runnable task) {
                handler.post(task);
            }

            @Override
            public void postDelayed(Runnable task, long delayMs) {
                handler.postDelayed(task, delayMs);
            }

            @Override
            public void removeCallbacks(Runnable task) {
                handler.removeCallbacks(task);
            }
        };
    }
}
