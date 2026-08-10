package net.adminrunet.h9cluster.navigation;

import net.adminrunet.h9cluster.skins.SkinSettings;
import net.adminrunet.h9cluster.skins.SkinSettingsProvider;

import android.content.Context;
import android.view.View;

/** Settings provider used by skins whose first option is the background app. */
public final class NavigationOnlySettingsProvider
        implements SkinSettingsProvider {
    @Override
    public SkinSettings getDefaultSettings() {
        return NavigationSettings.navigationOnly("");
    }

    @Override
    public SkinSettings normalize(SkinSettings settings) {
        return NavigationSettings.navigationOnly(
                NavigationSettings.selectedComponent(settings));
    }

    @Override
    public View createEditor(
            Context context,
            SkinSettings initialSettings,
            final Listener listener) {
        return new NavigationAppPickerView(
                context,
                NavigationSettings.selectedComponent(initialSettings),
                new NavigationAppPickerView.Listener() {
                    @Override
                    public void onNavigationAppChanged(String component) {
                        listener.onSettingsChanged(
                                NavigationSettings.navigationOnly(component));
                    }
                });
    }
}
