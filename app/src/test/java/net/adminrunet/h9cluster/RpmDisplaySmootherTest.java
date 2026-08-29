package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RpmDisplaySmootherTest {
    @Test
    public void alternatingIdleSamplesDoNotFlickerBetweenTenths() {
        RpmDisplaySmoother smoother = new RpmDisplaySmoother();
        long nowMs = 1_000L;
        assertEquals(800.0f, smoother.update(800, 0, nowMs), 0.0f);

        for (int frame = 0; frame < 80; frame++) {
            nowMs += 50L;
            int rpm = frame % 2 == 0 ? 900 : 800;
            assertEquals(800.0f, smoother.update(rpm, 0, nowMs), 0.0f);
        }
    }

    @Test
    public void alternatingSevenHundredAndEightHundredDoesNotFlicker() {
        RpmDisplaySmoother smoother = new RpmDisplaySmoother();
        long nowMs = 1_000L;
        assertEquals(700.0f, smoother.update(700, 0, nowMs), 0.0f);

        for (int frame = 0; frame < 80; frame++) {
            nowMs += 50L;
            int rpm = frame % 2 == 0 ? 800 : 700;
            assertEquals(700.0f, smoother.update(rpm, 0, nowMs), 0.0f);
        }
    }

    @Test
    public void alternatingEightHundredAndSevenHundredKeepsInitialValue() {
        RpmDisplaySmoother smoother = new RpmDisplaySmoother();
        long nowMs = 1_000L;
        assertEquals(800.0f, smoother.update(800, 0, nowMs), 0.0f);

        for (int frame = 0; frame < 80; frame++) {
            nowMs += 50L;
            int rpm = frame % 2 == 0 ? 700 : 800;
            assertEquals(800.0f, smoother.update(rpm, 0, nowMs), 0.0f);
        }
    }

    @Test
    public void sustainedIdleChangeMovesToNewTenthPromptly() {
        RpmDisplaySmoother smoother = new RpmDisplaySmoother();
        long nowMs = 1_000L;
        smoother.update(800, 0, nowMs);

        float displayed = 800.0f;
        for (int frame = 0; frame < 6; frame++) {
            nowMs += 50L;
            displayed = smoother.update(900, 0, nowMs);
        }

        assertEquals(900.0f, displayed, 0.0f);
    }

    @Test
    public void sustainedSevenHundredToEightHundredChangeIsAcceptedPromptly() {
        RpmDisplaySmoother smoother = new RpmDisplaySmoother();
        long nowMs = 1_000L;
        smoother.update(700, 0, nowMs);

        float displayed = 700.0f;
        for (int frame = 0; frame < 5; frame++) {
            nowMs += 50L;
            displayed = smoother.update(800, 0, nowMs);
        }

        assertEquals(800.0f, displayed, 0.0f);
    }

    @Test
    public void twoTenthIdleIncreaseIsImmediate() {
        RpmDisplaySmoother smoother = new RpmDisplaySmoother();
        long nowMs = 1_000L;
        smoother.update(700, 0, nowMs);

        assertEquals(900.0f, smoother.update(900, 0, nowMs + 50L), 0.0f);
    }

    @Test
    public void alternatingNineHundredAndOneThousandDoesNotFlicker() {
        RpmDisplaySmoother smoother = new RpmDisplaySmoother();
        long nowMs = 1_000L;
        assertEquals(900.0f, smoother.update(900, 0, nowMs), 0.0f);

        for (int frame = 0; frame < 80; frame++) {
            nowMs += 50L;
            int rpm = frame % 2 == 0 ? 1000 : 900;
            assertEquals(900.0f, smoother.update(rpm, 0, nowMs), 0.0f);
        }
    }

    @Test
    public void sustainedOneThousandIsStillAllowedAfterConfirmation() {
        RpmDisplaySmoother smoother = new RpmDisplaySmoother();
        long nowMs = 1_000L;
        smoother.update(900, 0, nowMs);

        float displayed = 900.0f;
        for (int frame = 0; frame < 6; frame++) {
            nowMs += 50L;
            displayed = smoother.update(1000, 0, nowMs);
        }

        assertEquals(1000.0f, displayed, 0.0f);
    }

    @Test
    public void lightThrottleOutsideIdleFilterIsImmediate() {
        RpmDisplaySmoother smoother = new RpmDisplaySmoother();
        long nowMs = 1_000L;
        smoother.update(800, 0, nowMs);

        assertEquals(1100.0f, smoother.update(1100, 0, nowMs + 50L), 0.0f);
        assertEquals(1050.0f, smoother.update(1050, 0, nowMs + 100L), 0.0f);
    }

    @Test
    public void gradualLightThrottleDoesNotWaitForEveryIntermediateTenth() {
        RpmDisplaySmoother smoother = new RpmDisplaySmoother();
        long nowMs = 1_000L;
        smoother.update(700, 0, nowMs);

        assertEquals(700.0f, smoother.update(800, 0, nowMs + 50L), 0.0f);
        assertEquals(900.0f, smoother.update(900, 0, nowMs + 100L), 0.0f);
        assertEquals(1050.0f, smoother.update(1050, 0, nowMs + 150L), 0.0f);
    }

    @Test
    public void movingEngineUsesRawRpmWithoutAddedLag() {
        RpmDisplaySmoother smoother = new RpmDisplaySmoother();
        long nowMs = 1_000L;
        smoother.update(900, 0, nowMs);

        assertEquals(3000.0f, smoother.update(3000, 40, nowMs + 50L), 0.0f);
    }

    @Test
    public void confirmedEngineStopSnapsToZero() {
        RpmDisplaySmoother smoother = new RpmDisplaySmoother();
        smoother.update(850, 0, 1_000L);

        assertEquals(0.0f, smoother.update(0, 0, 1_050L), 0.0f);
    }

    @Test
    public void idleDebounceDoesNotCreateAnimationLag() {
        RpmDisplaySmoother smoother = new RpmDisplaySmoother();
        smoother.update(800, 0, 1_000L);
        smoother.update(900, 0, 1_050L);

        assertFalse(smoother.needsAnimationFrame(900, 0));
    }
}
