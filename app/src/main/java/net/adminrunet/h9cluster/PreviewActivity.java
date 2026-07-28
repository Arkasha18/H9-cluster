package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.SkinSettings;
import net.adminrunet.h9cluster.trip.DemoSpeedometerHotspot;
import net.adminrunet.h9cluster.trip.TripSessionStore;
import net.adminrunet.h9cluster.trip.TripSummary;
import net.adminrunet.h9cluster.trip.TripSummaryCoordinator;
import net.adminrunet.h9cluster.trip.TripSummaryView;
import net.adminrunet.h9cluster.trip.TripTelemetry;
import net.adminrunet.h9cluster.trip.TripTelemetryDiagnostics;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

/** Full-screen host for the live cluster renderer. */
@SuppressWarnings("deprecation")
public final class PreviewActivity extends Activity {
    public static final String EXTRA_RELOAD_SKIN = "reload_skin";
    public static final String EXTRA_SINGLE_DISPLAY_FALLBACK = "single_display_fallback";
    public static final String EXTRA_HAS_DRAFT = "has_skin_settings_draft";
    public static final String EXTRA_DRAFT_SKIN = "draft_skin";
    public static final String EXTRA_DRAFT_SETTINGS = "draft_skin_settings";
    public static final String EXTRA_DEMO_INVALID_CONSUMPTION =
            "demo_invalid_consumption";
    private static final String TAG = "H9Cluster";
    private static final String TRIP_TELEMETRY_TAG = "H9TripTelemetry";
    private static final int IMMERSIVE_FLAGS =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

    private ClusterRenderer clusterRenderer;
    private View rendererView;
    private FrameLayout rootView;
    private SkinSettingsSession.Snapshot activeSnapshot;
    private ClusterDataSource dataSource;
    private TripSummaryCoordinator tripCoordinator;
    private ClusterState lastState = ClusterState.empty();
    private long lastTripTelemetryLogAtMs = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Starting build "
                + BuildConfig.VERSION_NAME
                + "-display2-api28");

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setDimAmount(0.0f);
        getWindow().setFormat(PreviewAppearance.usesOpaqueWindow(BuildConfig.DEMO_MODE)
                ? PixelFormat.OPAQUE
                : PixelFormat.TRANSLUCENT);
        int backgroundColor = PreviewAppearance.backgroundColor(BuildConfig.DEMO_MODE);
        getWindow().setBackgroundDrawable(new ColorDrawable(backgroundColor));
        getWindow().getDecorView().setBackgroundColor(backgroundColor);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(backgroundColor);
        setContentView(rootView);
        applySnapshot(resolveSnapshot(getIntent()), true);

        tripCoordinator = new TripSummaryCoordinator(
                new TripSessionStore(this),
                new TripSummaryCoordinator.Listener() {
                    @Override
                    public void onTripSummary(TripSummary summary) {
                        showTripSummary(summary);
                    }
                },
                SystemClock::elapsedRealtime);
        boolean invalidDemoConsumption = BuildConfig.DEMO_MODE
                && getIntent().getBooleanExtra(
                        EXTRA_DEMO_INVALID_CONSUMPTION,
                        false);
        dataSource = BuildConfig.DEMO_MODE
                ? new DemoClusterDataSource(this, invalidDemoConsumption)
                : new GwmClusterDataSource(this);
        if (BuildConfig.DEMO_MODE) {
            addDemoTouchLayer();
        }
        dataSource.start(new ClusterDataSource.Listener() {
            @Override
            public void onClusterState(ClusterState state) {
                lastState = state;
                clusterRenderer.setClusterState(state);
                logTripTelemetryIfDue(state);
                tripCoordinator.onClusterState(state);
            }
        });
        hideSystemUi();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        boolean forceReload = intent != null
                && intent.getBooleanExtra(EXTRA_RELOAD_SKIN, false);
        applySnapshot(resolveSnapshot(intent), forceReload);
    }

    private SkinSettingsSession.Snapshot resolveSnapshot(Intent intent) {
        boolean hasDraft = intent != null
                && intent.getBooleanExtra(EXTRA_HAS_DRAFT, false);
        if (hasDraft) {
            String draftSkin = intent.getStringExtra(EXTRA_DRAFT_SKIN);
            if (SkinRegistry.isSupported(draftSkin)) {
                SkinSettings draftSettings = SkinSettingsTransport.fromBundle(
                        intent.getBundleExtra(EXTRA_DRAFT_SETTINGS));
                return new SkinSettingsSession.Snapshot(
                        draftSkin,
                        SkinRegistry.normalizeSettings(
                                draftSkin,
                                draftSettings));
            }
        }
        String persistedSkin = SkinPreferences.getSelectedSkin(this);
        return new SkinSettingsSession.Snapshot(
                persistedSkin,
                SkinSettingsStore.load(this, persistedSkin));
    }

    private void applySnapshot(
            SkinSettingsSession.Snapshot snapshot,
            boolean forceReload) {
        if (!forceReload && snapshot.equals(activeSnapshot)) {
            return;
        }
        View replacement = SkinRegistry.createRenderer(
                this,
                snapshot.skinId,
                snapshot.settings);
        clusterRenderer = (ClusterRenderer) replacement;
        activeSnapshot = snapshot;
        replacement.setBackgroundColor(
                PreviewAppearance.backgroundColor(BuildConfig.DEMO_MODE));
        if (rendererView != null) {
            rootView.removeView(rendererView);
        }
        rendererView = replacement;
        rootView.addView(
                rendererView,
                0,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        clusterRenderer.setClusterState(lastState);
    }

    private void addDemoTouchLayer() {
        View touchLayer = new View(this);
        touchLayer.setBackgroundColor(Color.TRANSPARENT);
        touchLayer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return true;
                }
                view.performClick();
                if (DemoSpeedometerHotspot.contains(
                        event.getX(),
                        event.getY(),
                        view.getWidth(),
                        view.getHeight())) {
                    ((DemoClusterDataSource) dataSource).requestEngineStop();
                } else {
                    returnToSettings();
                }
                return true;
            }
        });
        rootView.addView(
                touchLayer,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void showTripSummary(TripSummary summary) {
        TripSummaryView summaryView = new TripSummaryView(this, summary);
        if (BuildConfig.DEMO_MODE) {
            summaryView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    returnToSettings();
                }
            });
        }
        rootView.addView(
                summaryView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void logTripTelemetryIfDue(ClusterState state) {
        long nowMs = SystemClock.elapsedRealtime();
        if (!TripTelemetryDiagnostics.shouldLog(
                lastTripTelemetryLogAtMs,
                nowMs)) {
            return;
        }
        TripTelemetry telemetry = TripTelemetry.from(state, nowMs);
        Log.i(
                TRIP_TELEMETRY_TAG,
                TripTelemetryDiagnostics.format(
                        telemetry,
                        telemetry.journeyOdometerKm));
        lastTripTelemetryLogAtMs = nowMs;
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
    }

    @Override
    protected void onDestroy() {
        if (tripCoordinator != null) {
            tripCoordinator.flush();
        }
        if (dataSource != null) {
            dataSource.stop();
        }
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (shouldReturnToSettingsOnInteraction()) {
            returnToSettings();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(IMMERSIVE_FLAGS);
    }

    private boolean shouldReturnToSettingsOnInteraction() {
        return ClusterDisplayPolicy.shouldReturnToSettingsOnInteraction(
                BuildConfig.DEMO_MODE,
                getIntent().getBooleanExtra(EXTRA_SINGLE_DISPLAY_FALLBACK, false));
    }

    @SuppressWarnings("deprecation")
    private void returnToSettings() {
        int currentDisplayId = getWindowManager().getDefaultDisplay().getDisplayId();
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        settingsIntent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(currentDisplayId);
        startActivity(settingsIntent, options.toBundle());
        finish();
    }
}
