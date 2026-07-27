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
        values.put("selected_skin", SkinPreferences.SKIN_HORIZON);
        values.put(BlockVisibility.Block.CLOCK.preferenceKey(), false);

        ClusterPreferences.Snapshot snapshot = ClusterPreferences.fromValues(values);

        assertEquals(SkinPreferences.SKIN_HORIZON, snapshot.skin);
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
        values.put(BlockVisibility.Block.TYRE_PRESSURE.preferenceKey(), "false");

        ClusterPreferences.Snapshot snapshot = ClusterPreferences.fromValues(values);

        assertEquals(SkinPreferences.SKIN_CLASSIC, snapshot.skin);
        assertTrue(snapshot.visibility.isVisible(BlockVisibility.Block.TYRE_PRESSURE));
    }

    @Test
    public void valuesForAtomicSaveContainSkinAndEveryBlock() {
        BlockVisibility visibility = BlockVisibility.allVisible()
                .with(BlockVisibility.Block.CLOCK, false)
                .with(BlockVisibility.Block.ATF_TEMPERATURE, false);

        Map<String, Object> values =
                ClusterPreferences.toValues(SkinPreferences.SKIN_HORIZON, visibility);

        assertEquals(16, values.size());
        assertEquals(SkinPreferences.SKIN_HORIZON, values.get("selected_skin"));
        assertEquals(
                Boolean.FALSE,
                values.get(BlockVisibility.Block.CLOCK.preferenceKey()));
        assertEquals(
                Boolean.FALSE,
                values.get(BlockVisibility.Block.ATF_TEMPERATURE.preferenceKey()));
        assertEquals(
                Boolean.TRUE,
                values.get(BlockVisibility.Block.SPEEDOMETER.preferenceKey()));
    }
}
