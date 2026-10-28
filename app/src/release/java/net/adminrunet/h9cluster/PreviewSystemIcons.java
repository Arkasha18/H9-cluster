package net.adminrunet.h9cluster;

import android.content.Context;
import android.view.View;

/** The vehicle draws its own system icons; production must never imitate them. */
final class PreviewSystemIcons {
    private PreviewSystemIcons() { }
    static View create(Context context, String skinId) { return null; }
    static void update(View view, ClusterState state) { }
}
