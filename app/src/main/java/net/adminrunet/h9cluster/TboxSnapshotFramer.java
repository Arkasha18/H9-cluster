package net.adminrunet.h9cluster;

import java.util.Arrays;

/** Extracts one strict TBOX frame from the bounded shared-memory capture. */
final class TboxSnapshotFramer {
    private static final int LENGTH_PREFIX_BYTES = 4;
    private static final int EXPECTED_PAYLOAD_LENGTH =
            TboxCanSnapshotDecoder.SNAPSHOT_LENGTH - LENGTH_PREFIX_BYTES;

    private TboxSnapshotFramer() {
    }

    static byte[] extract(byte[] capture, int observedLength) {
        if (capture == null) {
            return null;
        }

        int boundedLength = Math.max(0, Math.min(observedLength, capture.length));
        if (boundedLength >= TboxCanSnapshotDecoder.SNAPSHOT_LENGTH
                && littleEndianInt(capture, 0) == EXPECTED_PAYLOAD_LENGTH) {
            return Arrays.copyOf(capture, TboxCanSnapshotDecoder.SNAPSHOT_LENGTH);
        }
        return Arrays.copyOf(capture, boundedLength);
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }
}
