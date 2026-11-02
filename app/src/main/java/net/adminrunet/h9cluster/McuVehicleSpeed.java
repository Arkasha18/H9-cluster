package net.adminrunet.h9cluster;

/** ABS3 VehSpd, independently received through MCU/FDBus. See docs/SPEED_SOURCE_RU.md. */
final class McuVehicleSpeed {
    static final int CAN_ID = 0x265;
    static final int CHANNEL = 0;

    private McuVehicleSpeed() {}

    static float decode(byte[] data) {
        // H9's MCU forwards a 16-byte frame; the classic ABS3 layout is 8 bytes.
        if (data == null || (data.length != 8 && data.length != 16)
                || (data[1] & 0x20) == 0) return Float.NaN;
        int raw = ((data[1] & 0x1f) << 8) | (data[2] & 0xff);
        // DBC physical range ends at 5333 counts (299.98125 km/h).
        if (raw > 5333) return Float.NaN;
        return raw * 0.05625f;
    }
}
