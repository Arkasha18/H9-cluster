package net.adminrunet.h9cluster;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;

import org.junit.Test;

public final class LogUploadManagerTest {
    @Test
    public void compressesJsonLinesForBoundedTransfer() throws Exception {
        byte[] source = "{\"event\":\"one\"}\n{\"event\":\"two\"}\n".getBytes("UTF-8");
        byte[] compressed = LogUploadManager.gzip(source);
        ByteArrayOutputStream restored = new ByteArrayOutputStream();
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[64];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                restored.write(buffer, 0, read);
            }
        }

        assertArrayEquals(source, restored.toByteArray());
        assertEquals(
                "https://logs.admin.ru.net/api/v1/uploads",
                LogUploadManager.ENDPOINT);
    }
}
