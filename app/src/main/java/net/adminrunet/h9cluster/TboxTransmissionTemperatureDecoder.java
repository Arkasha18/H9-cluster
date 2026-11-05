package net.adminrunet.h9cluster;

/**
 * Decodes transmission-oil temperature from the TBOX can_data_collect
 * shared-memory snapshot.
 *
 * The first four bytes contain the little-endian payload length. A 12-byte
 * header follows, then ten 214-byte records. The tenth record is the newest.
 */
final class TboxTransmissionTemperatureDecoder {
    static final int SNAPSHOT_LENGTH = TboxCanSnapshotDecoder.SNAPSHOT_LENGTH;

    private TboxTransmissionTemperatureDecoder() {
    }

    static float decodeCelsius(byte[] snapshot) {
        TboxCanSnapshotDecoder.Result result =
                TboxCanSnapshotDecoder.decode(snapshot);
        return result.validSnapshot ? result.temperatureC : Float.NaN;
    }
}
