package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DiagnosticLogTest {
    @Test
    public void formatsStructuredLineAndRedactsRawVin() {
        String line = DiagnosticLog.formatLine(
                1_800_000_000_000L,
                1234L,
                "WARN",
                "identity",
                "sample",
                "detail", "VIN LGWFF6A58PH123456",
                "machine_id", "H9-01ABC-23DEF");

        assertTrue(line.contains("\"ts_ms\":1800000000000"));
        assertTrue(line.contains("\"component\":\"identity\""));
        assertTrue(line.contains("[redacted-vin]"));
        assertTrue(line.contains("H9-01ABC-23DEF"));
        assertFalse(line.contains("LGWFF6A58PH123456"));
        assertEquals(1_800_000_000_000L, DiagnosticLog.timestampFromLine(line));
    }

    @Test
    public void missingTimestampIsRejectedByExporterParser() {
        assertEquals(-1L, DiagnosticLog.timestampFromLine("{\"event\":\"x\"}"));
    }
}
