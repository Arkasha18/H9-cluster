package net.adminrunet.h9cluster;

import static org.junit.Assert.*;
import org.junit.Test;

public final class VehicleSpeedSelectorTest {
    @Test public void standstillIsValidAndDoesNotTriggerFailover() {
        VehicleSpeedSelector s = new VehicleSpeedSelector();
        s.onMcu(0, 1000);
        for (long t = 1000; t <= 10000; t += 100) {
            s.onGwm(0, t, true);
            assertEquals(0, s.select(t, t));
            assertEquals(VehicleSpeedSelector.Source.GWM, s.source());
        }
    }

    @Test public void freshCallbacksCannotKeepFrozenZeroInCharge() {
        VehicleSpeedSelector s = frozen(0, 35);
        assertEquals(VehicleSpeedSelector.Source.MCU, s.source());
        assertEquals(35, s.select(3000, 3000));
        s.onMcu(0, 3100);
        s.onGwm(0, 3100, true);
        assertEquals(0, s.select(3100, 3100));
        // A matching stop does not prove that the frozen GWM source recovered.
        s.onGwm(0, 4500, true);
        assertEquals(0, s.select(4500, 4500));
        assertEquals(VehicleSpeedSelector.Source.MCU, s.source());
    }

    @Test public void frozenNonzeroAndTransportLossDoNotDisplayOldSpeed() {
        VehicleSpeedSelector s = frozen(40, 20);
        assertEquals(20, s.select(3000, 3000));
        s.onGwm(40, 4101, true);
        assertEquals(-1, s.select(4101, 3000));
        assertEquals(VehicleSpeedSelector.Source.UNAVAILABLE, s.source());
    }

    @Test public void changedGwmMustAgreeForAFullRecoveryInterval() {
        VehicleSpeedSelector s = frozen(0, 35);
        s.onGwm(35, 3100, true);
        assertEquals(35, s.select(3100, 3100));
        assertEquals(VehicleSpeedSelector.Source.MCU, s.source());
        s.onGwm(35, 4099, true);
        s.select(4099, 4099);
        assertEquals(VehicleSpeedSelector.Source.MCU, s.source());
        s.onGwm(35, 4100, true);
        s.select(4100, 4100);
        assertEquals(VehicleSpeedSelector.Source.GWM, s.source());
    }

    @Test public void shortSourceLatencyDoesNotRejectHealthyGwm() {
        VehicleSpeedSelector s = new VehicleSpeedSelector();
        s.onGwm(20, 1000, true);
        s.onMcu(20, 1000);
        s.select(2500, 2500);
        s.onMcu(15, 2600);
        assertEquals(20, s.select(2600, 2600));
        s.onGwm(15, 2700, true);
        assertEquals(15, s.select(2700, 2700));
        assertEquals(VehicleSpeedSelector.Source.GWM, s.source());
    }

    @Test public void recoveredPrimaryCanTakeOverAfterMcuDisconnects() {
        VehicleSpeedSelector s = frozen(0, 35);
        s.onGwm(34, 3100, true);
        assertEquals(-1, s.select(3100, 0));
        s.onGwm(33, 3200, true);
        assertEquals(-1, s.select(3200, 0));
        s.onGwm(32, 4200, true);
        assertEquals(32, s.select(4200, 0));
        assertEquals(VehicleSpeedSelector.Source.GWM, s.source());
    }

    @Test public void rereadingAnIdenticalCacheDoesNotRenewMeasurementAge() {
        VehicleSpeedSelector s = new VehicleSpeedSelector();
        s.onGwm(48, 1000, false);
        s.onGwm(48, 3001, false);
        assertEquals(-1, s.select(3001, 0));
        s.onMcu(42, 3002);
        assertEquals(42, s.select(3002, 3002));
        s.onGwm(44, 3100, false);
        assertEquals(44, s.select(3100, 3100));
    }

    @Test public void onChangeSpeedSurvivesQuietAbs3OnlyWhileStreamIsAlive() {
        VehicleSpeedSelector s = new VehicleSpeedSelector();
        s.onMcu(50, 1000);
        assertEquals(50, s.select(10000, 10000));
        assertEquals(-1, s.select(11001, 10000));
        assertEquals(-1, s.select(11002, 0));
        s.onMcu(Float.NaN, 0); // FDBus reconnect invalidates the old session's value.
        assertEquals(-1, s.select(12000, 12000));
        s.onMcu(0, 12001);
        assertEquals(0, s.select(12001, 12001));
    }

    @Test public void invalidSamplesAndFutureTimestampsAreUnavailable() {
        for (float invalid : new float[] {-1, 301, Float.NaN, Float.POSITIVE_INFINITY}) {
            VehicleSpeedSelector s = new VehicleSpeedSelector();
            s.onGwm(invalid, 1000, true);
            s.onMcu(invalid, 1000);
            assertEquals(-1, s.select(1000, 1000));
        }
        VehicleSpeedSelector s = new VehicleSpeedSelector();
        s.onGwm(20, 2000, true);
        s.onMcu(20, 2000);
        assertEquals(-1, s.select(1000, 2000));
    }

    @Test public void invalidGwmCannotBeRevivedByTheSameCachedValue() {
        VehicleSpeedSelector s = new VehicleSpeedSelector();
        s.onGwm(48, 1000, true);
        s.onGwm(Float.NaN, 1100, true);
        s.onGwm(48, 1200, false);
        assertEquals(-1, s.select(1200, 0));
        s.onGwm(48, 1300, true);
        assertEquals(48, s.select(1300, 0));
        s.reset();
        assertEquals(-1, s.select(1400, 1400));
    }

    private static VehicleSpeedSelector frozen(float primary, float secondary) {
        VehicleSpeedSelector s = new VehicleSpeedSelector();
        s.onGwm(primary, 1000, true);
        for (long t = 2000; t <= 3000; t += 100) {
            s.onGwm(primary, t, true);
            s.onMcu(secondary, t);
            s.select(t, t);
        }
        return s;
    }
}
