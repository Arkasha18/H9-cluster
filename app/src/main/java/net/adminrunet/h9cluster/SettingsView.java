package net.adminrunet.h9cluster;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Touch settings surface kept dependency-free for the Android 9 head unit. */
public final class SettingsView extends View {
    private static final float LOGICAL_WIDTH = 960.0f;
    private static final float LOGICAL_HEIGHT = 540.0f;
    private static final int COLOR_BACKGROUND = 0xFF071014;
    private static final int COLOR_CARD_SELECTED = 0xFF17343A;
    private static final int COLOR_TEXT = 0xFFF2F5F7;
    private static final int COLOR_MUTED = 0xFF98A7AE;
    private static final int COLOR_ACCENT = 0xFF31D7C5;
    private static final SkinOption[] SKINS = {
        new SkinOption(
                SkinPreferences.SKIN_CLASSIC,
                "Classic — утверждённый дизайн",
                "Финальный дизайн демо v8 с реальными показаниями автомобиля"),
        new SkinOption(
                SkinPreferences.SKIN_HORIZON,
                "Horizon — базовый скин",
                "Исходный дизайн проекта с подключением к GWM Adapter Service")
    };
    private static final CharSequence[] SKIN_TITLES = createSkinTitles();

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private String selectedSkin;
    private String status = "";
    private float contentScale = 1.0f;
    private float contentOffsetX;
    private float contentOffsetY;

    public SettingsView(Context context) {
        super(context);
        selectedSkin = SkinPreferences.getSelectedSkin(context);
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

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_ACCENT);
        canvas.drawRoundRect(255.0f, 340.0f, 705.0f, 398.0f, 14.0f, 14.0f, paint);
        drawCenteredText(
                canvas,
                BuildConfig.DEMO_MODE
                        ? "Сохранить и запустить"
                        : "Сохранить и запустить на дисплее 2",
                480.0f,
                376.0f,
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
                455.0f,
                16.0f,
                status.length() == 0 ? COLOR_MUTED : COLOR_ACCENT,
                false);

        canvas.restoreToCount(save);
    }

    private void drawSkinSelector(Canvas canvas) {
        SkinOption option = SKINS[getSelectedSkinIndex()];
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_CARD_SELECTED);
        canvas.drawRoundRect(110.0f, 160.0f, 850.0f, 280.0f, 16.0f, 16.0f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        paint.setColor(COLOR_ACCENT);
        canvas.drawRoundRect(110.0f, 160.0f, 850.0f, 280.0f, 16.0f, 16.0f, paint);

        drawLeftText(canvas, "Тема приборной панели", 140.0f, 192.0f, 15.0f, COLOR_MUTED, false);
        drawLeftText(canvas, option.title, 140.0f, 226.0f, 21.0f, COLOR_TEXT, true);
        drawLeftText(
                canvas,
                option.description,
                140.0f,
                254.0f,
                15.0f,
                COLOR_MUTED,
                false);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        paint.setColor(COLOR_ACCENT);
        canvas.drawLine(794.0f, 211.0f, 808.0f, 225.0f, paint);
        canvas.drawLine(808.0f, 225.0f, 822.0f, 211.0f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        float x = (event.getX() - contentOffsetX) / contentScale;
        float y = (event.getY() - contentOffsetY) / contentScale;
        if (x >= 110.0f && x <= 850.0f && y >= 160.0f && y <= 280.0f) {
            showSkinPicker();
            return true;
        }
        if (x >= 255.0f && x <= 705.0f && y >= 340.0f && y <= 398.0f) {
            SkinPreferences.setSelectedSkin(getContext(), selectedSkin);
            boolean launched = ClusterLauncher.startOnClusterDisplay(getContext());
            status = launched
                    ? BuildConfig.DEMO_MODE
                            ? "Тема сохранена и запущена"
                            : "Тема сохранена и запущена на дисплее 2"
                    : BuildConfig.DEMO_MODE
                            ? "Тема сохранена. Не удалось запустить Demo"
                            : "Тема сохранена. Дисплей 2 сейчас недоступен";
            invalidate();
            return true;
        }
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
                                selectedSkin = SKINS[which].id;
                                status = "";
                                dialog.dismiss();
                                invalidate();
                            }
                        })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private int getSelectedSkinIndex() {
        for (int index = 0; index < SKINS.length; index++) {
            if (SKINS[index].id.equals(selectedSkin)) {
                return index;
            }
        }
        return 0;
    }

    private static CharSequence[] createSkinTitles() {
        CharSequence[] titles = new CharSequence[SKINS.length];
        for (int index = 0; index < SKINS.length; index++) {
            titles[index] = SKINS[index].title;
        }
        return titles;
    }

    private static final class SkinOption {
        final String id;
        final String title;
        final String description;

        SkinOption(String id, String title, String description) {
            this.id = id;
            this.title = title;
            this.description = description;
        }
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
