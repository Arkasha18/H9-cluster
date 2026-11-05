package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MachineIdentityTest {
    private static final String VIN = "LGWFF6A58PH123456";

    @Test
    public void vinCodeIsStableAndDoesNotContainVin() {
        String first = MachineIdentity.deriveVinCode(VIN);
        String second = MachineIdentity.deriveVinCode("vin=" + VIN.toLowerCase());

        assertEquals(first, second);
        assertTrue(MachineIdentity.isValidCode(first));
        assertFalse(first.contains(VIN));
    }

    @Test
    public void differentVinProducesDifferentCode() {
        assertNotEquals(
                MachineIdentity.deriveVinCode(VIN),
                MachineIdentity.deriveVinCode("LGWFF6A58PH123457"));
    }

    @Test
    public void invalidVinIsRejected() {
        assertNull(MachineIdentity.deriveVinCode("not-a-vin"));
        assertNull(MachineIdentity.deriveVinCode("LGWFF6A58PI123456"));
    }

    @Test
    public void readerFindsVinWithoutExposingOtherValues() {
        assertEquals(
                VIN,
                VehicleIdentityReader.findVin(new String[] {"waiting", "{\"value\":\"" + VIN + "\"}"}));
    }
}
