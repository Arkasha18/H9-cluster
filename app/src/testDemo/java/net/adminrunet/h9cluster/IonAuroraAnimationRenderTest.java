package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;

import net.adminrunet.h9cluster.skins.ionaurora.IonAuroraClusterView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;

/** Optional QA frames from the actual Android renderer, never a packaged skin asset. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class IonAuroraAnimationRenderTest {
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 720;
    private static final int FRAMES_PER_SECOND = 30;
    private static final int FRAME_COUNT = 120;

    @Test
    public void exportFourSecondsOfAccelerationAndBraking() throws IOException {
        assumeTrue("Set ION_AURORA_EXPORT_ANIMATION=1 to export optional QA frames",
                "1".equals(System.getenv("ION_AURORA_EXPORT_ANIMATION")));

        Context context = RuntimeEnvironment.getApplication();
        ShadowSystemClock.advanceBy(Duration.ofMillis(1L));
        IonAuroraClusterView skin = new IonAuroraClusterView(context);
        DemoSystemIconsView overlay = new DemoSystemIconsView(context);
        layoutScreen(skin);
        layoutScreen(overlay);

        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(bitmap);
        ClusterState initial = stateAt(0L);
        skin.setClusterState(initial);
        overlay.setClusterState(initial);
        skin.draw(canvas);

        // Settle the launch reveal while preserving the initial 86 km/h and 2400 rpm.
        for (int frame = 0; frame < 36; frame++) {
            advanceFrame(frame);
            skin.draw(canvas);
        }

        File directory = new File("build/reports/ionaurora/animation");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create animation QA directory: " + directory);
        }

        long captureStartedAtMs = SystemClock.elapsedRealtime();
        int exportedFrames = 0;
        try {
            for (int frame = 0; frame < FRAME_COUNT; frame++) {
                // Simulate 10 Hz telemetry with genuine 30 fps Canvas animation between samples.
                if (frame % 3 == 0) {
                    ClusterState state = stateAt(frame * 1000L / FRAMES_PER_SECOND);
                    skin.setClusterState(state);
                    overlay.setClusterState(state);
                }
                skin.draw(canvas);
                overlay.draw(canvas);
                File destination = new File(directory,
                        String.format(Locale.US, "frame-%04d.png", frame));
                try (FileOutputStream output = new FileOutputStream(destination)) {
                    assertTrue("Cannot encode animation frame " + frame,
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
                }
                exportedFrames++;
                advanceFrame(frame);
            }
        } finally {
            bitmap.recycle();
        }
        assertEquals(FRAME_COUNT, exportedFrames);
        assertEquals(4000L, SystemClock.elapsedRealtime() - captureStartedAtMs);
    }

    private static void layoutScreen(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
    }

    private static void advanceFrame(int frame) {
        long currentFrameMs = frame * 1000L / FRAMES_PER_SECOND;
        long nextFrameMs = (frame + 1L) * 1000L / FRAMES_PER_SECOND;
        ShadowSystemClock.advanceBy(Duration.ofMillis(nextFrameMs - currentFrameMs));
    }

    private static ClusterState stateAt(long elapsedMs) {
        float seconds = elapsedMs / 1000.0f;
        boolean accelerating = seconds <= 2.0f;
        float progress = accelerating ? seconds / 2.0f : (seconds - 2.0f) / 2.0f;
        float eased = smoothStep(progress);
        float speed = accelerating
                ? interpolate(86.0f, 150.0f, eased)
                : interpolate(150.0f, 60.0f, eased);
        float rpm = accelerating
                ? interpolate(2400.0f, 4200.0f, eased)
                : interpolate(4200.0f, 1700.0f, eased);
        float instant = accelerating
                ? interpolate(12.6f, 23.4f, eased)
                : interpolate(23.4f, 2.8f, eased);
        float torque = accelerating
                ? interpolate(224.0f, 380.0f, eased)
                : interpolate(380.0f, 42.0f, eased);
        long nowMs = Math.max(1L, SystemClock.elapsedRealtime());
        return new ClusterState(Math.round(speed), Math.round(rpm), 5, GearSelector.DRIVE,
                92, 78.0f, 47.0f, 421,
                28642.0, 42.3f, 167.8f, 2.35f, 2.37f, 2.42f, 2.40f,
                instant, 14.8f, 14.8f, 13.8f, 18.5f, -4.0f,
                speed - 0.2f, speed + 0.2f, speed - 0.1f, speed + 0.1f, torque,
                nowMs, nowMs, nowMs, nowMs, nowMs, "NORMAL");
    }

    private static float interpolate(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static float smoothStep(float progress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }
}
