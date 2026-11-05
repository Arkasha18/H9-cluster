package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class DiagnosticLogRingTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void repeatedWritesRemainInsideTwoOneMegabyteFiles() throws Exception {
        File directory = temporaryFolder.newFolder("diagnostics");
        File active = new File(directory, "events.jsonl");
        File previous = new File(directory, "events.previous.jsonl");
        byte[] record = new byte[16 * 1024];
        Arrays.fill(record, (byte) 'x');
        record[record.length - 1] = '\n';

        for (int index = 0; index < 256; index++) {
            DiagnosticLog.appendBounded(active, previous, record);
        }

        assertEquals(2, directory.listFiles().length);
        assertTrue(active.length() <= DiagnosticLog.FILE_LIMIT_BYTES);
        assertTrue(previous.length() <= DiagnosticLog.FILE_LIMIT_BYTES);
        assertTrue(
                active.length() + previous.length()
                        <= 2L * DiagnosticLog.FILE_LIMIT_BYTES);
    }
}
