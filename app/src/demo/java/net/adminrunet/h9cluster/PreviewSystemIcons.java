package net.adminrunet.h9cluster;

import android.content.Context;
import android.view.View;
import net.adminrunet.h9cluster.skins.SkinRegistry;

/** Demo-only factory: the production source sets provide a no-op implementation. */
final class PreviewSystemIcons {
    private PreviewSystemIcons() { }

    static View create(Context context, String skinId) {
        return SkinRegistry.ION_AURORA.equals(skinId)
                ? new DemoSystemIconsView(context) : null;
    }

    static void update(View view, ClusterState state) {
        if (view instanceof DemoSystemIconsView) {
            ((DemoSystemIconsView) view).setClusterState(state);
        }
    }
}
