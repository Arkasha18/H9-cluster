package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TboxCanSnapshotDecoderTest {
    private static final int RECORD_OFFSET = 16 + 9 * 214;

    @Test
    public void decodesTemperatureEngineParkAndEpbFromVerifiedSnapshot() {
        byte[] snapshot = snapshot(2, 0, 1, 120);

        TboxCanSnapshotDecoder.Result result = TboxCanSnapshotDecoder.decode(snapshot);

        assertTrue(result.validSnapshot);
        assertEquals("", result.failureReason);
        assertEquals("available", result.temperatureStatus);
        assertEquals(80.0f, result.temperatureC, 0.0f);
        assertEquals(120, result.rawTemperature);
        assertTrue(result.hasEngineState());
        assertEquals(TboxCanSnapshotDecoder.ENGINE_RUNNING, result.engineState);
        assertTrue(result.parkValid);
        assertTrue(result.inPark);
        assertTrue(result.epbValid);
        assertTrue(result.epbEngaged);
        assertEquals(0x261105123456L, result.generation);
    }

    @Test
    public void engineOffRawZeroTemperatureIsUnavailableInsteadOfMinusForty() {
        TboxCanSnapshotDecoder.Result result =
                TboxCanSnapshotDecoder.decode(snapshot(0, 0, 1, 0));

        assertTrue(result.validSnapshot);
        assertEquals("engine_off_sentinel", result.temperatureStatus);
        assertFalse(result.hasTemperature());
        assertEquals(TboxCanSnapshotDecoder.ENGINE_STOPPED, result.engineState);
    }

    @Test
    public void unavailableTemperatureDoesNotDiscardVehicleState() {
        TboxCanSnapshotDecoder.Result result =
                TboxCanSnapshotDecoder.decode(snapshot(0, 0, 1, 0xff));

        assertTrue(result.validSnapshot);
        assertEquals("source_unavailable", result.temperatureStatus);
        assertFalse(result.hasTemperature());
        assertTrue(result.hasEngineState());
        assertTrue(result.parkValid);
        assertTrue(result.inPark);
    }

    @Test
    public void neutralAndReleasedEpbRemainValidIndependentSignals() {
        TboxCanSnapshotDecoder.Result result =
                TboxCanSnapshotDecoder.decode(snapshot(2, 2, 0, 100));

        assertTrue(result.validSnapshot);
        assertTrue(result.parkValid);
        assertFalse(result.inPark);
        assertTrue(result.epbValid);
        assertFalse(result.epbEngaged);
    }

    @Test
    public void unknownDescriptorValuesFailClosedWithoutRejectingSnapshot() {
        TboxCanSnapshotDecoder.Result result =
                TboxCanSnapshotDecoder.decode(snapshot(1, 1, 3, 100));

        assertTrue(result.validSnapshot);
        assertFalse(result.hasEngineState());
        assertFalse(result.parkValid);
        assertFalse(result.epbValid);
    }

    @Test
    public void reportsExactStructuralRejectionReason() {
        byte[] snapshot = snapshot(2, 0, 1, 100);
        snapshot[12] = 0;
        TboxCanSnapshotDecoder.Result header = TboxCanSnapshotDecoder.decode(snapshot);
        assertFalse(header.validSnapshot);
        assertEquals("header", header.failureReason);
        assertEquals("0794/00430002", header.headerSignature);

        snapshot = snapshot(2, 0, 1, 100);
        snapshot[7] = 0x1a;
        TboxCanSnapshotDecoder.Result timestamp = TboxCanSnapshotDecoder.decode(snapshot);
        assertFalse(timestamp.validSnapshot);
        assertEquals("timestamp", timestamp.failureReason);

        snapshot = snapshot(2, 0, 1, 100);
        snapshot[0] = 0;
        TboxCanSnapshotDecoder.Result declared = TboxCanSnapshotDecoder.decode(snapshot);
        assertFalse(declared.validSnapshot);
        assertEquals("declared_length", declared.failureReason);

        TboxCanSnapshotDecoder.Result shortSnapshot =
                TboxCanSnapshotDecoder.decode(new byte[32]);
        assertFalse(shortSnapshot.validSnapshot);
        assertEquals("length", shortSnapshot.failureReason);
        assertEquals(32, shortSnapshot.observedLength);
    }

    @Test
    public void legacyTemperatureFacadeUsesStrictSnapshotDecoder() {
        assertEquals(65.0f,
                TboxTransmissionTemperatureDecoder.decodeCelsius(
                        snapshot(2, 0, 1, 105)),
                0.0f);
        assertTrue(Float.isNaN(
                TboxTransmissionTemperatureDecoder.decodeCelsius(new byte[2156])));
    }

    private static byte[] snapshot(
            int engineState,
            int parkState,
            int epbState,
            int rawTemperature) {
        byte[] snapshot = new byte[TboxCanSnapshotDecoder.SNAPSHOT_LENGTH];
        putLittleEndianInt(snapshot, 0, 2152);
        snapshot[4] = 0x07;
        snapshot[5] = (byte) 0x94;
        snapshot[6] = 0x26;
        snapshot[7] = 0x11;
        snapshot[8] = 0x05;
        snapshot[9] = 0x12;
        snapshot[10] = 0x34;
        snapshot[11] = 0x56;
        snapshot[12] = 0x01;
        snapshot[13] = 0x43;
        snapshot[14] = 0x00;
        snapshot[15] = 0x02;
        putPackedMsb(snapshot, 0, 2, engineState);
        putPackedMsb(snapshot, 736, 2, parkState);
        putPackedMsb(snapshot, 1484, 2, epbState);
        snapshot[RECORD_OFFSET + 0x45] = (byte) ((rawTemperature >>> 3) & 0x1f);
        snapshot[RECORD_OFFSET + 0x46] = (byte) ((rawTemperature & 0x07) << 5);
        return snapshot;
    }

    private static void putPackedMsb(
            byte[] snapshot,
            int bitOffset,
            int width,
            int value) {
        for (int index = 0; index < width; index++) {
            int current = bitOffset + index;
            int mask = 1 << (7 - current % 8);
            int byteOffset = RECORD_OFFSET + current / 8;
            if (((value >>> (width - 1 - index)) & 1) != 0) {
                snapshot[byteOffset] = (byte) (snapshot[byteOffset] | mask);
            } else {
                snapshot[byteOffset] = (byte) (snapshot[byteOffset] & ~mask);
            }
        }
    }

    private static void putLittleEndianInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
    }
}
