package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public final class TboxSnapshotFramerTest {
    @Test
    public void extractsDeclaredFrameFromOversizedSharedMemoryCapture() {
        byte[] capture = validCapture(4097);
        Arrays.fill(
                capture,
                TboxCanSnapshotDecoder.SNAPSHOT_LENGTH,
                capture.length,
                (byte) 0x5a);

        byte[] snapshot = TboxSnapshotFramer.extract(capture, capture.length);
        TboxCanSnapshotDecoder.Result decoded = TboxCanSnapshotDecoder.decode(snapshot);

        assertEquals(TboxCanSnapshotDecoder.SNAPSHOT_LENGTH, snapshot.length);
        assertTrue(decoded.validSnapshot);
        assertTrue(decoded.hasEngineState());
        assertEquals(TboxCanSnapshotDecoder.ENGINE_STOPPED, decoded.engineState);
        assertTrue(decoded.parkValid);
        assertTrue(decoded.inPark);
    }

    @Test
    public void preservesOversizedCaptureWhenDeclaredLengthIsNotExpected() {
        byte[] capture = validCapture(4097);
        putLittleEndianInt(capture, 0, 2296);

        byte[] snapshot = TboxSnapshotFramer.extract(capture, capture.length);
        TboxCanSnapshotDecoder.Result decoded = TboxCanSnapshotDecoder.decode(snapshot);

        assertEquals(capture.length, snapshot.length);
        assertFalse(decoded.validSnapshot);
        assertEquals("length", decoded.failureReason);
        assertEquals(capture.length, decoded.observedLength);
        assertEquals(2296, decoded.declaredLength);
    }

    @Test
    public void preservesTruncatedCaptureForStrictDecoderDiagnostics() {
        byte[] capture = validCapture(128);

        byte[] snapshot = TboxSnapshotFramer.extract(capture, capture.length);
        TboxCanSnapshotDecoder.Result decoded = TboxCanSnapshotDecoder.decode(snapshot);

        assertEquals(capture.length, snapshot.length);
        assertFalse(decoded.validSnapshot);
        assertEquals("length", decoded.failureReason);
        assertEquals(capture.length, decoded.observedLength);
    }

    private static byte[] validCapture(int length) {
        byte[] capture = new byte[length];
        putLittleEndianInt(capture, 0, 2152);
        capture[4] = 0x07;
        capture[5] = (byte) 0x94;
        capture[6] = 0x26;
        capture[7] = 0x11;
        capture[8] = 0x06;
        capture[9] = 0x12;
        capture[10] = 0x34;
        capture[11] = 0x56;
        capture[12] = 0x01;
        capture[13] = 0x43;
        capture[14] = 0x00;
        capture[15] = 0x02;
        return capture;
    }

    private static void putLittleEndianInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
    }
}
