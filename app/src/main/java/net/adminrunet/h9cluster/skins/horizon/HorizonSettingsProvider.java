package net.adminrunet.h9cluster.skins.horizon;

import net.adminrunet.h9cluster.skins.SkinSettings;
import net.adminrunet.h9cluster.skins.SkinSettingsProvider;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.Switch;

/** Settings owned exclusively by the Horizon skin. */
public final class HorizonSettingsProvider implements SkinSettingsProvider {
    public static final String SWAP_PRIMARY_GAUGES = "swap_primary_gauges";

    private static final boolean DEFAULT_SWAP_PRIMARY_GAUGES = false;

    @Override
    public SkinSettings getDefaultSettings() {
        return createSettings(DEFAULT_SWAP_PRIMARY_GAUGES);
    }

    @Override
    public SkinSettings normalize(SkinSettings settings) {
        SkinSettings source = settings == null
                ? SkinSettings.empty()
                : settings;
        return createSettings(source.getBoolean(
                SWAP_PRIMARY_GAUGES,
                DEFAULT_SWAP_PRIMARY_GAUGES));
    }

    @Override
    public View createEditor(
            Context context,
            SkinSettings initialSettings,
            final Listener listener) {
        SkinSettings normalized = normalize(initialSettings);
        Switch swapGauges = createSwitch(
                context,
                "Поменять местами спидометр и тахометр",
                shouldSwapPrimaryGauges(normalized));
        int padding = dp(context, 20);
        swapGauges.setPadding(
                padding,
                dp(context, 18),
                padding,
                dp(context, 18));
        swapGauges.setOnCheckedChangeListener((buttonView, isChecked) ->
                listener.onSettingsChanged(createSettings(isChecked)));
        return swapGauges;
    }

    public static boolean shouldSwapPrimaryGauges(SkinSettings settings) {
        return settings != null && settings.getBoolean(
                SWAP_PRIMARY_GAUGES,
                DEFAULT_SWAP_PRIMARY_GAUGES);
    }

    private static SkinSettings createSettings(boolean swapPrimaryGauges) {
        return SkinSettings.builder()
                .putBoolean(SWAP_PRIMARY_GAUGES, swapPrimaryGauges)
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
