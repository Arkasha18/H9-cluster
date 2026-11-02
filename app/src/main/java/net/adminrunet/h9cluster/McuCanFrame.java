package net.adminrunet.h9cluster;

/** The read-only 0x0100 MCU event: protobuf bytes containing a CAN frame. */
final class McuCanFrame {
    final int channel;
    final int id;
    final byte[] data;

    private McuCanFrame(int channel, int id, byte[] data) {
        this.channel = channel;
        this.id = id;
        this.data = data;
    }

    static McuCanFrame decode(byte[] payload) {
        if (payload == null || payload.length < 9 || payload[0] != 0x0a) return null;
        int cursor = 1;
        long length = 0;
        boolean complete = false;
        for (int shift = 0; shift <= 28 && cursor < payload.length; shift += 7) {
            int value = payload[cursor++] & 0xff;
            length |= (long) (value & 0x7f) << shift;
            if ((value & 0x80) == 0) {
                complete = true;
                break;
            }
        }
        // Seven header bytes followed by at most one CAN FD payload.
        if (!complete || length < 7 || length > 71 || length > payload.length - cursor) {
            return null;
        }
        int dlc = payload[cursor + 2] & 0xff;
        if (dlc != length - 7) return null;
        int id = ((payload[cursor + 4] & 0xff) << 16)
                | ((payload[cursor + 5] & 0xff) << 8)
                | (payload[cursor + 6] & 0xff);
        byte[] data = new byte[dlc];
        System.arraycopy(payload, cursor + 7, data, 0, dlc);
        return new McuCanFrame(payload[cursor] & 0xff, id, data);
    }
}
