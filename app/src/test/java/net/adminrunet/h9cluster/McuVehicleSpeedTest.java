package net.adminrunet.h9cluster;

import static org.junit.Assert.*;
import org.junit.Test;

public final class McuVehicleSpeedTest {
    @Test public void decodesRecordedH9MovementAndStop() {
        assertEquals(2.98125f, McuVehicleSpeed.decode(hex("002035041E0000000000000000000000")), 0.00001f);
        assertEquals(1.96875f, McuVehicleSpeed.decode(hex("00202304200000000000000000000000")), 0.00001f);
        assertEquals(0, McuVehicleSpeed.decode(hex("00200004220000000000000000000000")), 0);
    }

    @Test public void usesAllThirteenBitsWithoutMixingInValidity() {
        assertEquals(57.6f, McuVehicleSpeed.decode(hex("0024000000000000")), 0.00001f);
        assertEquals(299.98125f, McuVehicleSpeed.decode(hex("0034D50000000000")), 0.0001f);
        assertTrue(Float.isNaN(McuVehicleSpeed.decode(hex("0034D60000000000"))));
        assertTrue(Float.isNaN(McuVehicleSpeed.decode(hex("003FFF0000000000"))));
    }

    @Test public void rejectsInvalidFlagAndUnsupportedLengths() {
        assertTrue(Float.isNaN(McuVehicleSpeed.decode(hex("0004000000000000"))));
        assertTrue(Float.isNaN(McuVehicleSpeed.decode(null)));
        for (int size : new int[] {0, 2, 3, 7, 9, 15, 17, 64}) {
            byte[] bytes = new byte[size];
            if (size > 1) bytes[1] = 0x20;
            assertTrue(Float.isNaN(McuVehicleSpeed.decode(bytes)));
        }
    }

    @Test public void preservesChannelAndRejectsBrokenProtobufLengths() {
        byte[] packet = hex("0A1700001000000265002035041E00000000000000000000001017");
        McuCanFrame frame = McuCanFrame.decode(packet);
        assertNotNull(frame);
        assertEquals(0, frame.channel);
        assertEquals(McuVehicleSpeed.CAN_ID, frame.id);
        assertEquals(16, frame.data.length);
        packet[2] = 7;
        assertEquals(7, McuCanFrame.decode(packet).channel);
        packet[4] = 8;
        assertNull(McuCanFrame.decode(packet));
        assertNull(McuCanFrame.decode(null));
        assertNull(McuCanFrame.decode(hex("0AFFFFFFFF0F00000000000000")));
        assertNull(McuCanFrame.decode(hex("0A8080808080000000000000")));
        assertNull(McuCanFrame.decode(hex("0A1700001000000265002035")));
    }

    static byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
