package net.adminrunet.h9cluster.skins.simplered;

import net.adminrunet.h9cluster.skins.SkinSettings;
import net.adminrunet.h9cluster.skins.SkinSettingsProvider;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;

/** Settings owned by the Simple Red skin: the colour its scale is drawn in. */
public final class SimpleRedSettingsProvider implements SkinSettingsProvider {
    public static final String KEY_SCALE_COLOR = "scale_color";

    private static final int SWATCH_DP = 20;
    private static final int PADDING_DP = 16;
    private static final int ROW_SPACING_DP = 6;

    /**
     * Reads the choice out of settings that may be absent, stale or hand
     * edited, so it always answers with a colour the skin can draw.
     */
    public static SimpleRedScaleColor scaleColor(SkinSettings settings) {
        if (settings == null) {
            return SimpleRedScaleColor.defaultColor();
        }
        return SimpleRedScaleColor.byId(
                settings.getString(KEY_SCALE_COLOR, null));
    }

    private static SkinSettings settingsFor(SimpleRedScaleColor color) {
        return SkinSettings.builder()
                .putString(KEY_SCALE_COLOR, color.id)
                .build();
    }

    @Override
    public SkinSettings getDefaultSettings() {
        return settingsFor(SimpleRedScaleColor.defaultColor());
    }

    @Override
    public SkinSettings normalize(SkinSettings settings) {
        return settingsFor(scaleColor(settings));
    }

    @Override
    public View createEditor(
            Context context,
            SkinSettings initialSettings,
            final Listener listener) {
        SimpleRedScaleColor selected = scaleColor(initialSettings);
        final RadioGroup group = new RadioGroup(context);
        group.setOrientation(RadioGroup.VERTICAL);
        int padding = dip(context, PADDING_DP);
        group.setPadding(padding, padding, padding, padding);

        for (SimpleRedScaleColor color : SimpleRedScaleColor.values()) {
            group.addView(createRow(context, color, color == selected));
        }
        group.setOnCheckedChangeListener(
                new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup parent, int id) {
                        View checked = parent.findViewById(id);
                        if (checked == null || listener == null) {
                            return;
                        }
                        listener.onSettingsChanged(settingsFor(
                                (SimpleRedScaleColor) checked.getTag()));
                    }
                });

        ScrollView scroll = new ScrollView(context);
        scroll.addView(group);
        return scroll;
    }

    /**
     * A named row with a swatch of the colour itself, because eight colour
     * names are far harder to choose between than eight colours.
     */
    private static RadioButton createRow(
            Context context,
            SimpleRedScaleColor color,
            boolean checked) {
        RadioButton button = new RadioButton(context);
        button.setId(View.generateViewId());
        button.setText(color.title);
        button.setTag(color);
        button.setChecked(checked);

        int size = dip(context, SWATCH_DP);
        GradientDrawable swatch = new GradientDrawable();
        swatch.setShape(GradientDrawable.OVAL);
        swatch.setColor(color.accent);
        swatch.setBounds(0, 0, size, size);
        button.setCompoundDrawablePadding(dip(context, PADDING_DP));
        button.setCompoundDrawables(null, null, swatch, null);

        int spacing = dip(context, ROW_SPACING_DP);
        button.setPadding(0, spacing, 0, spacing);
        return button;
    }

    private static int dip(Context context, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()));
    }
}
