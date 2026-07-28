package net.adminrunet.h9cluster;

/** Validated renderer request resolved from persisted settings or a UI draft. */
final class PreviewRequest {
    final String skin;
    final BlockVisibility visibility;

    private PreviewRequest(
            String skin,
            BlockVisibility visibility) {
        this.skin = skin;
        this.visibility = visibility;
    }

    static PreviewRequest resolve(
            ClusterPreferences.Snapshot persisted,
            boolean hasDraft,
            String draftSkin,
            Object draftVisibilityMask) {
        ClusterPreferences.Snapshot safePersisted = persisted == null
                ? new ClusterPreferences.Snapshot(
                        SkinPreferences.SKIN_CLASSIC,
                        BlockVisibility.allVisible())
                : persisted;
        if (!hasDraft
                || !isKnownSkin(draftSkin)
                || !(draftVisibilityMask instanceof Long)
                || !isValidMask(((Long) draftVisibilityMask).longValue())) {
            return fromSnapshot(safePersisted);
        }

        BlockVisibility visibility =
                SkinPreferences.SKIN_CLASSIC_CUSTOM.equals(draftSkin)
                        ? BlockVisibility.fromMask(
                                ((Long) draftVisibilityMask).longValue())
                        : BlockVisibility.allVisible();
        return new PreviewRequest(draftSkin, visibility);
    }

    private static PreviewRequest fromSnapshot(
            ClusterPreferences.Snapshot snapshot) {
        String skin = SkinPreferences.normalize(snapshot.skin);
        BlockVisibility visibility =
                SkinPreferences.SKIN_CLASSIC_CUSTOM.equals(skin)
                        ? snapshot.visibility
                        : BlockVisibility.allVisible();
        return new PreviewRequest(skin, visibility);
    }

    private static boolean isKnownSkin(String skin) {
        return SkinPreferences.SKIN_CLASSIC.equals(skin)
                || SkinPreferences.SKIN_HORIZON.equals(skin)
                || SkinPreferences.SKIN_CLASSIC_CUSTOM.equals(skin);
    }

    private static boolean isValidMask(long mask) {
        return mask >= 0L
                && (mask & ~BlockVisibility.validMask()) == 0L;
    }
}
