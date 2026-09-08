package net.adminrunet.h9cluster;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import org.junit.Test;

public final class ReleaseBuildContractTest {
    @Test
    public void releaseVariantHasProductionIdentity() {
        assertFalse(BuildConfig.DEMO_MODE);
        assertFalse(BuildConfig.DEBUG);
        assertTrue(BuildConfig.APPLICATION_ID.equals("net.adminrunet.h9cluster"));
    }

    @Test
    public void tboxSecretContractMatchesProtectedBuildInput() {
        String expectedValue = System.getenv("H9_RELEASE_EXPECTED_TBOX_PASSWORD");
        if (expectedValue == null || expectedValue.isEmpty()) {
            assertTrue(
                    "Release TBOX mask and data must be configured together",
                    BuildConfig.TBOX_SECRET_MASK.isEmpty()
                            == BuildConfig.TBOX_SECRET_DATA.isEmpty());
            return;
        }

        assertFalse("Release TBOX secret mask must be present", BuildConfig.TBOX_SECRET_MASK.isEmpty());
        assertFalse("Release TBOX secret data must be present", BuildConfig.TBOX_SECRET_DATA.isEmpty());

        byte[] expected = expectedValue.getBytes(StandardCharsets.UTF_8);
        byte[] mask = Base64.getDecoder().decode(BuildConfig.TBOX_SECRET_MASK);
        byte[] data = Base64.getDecoder().decode(BuildConfig.TBOX_SECRET_DATA);
        byte[] reconstructed = new byte[data.length];
        try {
            assertTrue(
                    "Release TBOX mask length differs from expected input",
                    mask.length == expected.length);
            assertTrue(
                    "Release TBOX data length differs from expected input",
                    data.length == expected.length);

            for (int index = 0; index < data.length; index++) {
                reconstructed[index] = (byte) (data[index] ^ mask[index]);
            }
            assertTrue(
                    "Release TBOX secret material does not match protected build input",
                    MessageDigest.isEqual(expected, reconstructed));
        } finally {
            Arrays.fill(expected, (byte) 0);
            Arrays.fill(mask, (byte) 0);
            Arrays.fill(data, (byte) 0);
            Arrays.fill(reconstructed, (byte) 0);
        }
    }
}
