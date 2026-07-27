package net.adminrunet.h9cluster;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/** Full-screen host for the live cluster renderer. */
@SuppressWarnings("deprecation")
public final class PreviewActivity extends Activity {
    public static final String EXTRA_RELOAD_SKIN = "reload_skin";
    public static final String EXTRA_SINGLE_DISPLAY_FALLBACK = "single_display_fallback";
    private static final String TAG = "H9Cluster";
    private static final int IMMERSIVE_FLAGS =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

    private ClusterRenderer clusterRenderer;
    private String activeSkin;
    private ClusterDataSource dataSource;

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

        ClusterPreferences.Snapshot settings = ClusterPreferences.load(this);
        activeSkin = settings.skin;
        View rendererView;
        if (SkinPreferences.SKIN_HORIZON.equals(activeSkin)) {
            ClusterView horizonView = new ClusterView(this, settings.visibility);
            clusterRenderer = horizonView;
            rendererView = horizonView;
        } else {
            ClassicClusterView classicView =
                    new ClassicClusterView(this, settings.visibility);
            clusterRenderer = classicView;
            rendererView = classicView;
        }
        rendererView.setBackgroundColor(backgroundColor);
        setContentView(rendererView);
        if (shouldReturnToSettingsOnInteraction()) {
            rendererView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    returnToSettings();
                }
            });
        }

        dataSource = BuildConfig.DEMO_MODE
                ? new DemoClusterDataSource(this)
                : new GwmClusterDataSource(this);
        dataSource.start(new ClusterDataSource.Listener() {
            @Override
            public void onClusterState(ClusterState state) {
                clusterRenderer.setClusterState(state);
            }
        });
        hideSystemUi();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String selectedSkin = SkinPreferences.getSelectedSkin(this);
        if (!selectedSkin.equals(activeSkin)
                || intent.getBooleanExtra(EXTRA_RELOAD_SKIN, false)) {
            recreate();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
    }

    @Override
    protected void onDestroy() {
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
