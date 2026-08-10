package net.adminrunet.h9cluster.navigation;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

/** Reusable editor embedded into the independent settings dialog of a skin. */
public final class NavigationAppPickerView extends LinearLayout {
    public interface Listener {
        void onNavigationAppChanged(String mode);
    }

    private static final String[] MODES = {
        NavigationSettings.MODE_NONE,
        NavigationSettings.MODE_FACTORY_YANDEX
    };
    private static final String[] LABELS = {
        "Не показывать карту",
        "Штатный Яндекс Навигатор"
    };

    private String selectedMode;

    public NavigationAppPickerView(
            Context context,
            String initialMode,
            final Listener listener) {
        super(context);
        setOrientation(VERTICAL);
        int padding = dp(context, 20);
        setPadding(padding, dp(context, 14), padding, dp(context, 18));

        TextView title = new TextView(context);
        title.setText("Карта под скином");
        title.setTextColor(Color.BLACK);
        title.setTextSize(16.0f);
        title.setPadding(0, 0, 0, dp(context, 8));
        addView(title, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        TextView description = new TextView(context);
        description.setText(
                "Штатный Яндекс Навигатор показывает заводской слой карты под скином. "
                        + "Отдельное окно приложения не запускается, поэтому скин и "
                        + "системные индикаторы остаются видимыми.");
        description.setTextColor(0xFF526169);
        description.setTextSize(13.0f);
        description.setPadding(0, 0, 0, dp(context, 10));
        addView(description, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        selectedMode = NavigationSettings.normalizeMode(initialMode);
        int selectedIndex = NavigationSettings.MODE_FACTORY_YANDEX.equals(
                selectedMode) ? 1 : 0;

        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                LABELS);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selectedIndex, false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id) {
                String mode = MODES[position];
                if (mode.equals(selectedMode)) {
                    return;
                }
                selectedMode = mode;
                if (listener != null) {
                    listener.onNavigationAppChanged(mode);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        addView(spinner, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources()
                .getDisplayMetrics().density);
    }
}
