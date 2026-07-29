package net.adminrunet.h9cluster.trip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public final class TripSummaryDismissControllerTest {
    @Test
    public void timeoutAnimatesOutThenRemovesExactlyOnce() {
        FakeHost host = new FakeHost();
        TripSummaryDismissController controller =
                new TripSummaryDismissController(host);

        controller.attach();

        assertEquals(10_000L, host.delayMs);
        assertNotNull(host.scheduled);

        host.scheduled.run();
        controller.dismiss();

        assertEquals(1, host.cancelCalls);
        assertEquals(1, host.animateCalls);
        assertEquals(0, host.removeCalls);

        host.animationCompletion.run();
        host.animationCompletion.run();

        assertEquals(1, host.removeCalls);
    }

    @Test
    public void detachCancelsTimeoutAndSuppressesLateCallbacks() {
        FakeHost host = new FakeHost();
        TripSummaryDismissController controller =
                new TripSummaryDismissController(host);
        controller.attach();
        Runnable timeout = host.scheduled;

        controller.detach();
        timeout.run();

        assertEquals(1, host.cancelCalls);
        assertEquals(0, host.animateCalls);
        assertEquals(0, host.removeCalls);
    }

    private static final class FakeHost
            implements TripSummaryDismissController.Host {
        Runnable scheduled;
        Runnable animationCompletion;
        long delayMs;
        int cancelCalls;
        int animateCalls;
        int removeCalls;

        @Override
        public void schedule(Runnable task, long delayMs) {
            scheduled = task;
            this.delayMs = delayMs;
        }

        @Override
        public void cancel(Runnable task) {
            cancelCalls++;
        }

        @Override
        public void animateOut(Runnable completion) {
            animateCalls++;
            animationCompletion = completion;
        }

        @Override
        public void remove() {
            removeCalls++;
        }
    }
}
