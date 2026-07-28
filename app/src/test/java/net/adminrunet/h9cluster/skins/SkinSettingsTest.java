package net.adminrunet.h9cluster.skins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SkinSettingsTest {
    @Test
    public void builderKeepsSupportedPrimitiveTypes() {
        SkinSettings settings = SkinSettings.builder()
                .putBoolean("speedometer.visible", true)
                .putInt("layout.variant", 2)
                .putLong("mask", 15L)
                .putFloat("scale", 1.25f)
                .putString("accent", "red")
                .build();

        assertTrue(settings.getBoolean("speedometer.visible", false));
        assertEquals(2, settings.getInt("layout.variant", -1));
        assertEquals(15L, settings.getLong("mask", -1L));
        assertEquals(1.25f, settings.getFloat("scale", 0.0f), 0.0f);
        assertEquals("red", settings.getString("accent", ""));
    }

    @Test
    public void fromValuesDropsUnknownTypesAndInvalidKeys() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("valid", Boolean.TRUE);
        source.put("also.valid", "value");
        source.put("Invalid key", Integer.valueOf(4));
        source.put("unsupported", Double.valueOf(1.0d));

        SkinSettings settings = SkinSettings.fromValues(source);

        assertTrue(settings.contains("valid"));
        assertTrue(settings.contains("also.valid"));
        assertFalse(settings.contains("Invalid key"));
        assertFalse(settings.contains("unsupported"));
    }

    @Test
    public void buildUponDoesNotMutateOriginal() {
        SkinSettings original = SkinSettings.builder()
                .putBoolean("block.visible", true)
                .build();

        SkinSettings changed = original.buildUpon()
                .putBoolean("block.visible", false)
                .build();

        assertTrue(original.getBoolean("block.visible", false));
        assertFalse(changed.getBoolean("block.visible", true));
    }

    @Test
    public void emptyInstanceIsShared() {
        assertSame(SkinSettings.empty(), SkinSettings.builder().build());
        assertSame(SkinSettings.empty(), SkinSettings.fromValues(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void builderRejectsKeysOutsideStableFormat() {
        SkinSettings.builder().putBoolean("Current Gear", true);
    }
}
