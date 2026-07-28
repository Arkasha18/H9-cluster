package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PreviewRequestTest {
    private final ClusterPreferences.Snapshot persisted =
            new ClusterPreferences.Snapshot(
                    SkinPreferences.SKIN_CLASSIC,
                    BlockVisibility.allVisible());

    @Test
    public void absentDraftUsesPersistedSnapshot() {
        PreviewRequest request = PreviewRequest.resolve(
                persisted,
                false,
                null,
                null);

        assertEquals(SkinPreferences.SKIN_CLASSIC, request.skin);
        assertEquals(BlockVisibility.allVisible(), request.visibility);
    }

    @Test
    public void completeClassicCustomDraftOverridesPersistedSnapshot() {
        BlockVisibility draftVisibility = BlockVisibility.allVisible()
                .with(BlockVisibility.Block.CLOCK, false);

        PreviewRequest request = PreviewRequest.resolve(
                persisted,
                true,
                SkinPreferences.SKIN_CLASSIC_CUSTOM,
                Long.valueOf(draftVisibility.toMask()));

        assertEquals(SkinPreferences.SKIN_CLASSIC_CUSTOM, request.skin);
        assertEquals(draftVisibility, request.visibility);
    }

    @Test
    public void incompleteOrMalformedDraftFallsBackToPersistedSnapshot() {
        PreviewRequest missingSkin = PreviewRequest.resolve(
                persisted,
                true,
                null,
                Long.valueOf(0L));
        PreviewRequest wrongMaskType = PreviewRequest.resolve(
                persisted,
                true,
                SkinPreferences.SKIN_CLASSIC_CUSTOM,
                "0");
        PreviewRequest invalidMask = PreviewRequest.resolve(
                persisted,
                true,
                SkinPreferences.SKIN_CLASSIC_CUSTOM,
                Long.valueOf(-1L));

        assertEquals(SkinPreferences.SKIN_CLASSIC, missingSkin.skin);
        assertEquals(SkinPreferences.SKIN_CLASSIC, wrongMaskType.skin);
        assertEquals(SkinPreferences.SKIN_CLASSIC, invalidMask.skin);
        assertEquals(BlockVisibility.allVisible(), missingSkin.visibility);
        assertEquals(BlockVisibility.allVisible(), wrongMaskType.visibility);
        assertEquals(BlockVisibility.allVisible(), invalidMask.visibility);
    }

    @Test
    public void classicAndHorizonDraftsIgnoreCustomVisibilityMask() {
        PreviewRequest classic = PreviewRequest.resolve(
                persisted,
                true,
                SkinPreferences.SKIN_CLASSIC,
                Long.valueOf(0L));
        PreviewRequest horizon = PreviewRequest.resolve(
                persisted,
                true,
                SkinPreferences.SKIN_HORIZON,
                Long.valueOf(0L));

        assertEquals(BlockVisibility.allVisible(), classic.visibility);
        assertEquals(BlockVisibility.allVisible(), horizon.visibility);
    }
}
