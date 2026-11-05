package net.adminrunet.h9cluster;

/**
 * Strict, side-effect-free decoder for the stock TBOX {@code can_data_collect}
 * snapshot. The engine and Park mappings are shared with the independently
 * validated read-only implementation in {@code tbox-remote-control}.
 */
final class TboxCanSnapshotDecoder {
    static final int SNAPSHOT_LENGTH = 2156;
    static final int ENGINE_UNKNOWN = ClusterState.ENGINE_STATE_UNKNOWN;
    static final int ENGINE_STOPPED = ClusterState.ENGINE_STATE_STOPPED;
    static final int ENGINE_RUNNING = ClusterState.ENGINE_STATE_RUNNING;

    private static final int DECLARED_PAYLOAD_LENGTH = 2152;
    private static final int RECORDS_OFFSET = 16;
    private static final int RECORD_SIZE = 214;
    private static final int RECORD_COUNT = 10;
    private static final int NEWEST_RECORD_INDEX = RECORD_COUNT - 1;
    private static final int TEMPERATURE_BYTE_1 = 0x45;
    private static final int TEMPERATURE_BYTE_2 = 0x46;
    private static final int INVALID_RAW_TEMPERATURE = 0xff;
    private static final int TEMPERATURE_OFFSET_C = 40;
    private static final int MAX_RAW_TEMPERATURE = 240;
    private static final int ENGINE_STATE_BIT = 0;
    private static final int PARK_STATE_BIT = 736;
    private static final int EPB_STATE_BIT = 1484;

    static final class Result {
        final boolean validSnapshot;
        final String failureReason;
        final String temperatureStatus;
        final int observedLength;
        final int declaredLength;
        final String headerSignature;
        final String generationSignature;
        final String temperatureRecordsRaw;
        final long generation;
        final float temperatureC;
        final int rawTemperature;
        final int engineState;
        final int rawEngineState;
        final boolean parkValid;
        final boolean inPark;
        final int rawParkState;
        final boolean epbValid;
        final boolean epbEngaged;
        final int rawEpbState;

        private Result(
                boolean validSnapshot,
                String failureReason,
                String temperatureStatus,
                int observedLength,
                int declaredLength,
                String headerSignature,
                String generationSignature,
                String temperatureRecordsRaw,
                long generation,
                float temperatureC,
                int rawTemperature,
                int engineState,
                int rawEngineState,
                boolean parkValid,
                boolean inPark,
                int rawParkState,
                boolean epbValid,
                boolean epbEngaged,
                int rawEpbState) {
            this.validSnapshot = validSnapshot;
            this.failureReason = failureReason;
            this.temperatureStatus = temperatureStatus;
            this.observedLength = observedLength;
            this.declaredLength = declaredLength;
            this.headerSignature = headerSignature;
            this.generationSignature = generationSignature;
            this.temperatureRecordsRaw = temperatureRecordsRaw;
            this.generation = generation;
            this.temperatureC = temperatureC;
            this.rawTemperature = rawTemperature;
            this.engineState = engineState;
            this.rawEngineState = rawEngineState;
            this.parkValid = parkValid;
            this.inPark = inPark;
            this.rawParkState = rawParkState;
            this.epbValid = epbValid;
            this.epbEngaged = epbEngaged;
            this.rawEpbState = rawEpbState;
        }

        boolean hasTemperature() {
            return Float.isFinite(temperatureC);
        }

        boolean hasEngineState() {
            return engineState == ENGINE_STOPPED || engineState == ENGINE_RUNNING;
        }
    }

    private TboxCanSnapshotDecoder() {
    }

    static Result decode(byte[] snapshot) {
        int observedLength = snapshot == null ? 0 : snapshot.length;
        int declaredLength = snapshot != null && snapshot.length >= 4
                ? littleEndianInt(snapshot, 0)
                : -1;
        String headerSignature = headerSignature(snapshot);
        String generationSignature = generationSignature(snapshot);
        if (snapshot == null || snapshot.length != SNAPSHOT_LENGTH) {
            return invalid(
                    "length",
                    observedLength,
                    declaredLength,
                    headerSignature,
                    generationSignature);
        }
        if (declaredLength != DECLARED_PAYLOAD_LENGTH) {
            return invalid(
                    "declared_length",
                    observedLength,
                    declaredLength,
                    headerSignature,
                    generationSignature);
        }
        if ((snapshot[4] & 0xff) != 0x07
                || (snapshot[5] & 0xff) != 0x94
                || (snapshot[12] & 0xff) != 0x01
                || (snapshot[13] & 0xff) != 0x43
                || (snapshot[14] & 0xff) != 0x00
                || (snapshot[15] & 0xff) != 0x02) {
            return invalid(
                    "header",
                    observedLength,
                    declaredLength,
                    headerSignature,
                    generationSignature);
        }
        if (!validBcd(snapshot[6], 0, 99)
                || !validBcd(snapshot[7], 1, 12)
                || !validBcd(snapshot[8], 1, 31)
                || !validBcd(snapshot[9], 0, 23)
                || !validBcd(snapshot[10], 0, 59)
                || !validBcd(snapshot[11], 0, 59)) {
            return invalid(
                    "timestamp",
                    observedLength,
                    declaredLength,
                    headerSignature,
                    generationSignature);
        }
        if (RECORDS_OFFSET + RECORD_SIZE * RECORD_COUNT != snapshot.length) {
            return invalid(
                    "record_layout",
                    observedLength,
                    declaredLength,
                    headerSignature,
                    generationSignature);
        }

        int recordOffset = RECORDS_OFFSET + NEWEST_RECORD_INDEX * RECORD_SIZE;
        int rawEngineState = packedMsbBits(
                snapshot,
                recordOffset,
                ENGINE_STATE_BIT,
                2);
        int engineState = rawEngineState == ENGINE_STOPPED
                || rawEngineState == ENGINE_RUNNING
                ? rawEngineState
                : ENGINE_UNKNOWN;
        int rawParkState = packedMsbBits(
                snapshot,
                recordOffset,
                PARK_STATE_BIT,
                2);
        boolean parkValid = rawParkState == 0 || rawParkState == 2;
        boolean inPark = parkValid && rawParkState == 0;
        int rawEpbState = packedMsbBits(
                snapshot,
                recordOffset,
                EPB_STATE_BIT,
                2);
        boolean epbValid = rawEpbState == 0 || rawEpbState == 1;
        boolean epbEngaged = epbValid && rawEpbState == 1;

        int rawTemperature = rawTemperature(snapshot, recordOffset);
        float temperatureC = Float.NaN;
        String temperatureStatus;
        if (rawTemperature == INVALID_RAW_TEMPERATURE) {
            temperatureStatus = "source_unavailable";
        } else if (rawTemperature > MAX_RAW_TEMPERATURE) {
            temperatureStatus = "out_of_range";
        } else if (rawTemperature == 0 && engineState == ENGINE_STOPPED) {
            // Several cars publish raw zero after ignition-off. It is a source
            // sentinel there, not a measured -40 C transmission temperature.
            temperatureStatus = "engine_off_sentinel";
        } else {
            temperatureC = rawTemperature - TEMPERATURE_OFFSET_C;
            temperatureStatus = "available";
        }

        return new Result(
                true,
                "",
                temperatureStatus,
                observedLength,
                declaredLength,
                headerSignature,
                generationSignature,
                temperatureRecordsRaw(snapshot),
                generation(snapshot),
                temperatureC,
                rawTemperature,
                engineState,
                rawEngineState,
                parkValid,
                inPark,
                rawParkState,
                epbValid,
                epbEngaged,
                rawEpbState);
    }

    private static Result invalid(
            String reason,
            int observedLength,
            int declaredLength,
            String headerSignature,
            String generationSignature) {
        return new Result(
                false,
                reason,
                "snapshot_invalid",
                observedLength,
                declaredLength,
                headerSignature,
                generationSignature,
                "unavailable",
                -1L,
                Float.NaN,
                -1,
                ENGINE_UNKNOWN,
                -1,
                false,
                false,
                -1,
                false,
                false,
                -1);
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static int packedMsbBits(
            byte[] snapshot,
            int recordOffset,
            int bitOffset,
            int width) {
        int result = 0;
        for (int index = 0; index < width; index++) {
            int current = bitOffset + index;
            result = (result << 1)
                    | ((snapshot[recordOffset + current / 8]
                            >>> (7 - current % 8)) & 1);
        }
        return result;
    }

    private static boolean validBcd(byte encoded, int minimum, int maximum) {
        int high = (encoded >>> 4) & 0x0f;
        int low = encoded & 0x0f;
        int value = high * 10 + low;
        return high <= 9 && low <= 9 && value >= minimum && value <= maximum;
    }

    private static long generation(byte[] snapshot) {
        long result = 0L;
        for (int index = 6; index <= 11; index++) {
            result = (result << 8) | (snapshot[index] & 0xffL);
        }
        return result;
    }

    private static int rawTemperature(byte[] snapshot, int recordOffset) {
        int first = snapshot[recordOffset + TEMPERATURE_BYTE_1] & 0xff;
        int second = snapshot[recordOffset + TEMPERATURE_BYTE_2] & 0xff;
        return ((first & 0x1f) << 3) | ((second >>> 5) & 0x07);
    }

    /** Derived temperature fields for records 0..9; no raw packet is retained. */
    private static String temperatureRecordsRaw(byte[] snapshot) {
        StringBuilder result = new StringBuilder(RECORD_COUNT * 4);
        for (int index = 0; index < RECORD_COUNT; index++) {
            if (index > 0) {
                result.append(',');
            }
            int recordOffset = RECORDS_OFFSET + index * RECORD_SIZE;
            result.append(rawTemperature(snapshot, recordOffset));
        }
        return result.toString();
    }

    private static String headerSignature(byte[] snapshot) {
        if (snapshot == null || snapshot.length < 16) {
            return "unavailable";
        }
        return hex(snapshot[4]) + hex(snapshot[5]) + "/"
                + hex(snapshot[12]) + hex(snapshot[13])
                + hex(snapshot[14]) + hex(snapshot[15]);
    }

    private static String generationSignature(byte[] snapshot) {
        if (snapshot == null || snapshot.length < 12) {
            return "unavailable";
        }
        StringBuilder result = new StringBuilder(12);
        for (int index = 6; index <= 11; index++) {
            result.append(hex(snapshot[index]));
        }
        return result.toString();
    }

    private static String hex(byte value) {
        final char[] digits = "0123456789abcdef".toCharArray();
        int unsigned = value & 0xff;
        return new String(new char[] {
                digits[(unsigned >>> 4) & 0x0f],
                digits[unsigned & 0x0f]
        });
    }
}
