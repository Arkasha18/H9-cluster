package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SettingsSessionTest {
    private static ClusterPreferences.Snapshot snapshot(
            String skin,
            BlockVisibility visibility) {
        return new ClusterPreferences.Snapshot(skin, visibility);
    }

    @Test
    public void unsavedDraftChangesDoNotReplaceOriginalRestoreSnapshot() {
        ClusterPreferences.Snapshot original = snapshot(
                SkinPreferences.SKIN_CLASSIC_CUSTOM,
                BlockVisibility.allVisible());
        SettingsSession session = new SettingsSession(original);

        session.setVisible(BlockVisibility.Block.CLOCK, false);

        assertFalse(session.draft().visibility.isVisible(
                BlockVisibility.Block.CLOCK));
        assertTrue(session.snapshotToRestoreOnClose().visibility.isVisible(
                BlockVisibility.Block.CLOCK));
    }

    @Test
    public void savedDraftBecomesTheNewRestoreSnapshot() {
        SettingsSession session = new SettingsSession(snapshot(
                SkinPreferences.SKIN_CLASSIC_CUSTOM,
                BlockVisibility.allVisible()));
        session.setVisible(BlockVisibility.Block.CLOCK, false);

        ClusterPreferences.Snapshot saved = session.markSaved();

        assertFalse(saved.visibility.isVisible(BlockVisibility.Block.CLOCK));
        assertFalse(session.snapshotToRestoreOnClose().visibility.isVisible(
                BlockVisibility.Block.CLOCK));
    }

    @Test
    public void selectingApprovedSkinsPreservesClassicCustomVisibility() {
        SettingsSession session = new SettingsSession(snapshot(
                SkinPreferences.SKIN_CLASSIC_CUSTOM,
                BlockVisibility.allVisible()));
        session.setVisible(BlockVisibility.Block.TYRE_PRESSURE, false);

        session.selectSkin(SkinPreferences.SKIN_CLASSIC);
        session.selectSkin(SkinPreferences.SKIN_HORIZON);
        session.selectSkin(SkinPreferences.SKIN_CLASSIC_CUSTOM);

        assertFalse(session.draft().visibility.isVisible(
                BlockVisibility.Block.TYRE_PRESSURE));
    }

    @Test
    public void enableAllRestoresEveryBlockWithoutChangingSelectedSkin() {
        SettingsSession session = new SettingsSession(snapshot(
                SkinPreferences.SKIN_CLASSIC_CUSTOM,
                BlockVisibility.noneVisible()));

        session.enableAll();

        assertEquals(
                SkinPreferences.SKIN_CLASSIC_CUSTOM,
                session.draft().skin);
        for (BlockVisibility.Block block : BlockVisibility.Block.values()) {
            assertTrue(block.name(), session.draft().visibility.isVisible(block));
        }
    }

    @Test
    public void unknownSelectedSkinNormalizesToClassic() {
        SettingsSession session = new SettingsSession(snapshot(
                SkinPreferences.SKIN_CLASSIC,
                BlockVisibility.allVisible()));

        session.selectSkin("unknown");

        assertEquals(SkinPreferences.SKIN_CLASSIC, session.draft().skin);
    }
}
