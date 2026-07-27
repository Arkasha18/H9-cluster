package net.adminrunet.h9cluster;

/**
 * Decodes transmission-oil temperature from the TBOX can_data_collect
 * shared-memory snapshot.
 *
 * The first four bytes contain the little-endian payload length. A 12-byte
 * header follows, then ten 214-byte records. The tenth record is the newest.
 */
final class TboxTransmissionTemperatureDecoder {
    static final int SNAPSHOT_LENGTH = 2156;

    private static final int DECLARED_PAYLOAD_LENGTH = 2152;
    private static final int RECORDS_OFFSET = 16;
    private static final int RECORD_SIZE = 214;
    private static final int NEWEST_RECORD_INDEX = 9;
    private static final int TEMPERATURE_BYTE_1 = 0x45;
    private static final int TEMPERATURE_BYTE_2 = 0x46;
    private static final int INVALID_RAW_VALUE = 0xff;
    private static final int TEMPERATURE_OFFSET_C = 40;
    private static final int MIN_TEMPERATURE_C = -40;
    private static final int MAX_TEMPERATURE_C = 200;

    private TboxTransmissionTemperatureDecoder() {
    }

    static float decodeCelsius(byte[] snapshot) {
        if (snapshot == null || snapshot.length < SNAPSHOT_LENGTH) {
            return Float.NaN;
        }

        int declaredLength = (snapshot[0] & 0xff)
                | ((snapshot[1] & 0xff) << 8)
                | ((snapshot[2] & 0xff) << 16)
                | ((snapshot[3] & 0xff) << 24);
        if (declaredLength < DECLARED_PAYLOAD_LENGTH
                || declaredLength + 4 > snapshot.length) {
            return Float.NaN;
        }

        int recordOffset = RECORDS_OFFSET + NEWEST_RECORD_INDEX * RECORD_SIZE;
        int first = snapshot[recordOffset + TEMPERATURE_BYTE_1] & 0xff;
        int second = snapshot[recordOffset + TEMPERATURE_BYTE_2] & 0xff;
        int raw = ((first & 0x1f) << 3) | ((second >>> 5) & 0x07);
        if (raw == INVALID_RAW_VALUE) {
            return Float.NaN;
        }

        int temperatureC = raw - TEMPERATURE_OFFSET_C;
        if (temperatureC < MIN_TEMPERATURE_C
                || temperatureC > MAX_TEMPERATURE_C) {
            return Float.NaN;
        }
        return temperatureC;
    }
}
