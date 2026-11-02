package net.adminrunet.h9cluster;

import static org.junit.Assert.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class VehicleSpeedReplayTest {
    @Test public void recordedReverseStopForwardKeepsHealthyGwmPrimary() throws Exception {
        replay(false);
    }

    @Test public void recordedMcuMovementBypassesRepeatedFrozenGwmZeros() throws Exception {
        replay(true);
    }

    private static void replay(boolean frozenGwm) throws Exception {
        VehicleSpeedSelector selector = new VehicleSpeedSelector();
        InputStream input = getClassLoaderResource("vehicle-speed/h9-abs3-maneuver.csv");
        int movingChecks = 0;
        int mcuSelections = 0;
        long firstMovement = 0;
        int selected = -1;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) continue;
                String[] fields = line.split(",");
                long now = Long.parseLong(fields[0]);
                if ("CAN".equals(fields[1])) {
                    McuCanFrame frame = McuCanFrame.decode(McuVehicleSpeedTest.hex(fields[2]));
                    assertNotNull(frame);
                    assertEquals(McuVehicleSpeed.CHANNEL, frame.channel);
                    assertEquals(McuVehicleSpeed.CAN_ID, frame.id);
                    selector.onMcu(McuVehicleSpeed.decode(frame.data), now);
                } else {
                    float reference = Float.parseFloat(fields[2]);
                    selector.onGwm(frozenGwm ? 0 : reference, now, true);
                    if (reference > 0 && firstMovement == 0) firstMovement = now;
                    selected = selector.select(now, now);
                    if (reference >= 2 && now - firstMovement >= 1000) {
                        assertTrue("Moving speed at " + now, selected > 0);
                        movingChecks++;
                    }
                }
                // The capture also contains a continuous RPM/other-frame stream.
                selected = selector.select(now, now);
                if (selector.source() == VehicleSpeedSelector.Source.MCU) mcuSelections++;
                if (!frozenGwm) assertEquals(VehicleSpeedSelector.Source.GWM, selector.source());
            }
        }
        assertTrue(movingChecks >= 20);
        assertEquals(0, selected);
        if (frozenGwm) {
            assertTrue(mcuSelections > 100);
            assertEquals(VehicleSpeedSelector.Source.MCU, selector.source());
        }
    }

    private static InputStream getClassLoaderResource(String name) {
        InputStream input = VehicleSpeedReplayTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(input);
        return input;
    }
}
