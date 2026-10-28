package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.SkinSettings;
import net.adminrunet.h9cluster.skins.SkinSettingsProvider;
import net.adminrunet.h9cluster.skins.SpeedDisplayMode;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Touch settings surface kept dependency-free for the Android 9 head unit. */
@SuppressLint("ViewConstructor")
public final class SettingsView extends View {
    private static final float LOGICAL_WIDTH = 960.0f;
    private static final float LOGICAL_HEIGHT = 540.0f;
    private static final int COLOR_BACKGROUND = 0xFF071014;
    private static final int COLOR_CARD_SELECTED = 0xFF17343A;
    private static final int COLOR_TEXT = 0xFFF2F5F7;
    private static final int COLOR_MUTED = 0xFF98A7AE;
    private static final int COLOR_ACCENT = 0xFF31D7C5;
    private static final int COLOR_DANGER = 0xFFE2574C;
    private static final SkinRegistry.Definition[] SKINS =
            SkinRegistry.getDefinitions();
    private static final CharSequence[] SKIN_TITLES = createSkinTitles();
    private static final SpeedDisplayMode[] SPEED_MODES = {
            SpeedDisplayMode.REAL,
            SpeedDisplayMode.FACTORY_PLUS_4_5_PERCENT,
            SpeedDisplayMode.FACTORY_PLUS_5_KPH,
    };
    private static final String[] SPEED_MODE_TITLES = {
            "Реальная",
            "Как в авто +4,5%",
            "Как в авто +5 км/ч",
    };
    private static final int[] FUEL_TANK_CAPACITIES = {
            FuelTankCapacity.DEFAULT_LITERS,
            FuelTankCapacity.SMALL_LITERS,
    };
    private static final float CONFIGURE_LEFT = 590.0f;
    private static final float CONFIGURE_RIGHT = 850.0f;
    private static final float CONFIGURE_TOP = 318.0f;
    private static final float CONFIGURE_BOTTOM = 360.0f;
    private static final float SAVE_TOP = 368.0f;
    private static final float SAVE_BOTTOM = 416.0f;
    private static final float STATUS_BASELINE = 440.0f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final SkinSettingsSession session;
    private final Listener listener;
    private String status = "";
    private float contentScale = 1.0f;
    private float contentOffsetX;
    private float contentOffsetY;

    interface Listener {
        void onDraftChanged(SkinSettingsSession.Snapshot draft);

        void onSaveRequested(SkinSettingsSession.Snapshot draft);

        void onCheckForUpdatesRequested();

        void onExitRequested();
    }

    SettingsView(
            Context context,
            SkinSettingsSession session,
            Listener listener) {
        super(context);
        this.session = session;
        this.listener = listener;
        setBackgroundColor(COLOR_BACKGROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(COLOR_BACKGROUND);
        contentScale = Math.min(
                getWidth() / LOGICAL_WIDTH,
                getHeight() / LOGICAL_HEIGHT);
        contentOffsetX = (getWidth() - LOGICAL_WIDTH * contentScale) * 0.5f;
        contentOffsetY = (getHeight() - LOGICAL_HEIGHT * contentScale) * 0.5f;

        int save = canvas.save();
        canvas.translate(contentOffsetX, contentOffsetY);
        canvas.scale(contentScale, contentScale);

        drawCenteredText(canvas, "H9 Cluster", 480.0f, 54.0f, 34.0f, COLOR_TEXT, true);
        drawCenteredText(
                canvas,
                "Разработчик: admin.ru.net",
                480.0f,
                84.0f,
                18.0f,
                COLOR_ACCENT,
                false);
        drawCenteredText(
                canvas,
                BuildConfig.DEMO_MODE
                        ? "Выберите тему для автономного Demo"
                        : "Выберите тему, которая будет автоматически запускаться на дисплее 2",
                480.0f,
                125.0f,
                17.0f,
                COLOR_MUTED,
                false);

        drawSkinSelector(canvas);
        drawSpeedControl(canvas);
        drawFuelControl(canvas);

        boolean configurable = selectedDefinition().hasSettings();
        if (configurable) {
            drawConfigureButton(canvas);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_ACCENT);
        canvas.drawRoundRect(
                255.0f,
                SAVE_TOP,
                705.0f,
                SAVE_BOTTOM,
                14.0f,
                14.0f,
                paint);
        drawCenteredText(
                canvas,
                saveButtonTitle(),
                480.0f,
                SAVE_TOP + 36.0f,
                19.0f,
                Color.BLACK,
                true);

        drawCenteredText(
                canvas,
                status.length() == 0
                        ? BuildConfig.DEMO_MODE
                                ? "Demo использует только тестовые данные"
                                : "При автозапуске основной дисплей остаётся свободным"
                        : status,
                480.0f,
                STATUS_BASELINE,
                16.0f,
                status.length() == 0 ? COLOR_MUTED : COLOR_ACCENT,
                false);

        drawExitButton(canvas);
        if (updatesEnabled()) {
            drawUpdateButton(canvas);
        }

        canvas.restoreToCount(save);
    }

    private String saveButtonTitle() {
        if (!selectedDefinition().hasRenderer()) {
            return "Сохранить и показать штатную панель";
        }
        return BuildConfig.DEMO_MODE
                ? "Сохранить и запустить"
                : "Сохранить и запустить на дисплее 2";
    }

    private void drawExitButton(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        paint.setColor(COLOR_DANGER);
        canvas.drawRoundRect(
                SettingsExitButton.LEFT,
                SettingsExitButton.TOP,
                SettingsExitButton.RIGHT,
                SettingsExitButton.BOTTOM,
                12.0f,
                12.0f,
                paint);
        drawCenteredText(
                canvas,
                "Выйти из приложения",
                480.0f,
                SettingsExitButton.TOP + 29.0f,
                17.0f,
                COLOR_DANGER,
                true);
    }

    private void drawUpdateButton(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        paint.setColor(COLOR_ACCENT);
        canvas.drawRoundRect(
                SettingsUpdateButton.LEFT,
                SettingsUpdateButton.TOP,
                SettingsUpdateButton.RIGHT,
                SettingsUpdateButton.BOTTOM,
                12.0f,
                12.0f,
                paint);
        drawCenteredText(
                canvas,
                "Проверить обновление",
                (SettingsUpdateButton.LEFT + SettingsUpdateButton.RIGHT) * 0.5f,
                SettingsUpdateButton.TOP + 29.0f,
                16.0f,
                COLOR_ACCENT,
                true);
    }

    private void drawConfigureButton(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_CARD_SELECTED);
        canvas.drawRoundRect(
                CONFIGURE_LEFT,
                CONFIGURE_TOP,
                CONFIGURE_RIGHT,
                CONFIGURE_BOTTOM,
                12.0f,
                12.0f,
                paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        paint.setColor(COLOR_ACCENT);
        canvas.drawRoundRect(
                CONFIGURE_LEFT,
                CONFIGURE_TOP,
                CONFIGURE_RIGHT,
                CONFIGURE_BOTTOM,
                12.0f,
                12.0f,
                paint);
        drawCenteredText(
                canvas,
                "Настройки темы",
                (CONFIGURE_LEFT + CONFIGURE_RIGHT) * 0.5f,
                CONFIGURE_TOP + 31.0f,
                17.0f,
                COLOR_TEXT,
                true);
    }

    private void drawSkinSelector(Canvas canvas) {
        SkinRegistry.Definition option = selectedDefinition();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_CARD_SELECTED);
        canvas.drawRoundRect(110.0f, 160.0f, 850.0f, 252.0f, 16.0f, 16.0f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        paint.setColor(COLOR_ACCENT);
        canvas.drawRoundRect(110.0f, 160.0f, 850.0f, 252.0f, 16.0f, 16.0f, paint);

        drawLeftText(canvas, "Тема приборной панели", 140.0f, 184.0f, 15.0f, COLOR_MUTED, false);
        drawLeftText(canvas, option.title, 140.0f, 212.0f, 21.0f, COLOR_TEXT, true);
        drawLeftText(
                canvas,
                option.description,
                140.0f,
                236.0f,
                15.0f,
                COLOR_MUTED,
                false);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        paint.setColor(COLOR_ACCENT);
        canvas.drawLine(794.0f, 198.0f, 808.0f, 212.0f, paint);
        canvas.drawLine(808.0f, 212.0f, 822.0f, 198.0f, paint);
    }

    private void drawSpeedControl(Canvas canvas) {
        drawLeftText(
                canvas,
                "Спидометр",
                SettingsSpeedControl.LABEL_X,
                SettingsSpeedControl.LABEL_BASELINE,
                15.0f,
                COLOR_MUTED,
                false);

        SpeedDisplayMode current = session.snapshot().speedDisplayMode;
        for (int index = 0;
                index < SettingsSpeedControl.SEGMENT_COUNT;
                index++) {
            boolean selected = SPEED_MODES[index] == current;
            float left = SettingsSpeedControl.segmentLeft(index);
            float right = SettingsSpeedControl.segmentRight(index);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(selected ? COLOR_ACCENT : COLOR_CARD_SELECTED);
            canvas.drawRoundRect(
                    left,
                    SettingsSpeedControl.TOP,
                    right,
                    SettingsSpeedControl.BOTTOM,
                    12.0f,
                    12.0f,
                    paint);
            if (!selected) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.0f);
                paint.setColor(COLOR_CARD_SELECTED);
                canvas.drawRoundRect(
                        left,
                        SettingsSpeedControl.TOP,
                        right,
                        SettingsSpeedControl.BOTTOM,
                        12.0f,
                        12.0f,
                        paint);
            }
            drawCenteredText(
                    canvas,
                    SPEED_MODE_TITLES[index],
                    SettingsSpeedControl.segmentCenterX(index),
                    SettingsSpeedControl.TEXT_BASELINE,
                    15.0f,
                    selected ? Color.BLACK : COLOR_TEXT,
                    selected);
        }
    }

    private void drawFuelControl(Canvas canvas) {
        drawLeftText(canvas, "Объём бака", 140.0f,
                SettingsFuelControl.TEXT_BASELINE, 15.0f, COLOR_MUTED, false);
        for (int index = 0; index < SettingsFuelControl.SEGMENT_COUNT; index++) {
            int capacity = FUEL_TANK_CAPACITIES[index];
            boolean selected = capacity == session.snapshot().fuelTankCapacityLiters;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(selected ? COLOR_ACCENT : COLOR_CARD_SELECTED);
            canvas.drawRoundRect(
                    SettingsFuelControl.segmentLeft(index), SettingsFuelControl.TOP,
                    SettingsFuelControl.segmentRight(index), SettingsFuelControl.BOTTOM,
                    12.0f, 12.0f, paint);
            drawCenteredText(canvas, capacity + " л",
                    SettingsFuelControl.segmentCenterX(index),
                    SettingsFuelControl.TEXT_BASELINE, 17.0f,
                    selected ? Color.BLACK : COLOR_TEXT, selected);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        performClick();
        float x = (event.getX() - contentOffsetX) / contentScale;
        float y = (event.getY() - contentOffsetY) / contentScale;
        if (x >= 110.0f && x <= 850.0f && y >= 160.0f && y <= 252.0f) {
            showSkinPicker();
            return true;
        }
        int speedSegment = SettingsSpeedControl.segmentAt(x, y);
        if (speedSegment >= 0) {
            selectSpeedDisplayMode(SPEED_MODES[speedSegment]);
            return true;
        }
        int fuelSegment = SettingsFuelControl.segmentAt(x, y);
        if (fuelSegment >= 0) {
            int capacity = FUEL_TANK_CAPACITIES[fuelSegment];
            if (capacity != session.snapshot().fuelTankCapacityLiters) {
                session.selectFuelTankCapacity(capacity);
                status = "Предпросмотр объёма бака: " + capacity + " л";
                listener.onDraftChanged(session.snapshot());
                invalidate();
            }
            return true;
        }
        boolean configurable = selectedDefinition().hasSettings();
        if (configurable
                && x >= CONFIGURE_LEFT
                && x <= CONFIGURE_RIGHT
                && y >= CONFIGURE_TOP
                && y <= CONFIGURE_BOTTOM) {
            showSettingsEditor();
            return true;
        }
        if (x >= 255.0f
                && x <= 705.0f
                && y >= SAVE_TOP
                && y <= SAVE_BOTTOM) {
            listener.onSaveRequested(session.snapshot());
            return true;
        }
        if (SettingsExitButton.contains(x, y)) {
            confirmExit();
            return true;
        }
        if (updatesEnabled() && SettingsUpdateButton.contains(x, y)) {
            listener.onCheckForUpdatesRequested();
            return true;
        }
        return true;
    }

    private static boolean updatesEnabled() {
        return !BuildConfig.DEBUG && !BuildConfig.DEMO_MODE;
    }

    private void confirmExit() {
        new AlertDialog.Builder(getContext())
                .setTitle("Выйти из приложения?")
                .setMessage(
                        "Приборная панель закроется, чтение данных автомобиля"
                                + " прекратится. Выбранная тема и её настройки"
                                + " сохранятся.")
                .setPositiveButton(
                        "Выйти",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                listener.onExitRequested();
                            }
                        })
                .setNegativeButton("Отмена", null)
                .show();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void showSkinPicker() {
        new AlertDialog.Builder(getContext())
                .setTitle("Выберите тему")
                .setSingleChoiceItems(
                        SKIN_TITLES,
                        getSelectedSkinIndex(),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                session.selectSkin(SKINS[which].id);
                                status = "Предпросмотр темы изменён";
                                dialog.dismiss();
                                listener.onDraftChanged(session.snapshot());
                                invalidate();
                            }
                        })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void selectSpeedDisplayMode(SpeedDisplayMode mode) {
        if (mode == session.snapshot().speedDisplayMode) {
            return;
        }
        session.selectSpeedDisplayMode(mode);
        status = "Предпросмотр режима спидометра изменён";
        listener.onDraftChanged(session.snapshot());
        invalidate();
    }

    private int getSelectedSkinIndex() {
        for (int index = 0; index < SKINS.length; index++) {
            if (SKINS[index].id.equals(session.snapshot().skinId)) {
                return index;
            }
        }
        return 0;
    }

    private SkinRegistry.Definition selectedDefinition() {
        return SkinRegistry.getDefinition(session.snapshot().skinId);
    }

    private void showSettingsEditor() {
        SkinRegistry.Definition definition = selectedDefinition();
        if (!definition.hasSettings()) {
            return;
        }
        SkinSettingsSession.Snapshot snapshot = session.snapshot();
        View editor = definition.createSettingsEditor(
                getContext(),
                snapshot.settings,
                new SkinSettingsProvider.Listener() {
                    @Override
                    public void onSettingsChanged(SkinSettings settings) {
                        session.updateSettings(settings);
                        status = "Предпросмотр настроек изменён";
                        listener.onDraftChanged(session.snapshot());
                        invalidate();
                    }
                });
        new AlertDialog.Builder(getContext())
                .setTitle("Настройки: " + definition.title)
                .setView(editor)
                .setPositiveButton("Готово", null)
                .show();
    }

    void showSaveResult(boolean launched) {
        if (!selectedDefinition().hasRenderer()) {
            status = "Настройки сохранены. Показана штатная панель";
            invalidate();
            return;
        }
        status = launched
                ? BuildConfig.DEMO_MODE
                        ? "Настройки сохранены и запущены"
                        : "Настройки сохранены и запущены на дисплее 2"
                : BuildConfig.DEMO_MODE
                        ? "Настройки сохранены. Не удалось запустить Demo"
                        : "Настройки сохранены. Ждём дисплей 2 и запустим автоматически";
        invalidate();
    }

    private static CharSequence[] createSkinTitles() {
        CharSequence[] titles = new CharSequence[SKINS.length];
        for (int index = 0; index < SKINS.length; index++) {
            titles[index] = SKINS[index].title;
        }
        return titles;
    }

    private void drawCenteredText(
            Canvas canvas,
            String text,
            float centerX,
            float baseline,
            float size,
            int color,
            boolean bold) {
        configureText(size, color, Paint.Align.CENTER, bold);
        canvas.drawText(text, centerX, baseline, paint);
    }

    private void drawLeftText(
            Canvas canvas,
            String text,
            float left,
            float baseline,
            float size,
            int color,
            boolean bold) {
        configureText(size, color, Paint.Align.LEFT, bold);
        canvas.drawText(text, left, baseline, paint);
    }

    private void configureText(float size, int color, Paint.Align align, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTextAlign(align);
        paint.setFakeBoldText(bold);
    }
}
