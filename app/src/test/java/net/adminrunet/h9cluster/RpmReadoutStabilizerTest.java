package net.adminrunet.h9cluster;

import static org.junit.Assert.*;
import org.junit.Test;

public final class RpmReadoutStabilizerTest {
    @Test
    public void idleNoiseDoesNotAlternateTheLastDigitAtTheRoundingBoundary() {
        RpmReadoutStabilizer digits = new RpmReadoutStabilizer();
        assertEquals(750, digits.update(753, 0));
        for (int i = 1; i <= 100; i++) {
            assertEquals(750, digits.update(i % 2 == 0 ? 754 : 757, i * 20));
        }
        assertEquals(770, digits.update(770, 2200));
    }

    @Test
    public void acceleratingGaugeCanMoveEveryFrameButDigitsHoldForAtLeast160Ms() {
        RpmReadoutStabilizer digits = new RpmReadoutStabilizer();
        int previous = digits.update(2000, 0);
        long previousChange = 0;
        int changes = 0;
        for (long ms = 16; ms <= 1600; ms += 16) {
            int next = digits.update(2000 + ms, ms);
            if (next != previous) {
                assertTrue(ms - previousChange >= 160);
                assertTrue(next > previous);
                previousChange = ms;
                previous = next;
                changes++;
            }
        }
        assertEquals(10, changes);
        assertEquals(3600, previous);
    }

    @Test
    public void aPendingReadingSettlesWithoutAnotherBusMessage() {
        RpmReadoutStabilizer digits = new RpmReadoutStabilizer();
        assertEquals(2000, digits.update(2000, 1000));
        assertEquals(2000, digits.update(2400, 1100));
        assertTrue(digits.needsUpdate(2400));
        assertEquals(2400, digits.update(2400, 1160));
        assertFalse(digits.needsUpdate(2400));
    }

    @Test
    public void stoppedEngineAndInvalidInputsNeverLeaveStaleDigits() {
        RpmReadoutStabilizer digits = new RpmReadoutStabilizer();
        assertEquals(8000, digits.update(9000, 1000));
        assertEquals(0, digits.update(0, 1001));
        assertEquals(750, digits.update(750, 2000));
        assertEquals(0, digits.update(Float.NaN, 2001));
        assertEquals(2300, digits.update(2300, 10));
    }
}
