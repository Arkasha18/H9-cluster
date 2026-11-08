package net.adminrunet.h9cluster;

/** Static SBP QR payload used on the settings screen. */
final class SupportProjectQr {
    static final String PAYMENT_URL =
            "https://qr.nspk.ru/BS2A000TQ6AEVP538UPOR733TS5CFV8D";

    private static final int EXTRA_QUIET_ZONE_MODULES = 2;
    private static final String[] ENCODED_MODULES = {
            "111111111111111111111111111111111",
            "111111111111111111111111111111111",
            "110000000100111100010101000000011",
            "110111110111001111101101011111011",
            "110100010101011001010111010001011",
            "110100010100101101000101010001011",
            "110100010100111110100001010001011",
            "110111110110111011111001011111011",
            "110000000101010101010101000000011",
            "111111111110110100101001111111111",
            "110000110101110001101000110001011",
            "111101001010111011111101000111011",
            "110001010101101011001101100100111",
            "110110001011100001111110100111011",
            "111100100110110100000111111001111",
            "110010001100000110100110011100011",
            "110000010101110111011001010100011",
            "110010101001000110110000101110111",
            "111100110000101001000000100010111",
            "111010001101010001110111111000111",
            "110111100100011000111100011101111",
            "111100111100100110001110001101111",
            "110010000000110111110000000001111",
            "111111111101000100011001110000011",
            "110000000110100001001001010010111",
            "110111110110101001110101110010111",
            "110100010110111010100100000100111",
            "110100010100100111000111110010011",
            "110100010101011111100011101101011",
            "110111110101111100110001000010111",
            "110000000101000111100001001010111",
            "111111111111111111111111111111111",
            "111111111111111111111111111111111",
    };

    static final int MODULE_COUNT =
            ENCODED_MODULES.length + EXTRA_QUIET_ZONE_MODULES * 2;

    static {
        int width = ENCODED_MODULES.length;
        for (String row : ENCODED_MODULES) {
            if (row.length() != width) {
                throw new IllegalStateException("The embedded QR matrix must be square");
            }
        }
    }

    private SupportProjectQr() {
    }

    static boolean isDarkModule(int row, int column) {
        if (row < 0 || row >= MODULE_COUNT || column < 0 || column >= MODULE_COUNT) {
            throw new IndexOutOfBoundsException("QR module is outside the matrix");
        }
        int encodedRow = row - EXTRA_QUIET_ZONE_MODULES;
        int encodedColumn = column - EXTRA_QUIET_ZONE_MODULES;
        if (encodedRow < 0
                || encodedRow >= ENCODED_MODULES.length
                || encodedColumn < 0
                || encodedColumn >= ENCODED_MODULES.length) {
            return false;
        }
        return ENCODED_MODULES[encodedRow].charAt(encodedColumn) == '0';
    }
}
