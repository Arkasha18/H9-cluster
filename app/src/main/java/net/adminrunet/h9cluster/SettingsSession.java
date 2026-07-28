package net.adminrunet.h9cluster;

/** In-memory settings draft that persists only after an explicit Save. */
final class SettingsSession {
    private ClusterPreferences.Snapshot original;
    private ClusterPreferences.Snapshot draft;

    SettingsSession(ClusterPreferences.Snapshot original) {
        this.original = safeSnapshot(original);
        draft = this.original;
    }

    ClusterPreferences.Snapshot draft() {
        return draft;
    }

    void selectSkin(String skin) {
        draft = new ClusterPreferences.Snapshot(
                SkinPreferences.normalize(skin),
                draft.visibility);
    }

    void setVisible(
            BlockVisibility.Block block,
            boolean visible) {
        draft = new ClusterPreferences.Snapshot(
                draft.skin,
                draft.visibility.with(block, visible));
    }

    void enableAll() {
        draft = new ClusterPreferences.Snapshot(
                draft.skin,
                BlockVisibility.allVisible());
    }

    ClusterPreferences.Snapshot markSaved() {
        original = draft;
        return original;
    }

    ClusterPreferences.Snapshot snapshotToRestoreOnClose() {
        return original;
    }

    private static ClusterPreferences.Snapshot safeSnapshot(
            ClusterPreferences.Snapshot snapshot) {
        if (snapshot == null) {
            return new ClusterPreferences.Snapshot(
                    SkinPreferences.SKIN_CLASSIC,
                    BlockVisibility.allVisible());
        }
        return new ClusterPreferences.Snapshot(
                SkinPreferences.normalize(snapshot.skin),
                snapshot.visibility == null
                        ? BlockVisibility.allVisible()
                        : snapshot.visibility);
    }
}
