package net.adminrunet.h9cluster.skins.horizon;

import net.adminrunet.h9cluster.navigation.NavigationAppPickerView;
import net.adminrunet.h9cluster.navigation.NavigationSettings;
import net.adminrunet.h9cluster.skins.SkinSettings;
import net.adminrunet.h9cluster.skins.SkinSettingsProvider;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;

/** Settings owned exclusively by the Horizon skin. */
public final class HorizonSettingsProvider implements SkinSettingsProvider {
    public static final String SWAP_PRIMARY_GAUGES = "swap_primary_gauges";

    private static final boolean DEFAULT_SWAP_PRIMARY_GAUGES = false;

    @Override
    public SkinSettings getDefaultSettings() {
        return createSettings(DEFAULT_SWAP_PRIMARY_GAUGES, "");
    }

    @Override
    public SkinSettings normalize(SkinSettings settings) {
        SkinSettings source = settings == null
                ? SkinSettings.empty()
                : settings;
        return createSettings(
                source.getBoolean(
                        SWAP_PRIMARY_GAUGES,
                        DEFAULT_SWAP_PRIMARY_GAUGES),
                NavigationSettings.selectedMode(source));
    }

    @Override
    public View createEditor(
            Context context,
            SkinSettings initialSettings,
            final Listener listener) {
        SkinSettings normalized = normalize(initialSettings);
        final boolean[] swapEnabled = {
            shouldSwapPrimaryGauges(normalized)
        };
        final String[] navigationMode = {
            NavigationSettings.selectedMode(normalized)
        };

        LinearLayout editor = new LinearLayout(context);
        editor.setOrientation(LinearLayout.VERTICAL);

        Switch swapGauges = createSwitch(
                context,
                "Поменять местами спидометр и тахометр",
                swapEnabled[0]);
        int padding = dp(context, 20);
        swapGauges.setPadding(
                padding,
                dp(context, 18),
                padding,
                dp(context, 18));
        swapGauges.setOnCheckedChangeListener((buttonView, isChecked) -> {
            swapEnabled[0] = isChecked;
            listener.onSettingsChanged(createSettings(
                    swapEnabled[0],
                    navigationMode[0]));
        });
        editor.addView(swapGauges, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        NavigationAppPickerView navigationPicker =
                new NavigationAppPickerView(
                        context,
                        navigationMode[0],
                        new NavigationAppPickerView.Listener() {
                            @Override
                            public void onNavigationAppChanged(String mode) {
                                navigationMode[0] = mode;
                                listener.onSettingsChanged(createSettings(
                                        swapEnabled[0],
                                        navigationMode[0]));
                            }
                        });
        editor.addView(navigationPicker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return editor;
    }

    public static boolean shouldSwapPrimaryGauges(SkinSettings settings) {
        return settings != null && settings.getBoolean(
                SWAP_PRIMARY_GAUGES,
                DEFAULT_SWAP_PRIMARY_GAUGES);
    }

    private static SkinSettings createSettings(
            boolean swapPrimaryGauges,
            String navigationMode) {
        return SkinSettings.builder()
                .putBoolean(SWAP_PRIMARY_GAUGES, swapPrimaryGauges)
                .putString(
                        NavigationSettings.KEY_MODE,
                        NavigationSettings.normalizeMode(navigationMode))
                .build();
    }

    private static Switch createSwitch(
            Context context,
            String text,
            boolean checked) {
        Switch control = new Switch(context);
        control.setText(text);
        control.setTextColor(Color.BLACK);
        control.setTextSize(16.0f);
        control.setChecked(checked);
        control.setPadding(0, dp(context, 10), 0, dp(context, 10));
        return control;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources()
                .getDisplayMetrics().density);
    }
}
