package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public final class ClusterPreferencesContractTest {
    @Test
    public void storedSkinAndVisibilityAreLoadedTogether() {
        Map<String, Object> values = new HashMap<>();
        values.put("selected_skin", SkinPreferences.SKIN_CLASSIC_CUSTOM);
        values.put(
                "classic_custom_visibility_mask",
                BlockVisibility.allVisible()
                        .with(BlockVisibility.Block.CLOCK, false)
                        .toMask());

        ClusterPreferences.Snapshot snapshot = ClusterPreferences.fromValues(values);

        assertEquals(SkinPreferences.SKIN_CLASSIC_CUSTOM, snapshot.skin);
        assertFalse(snapshot.visibility.isVisible(BlockVisibility.Block.CLOCK));
        assertTrue(snapshot.visibility.isVisible(BlockVisibility.Block.SPEEDOMETER));
    }

    @Test
    public void missingValuesPreserveCurrentAllVisibleClassicBehavior() {
        ClusterPreferences.Snapshot snapshot =
                ClusterPreferences.fromValues(Collections.<String, Object>emptyMap());

        assertEquals(SkinPreferences.SKIN_CLASSIC, snapshot.skin);
        for (BlockVisibility.Block block : BlockVisibility.Block.values()) {
            assertTrue(block.name(), snapshot.visibility.isVisible(block));
        }
    }

    @Test
    public void invalidSkinAndMalformedFlagUseSafeDefaults() {
        Map<String, Object> values = new HashMap<>();
        values.put("selected_skin", "unknown");
        values.put("classic_custom_visibility_mask", "0");

        ClusterPreferences.Snapshot snapshot = ClusterPreferences.fromValues(values);

        assertEquals(SkinPreferences.SKIN_CLASSIC, snapshot.skin);
        assertTrue(snapshot.visibility.isVisible(BlockVisibility.Block.TYRE_PRESSURE));
    }

    @Test
    public void valuesForAtomicSaveContainOneSkinAndOneCompleteMask() {
        BlockVisibility visibility = BlockVisibility.allVisible()
                .with(BlockVisibility.Block.CLOCK, false)
                .with(BlockVisibility.Block.ATF_TEMPERATURE, false);

        Map<String, Object> values =
                ClusterPreferences.toValues(
                        SkinPreferences.SKIN_CLASSIC_CUSTOM,
                        visibility);

        assertEquals(2, values.size());
        assertEquals(
                SkinPreferences.SKIN_CLASSIC_CUSTOM,
                values.get("selected_skin"));
        assertEquals(
                Long.valueOf(visibility.toMask()),
                values.get("classic_custom_visibility_mask"));
    }

    @Test
    public void classicCustomIsValidButUnknownSkinStillFallsBackToClassic() {
        Map<String, Object> values = new HashMap<>();
        values.put("selected_skin", SkinPreferences.SKIN_CLASSIC_CUSTOM);
        assertEquals(
                SkinPreferences.SKIN_CLASSIC_CUSTOM,
                ClusterPreferences.fromValues(values).skin);

        values.put("selected_skin", "classic_custom_typo");
        assertEquals(
                SkinPreferences.SKIN_CLASSIC,
                ClusterPreferences.fromValues(values).skin);
    }
}
