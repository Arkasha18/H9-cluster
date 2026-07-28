package net.adminrunet.h9cluster.skins;

import android.content.Context;
import android.view.View;

/**
 * Optional settings extension implemented inside a configurable skin package.
 */
public interface SkinSettingsProvider {
    interface Listener {
        void onSettingsChanged(SkinSettings settings);
    }

    /** Returns a complete initial configuration for this skin. */
    SkinSettings getDefaultSettings();

    /**
     * Validates persisted or preview values and fills any missing defaults.
     */
    SkinSettings normalize(SkinSettings settings);

    /**
     * Creates the skin-owned editor displayed by the shared settings screen.
     */
    View createEditor(
            Context context,
            SkinSettings initialSettings,
            Listener listener);
}
