package net.adminrunet.h9cluster.navigation;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Reusable editor embedded into the independent settings dialog of a skin. */
public final class NavigationAppPickerView extends LinearLayout {
    public interface Listener {
        void onNavigationAppChanged(String component);
    }

    private final List<NavigationAppOption> options;
    private String selectedComponent;

    public NavigationAppPickerView(
            Context context,
            String initialComponent,
            final Listener listener) {
        super(context);
        setOrientation(VERTICAL);
        int padding = dp(context, 20);
        setPadding(padding, dp(context, 14), padding, dp(context, 18));

        TextView title = new TextView(context);
        title.setText("Навигация или приложение на дисплее 2");
        title.setTextColor(Color.BLACK);
        title.setTextSize(16.0f);
        title.setPadding(0, 0, 0, dp(context, 8));
        addView(title, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        TextView description = new TextView(context);
        description.setText(
                "Приложение запускается под прозрачным скином. "
                        + "Если оно запрещает второй дисплей, H9 Cluster продолжит работу без него.");
        description.setTextColor(0xFF526169);
        description.setTextSize(13.0f);
        description.setPadding(0, 0, 0, dp(context, 10));
        addView(description, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        selectedComponent = NavigationSettings.normalizeComponent(initialComponent);
        options = NavigationAppCatalog.load(context, selectedComponent);
        List<String> labels = new ArrayList<>();
        int selectedIndex = 0;
        for (int index = 0; index < options.size(); index++) {
            NavigationAppOption option = options.get(index);
            labels.add(option.title);
            if (option.component.equals(selectedComponent)) {
                selectedIndex = index;
            }
        }

        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                labels);
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
                String component = options.get(position).component;
                if (component.equals(selectedComponent)) {
                    return;
                }
                selectedComponent = component;
                if (listener != null) {
                    listener.onNavigationAppChanged(component);
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
