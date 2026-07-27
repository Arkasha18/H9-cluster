package net.adminrunet.h9cluster;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.EnumMap;

/** Touch settings surface for the Android 9 main head-unit display. */
public final class SettingsView extends LinearLayout {
    private static final int COLOR_BACKGROUND = 0xFF071014;
    private static final int COLOR_CARD = 0xFF102329;
    private static final int COLOR_CARD_SELECTED = 0xFF17343A;
    private static final int COLOR_TEXT = 0xFFF2F5F7;
    private static final int COLOR_MUTED = 0xFF98A7AE;
    private static final int COLOR_ACCENT = 0xFF31D7C5;

    private static final CharSequence[] SKIN_TITLES = {
            "Classic — утверждённый дизайн",
            "Horizon — базовый скин"
    };

    private final EnumMap<BlockVisibility.Block, Switch> switches =
            new EnumMap<>(BlockVisibility.Block.class);
    private final TextView skinValue;
    private final TextView statusView;
    private String selectedSkin;

    public SettingsView(Context context) {
        super(context);
        ClusterPreferences.Snapshot settings = ClusterPreferences.load(context);
        selectedSkin = settings.skin;

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setPadding(dp(24), dp(14), dp(24), dp(14));
        setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header, matchWrap());

        LinearLayout identity = new LinearLayout(context);
        identity.setOrientation(VERTICAL);
        header.addView(identity, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f));

        TextView title = text("H9 Cluster", 28.0f, COLOR_TEXT, true);
        identity.addView(title, wrapWrap());
        TextView developer = text(
                BuildConfig.DEMO_MODE
                        ? "admin.ru.net  ·  DEMO"
                        : "admin.ru.net  ·  DISPLAY 2",
                13.0f,
                COLOR_ACCENT,
                true);
        developer.setLetterSpacing(0.08f);
        identity.addView(developer, wrapWrap());

        Button skinButton = button("", COLOR_CARD_SELECTED, COLOR_TEXT);
        skinButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        skinButton.setPadding(dp(18), 0, dp(18), 0);
        skinButton.setMinHeight(dp(58));
        skinButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                showSkinPicker();
            }
        });
        skinValue = skinButton;
        updateSkinLabel();
        header.addView(skinButton, new LayoutParams(dp(390), dp(58)));

        TextView sectionTitle = text("ОТОБРАЖАЕМЫЕ БЛОКИ", 13.0f, COLOR_MUTED, true);
        sectionTitle.setLetterSpacing(0.12f);
        LayoutParams sectionTitleParams = matchWrap();
        sectionTitleParams.topMargin = dp(12);
        sectionTitleParams.bottomMargin = dp(5);
        addView(sectionTitle, sectionTitleParams);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        addView(scrollView, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1.0f));

        LinearLayout options = new LinearLayout(context);
        options.setOrientation(VERTICAL);
        scrollView.addView(options, matchWrap());
        populateOptions(options, settings.visibility);

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams footerParams = matchWrap();
        footerParams.topMargin = dp(10);
        addView(footer, footerParams);

        Button enableAll = button("Включить все", COLOR_CARD, COLOR_ACCENT);
        enableAll.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                setAllBlocksVisible();
            }
        });
        footer.addView(enableAll, new LayoutParams(dp(170), dp(52)));

        statusView = text("", 14.0f, COLOR_MUTED, false);
        statusView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        statusParams.leftMargin = dp(16);
        statusParams.rightMargin = dp(16);
        footer.addView(statusView, statusParams);

        Button save = button(
                BuildConfig.DEMO_MODE
                        ? "Сохранить и запустить"
                        : "Сохранить и запустить на дисплее 2",
                COLOR_ACCENT,
                Color.BLACK);
        save.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                saveAndLaunch();
            }
        });
        footer.addView(save, new LayoutParams(dp(390), dp(52)));
    }

    BlockVisibility getBlockVisibility() {
        BlockVisibility visibility = BlockVisibility.allVisible();
        for (SettingsCatalog.Option option : SettingsCatalog.options()) {
            Switch control = switches.get(option.block);
            visibility = visibility.with(option.block, control != null && control.isChecked());
        }
        return visibility;
    }

    private void populateOptions(LinearLayout parent, BlockVisibility visibility) {
        SettingsCatalog.Group currentGroup = null;
        for (SettingsCatalog.Option option : SettingsCatalog.options()) {
            if (option.group != currentGroup) {
                currentGroup = option.group;
                parent.addView(groupHeader(groupTitle(currentGroup)), matchWrap());
            }
            parent.addView(optionRow(option, visibility.isVisible(option.block)), matchWrap());
        }
    }

    private View optionRow(SettingsCatalog.Option option, boolean checked) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(12), 0);
        row.setMinimumHeight(dp(50));
        row.setBackground(roundedBackground(COLOR_CARD, 10));

        TextView label = text(option.label, 17.0f, COLOR_TEXT, false);
        row.addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f));

        Switch control = new Switch(getContext());
        control.setChecked(checked);
        control.setShowText(false);
        control.setContentDescription(option.label);
        int[][] states = {
                new int[] {android.R.attr.state_checked},
                new int[] {}
        };
        control.setThumbTintList(new ColorStateList(
                states,
                new int[] {COLOR_ACCENT, COLOR_MUTED}));
        control.setTrackTintList(new ColorStateList(
                states,
                new int[] {0x9931D7C5, 0x553D5158}));
        row.addView(control, new LayoutParams(dp(74), dp(48)));
        switches.put(option.block, control);

        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(VERTICAL);
        wrapper.addView(row, matchWrap());
        TextView divider = new TextView(getContext());
        wrapper.addView(divider, new LayoutParams(1, dp(5)));
        return wrapper;
    }

    private TextView groupHeader(String label) {
        TextView header = text(label, 13.0f, COLOR_ACCENT, true);
        header.setLetterSpacing(0.1f);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(9), 0, dp(7));
        return header;
    }

    private void showSkinPicker() {
        new AlertDialog.Builder(getContext())
                .setTitle("Выберите тему")
                .setSingleChoiceItems(
                        SKIN_TITLES,
                        SkinPreferences.SKIN_HORIZON.equals(selectedSkin) ? 1 : 0,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                selectedSkin = which == 1
                                        ? SkinPreferences.SKIN_HORIZON
                                        : SkinPreferences.SKIN_CLASSIC;
                                updateSkinLabel();
                                statusView.setText("");
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void setAllBlocksVisible() {
        for (Switch control : switches.values()) {
            control.setChecked(true);
        }
        statusView.setText("Все блоки включены. Нажмите «Сохранить»");
        statusView.setTextColor(COLOR_ACCENT);
    }

    private void saveAndLaunch() {
        ClusterPreferences.save(getContext(), selectedSkin, getBlockVisibility());
        boolean launched = ClusterLauncher.startOnClusterDisplay(getContext());
        statusView.setText(launched
                ? "Настройки сохранены и запущены"
                : BuildConfig.DEMO_MODE
                        ? "Настройки сохранены. Не удалось запустить панель"
                        : "Настройки сохранены. Дисплей 2 недоступен");
        statusView.setTextColor(launched ? COLOR_ACCENT : COLOR_MUTED);
    }

    private void updateSkinLabel() {
        skinValue.setText(SkinPreferences.SKIN_HORIZON.equals(selectedSkin)
                ? "ТЕМА\nHorizon — базовый скин  ▾"
                : "ТЕМА\nClassic — утверждённый дизайн  ▾");
    }

    private String groupTitle(SettingsCatalog.Group group) {
        if (group == SettingsCatalog.Group.MAIN) {
            return "ОСНОВНЫЕ ПРИБОРЫ";
        }
        if (group == SettingsCatalog.Group.TOP) {
            return "ВЕРХНЯЯ ПАНЕЛЬ";
        }
        return "НИЖНЯЯ ПАНЕЛЬ";
    }

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private Button button(String value, int background, int foreground) {
        Button view = new Button(getContext());
        view.setText(value);
        view.setTextSize(15.0f);
        view.setTextColor(foreground);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setAllCaps(false);
        view.setBackground(roundedBackground(background, 12));
        return view;
    }

    private GradientDrawable roundedBackground(int color, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        return background;
    }

    private LayoutParams matchWrap() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    private LayoutParams wrapWrap() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
