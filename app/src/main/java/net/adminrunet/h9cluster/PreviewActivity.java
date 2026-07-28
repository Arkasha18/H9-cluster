package net.adminrunet.h9cluster;

import android.app.Activity;
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
    public static final String EXTRA_HAS_DRAFT = "has_draft";
    public static final String EXTRA_DRAFT_SKIN = "draft_skin";
    public static final String EXTRA_DRAFT_VISIBILITY_MASK =
            "draft_visibility_mask";
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
    private ClusterState lastState = ClusterState.empty();

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
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().getDecorView().setBackgroundColor(Color.TRANSPARENT);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        applyRequest(resolveRequest(getIntent()));

        dataSource = new GwmClusterDataSource(this);
        dataSource.start(new ClusterDataSource.Listener() {
            @Override
            public void onClusterState(ClusterState state) {
                lastState = state;
                clusterRenderer.setClusterState(state);
            }
        });
        hideSystemUi();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyRequest(resolveRequest(intent));
    }

    private PreviewRequest resolveRequest(Intent intent) {
        boolean hasDraft =
                intent != null && intent.getBooleanExtra(EXTRA_HAS_DRAFT, false);
        String draftSkin = hasDraft
                ? intent.getStringExtra(EXTRA_DRAFT_SKIN)
                : null;
        Object draftMask = hasDraft
                && intent.hasExtra(EXTRA_DRAFT_VISIBILITY_MASK)
                ? Long.valueOf(intent.getLongExtra(
                        EXTRA_DRAFT_VISIBILITY_MASK,
                        Long.MIN_VALUE))
                : null;
        return PreviewRequest.resolve(
                ClusterPreferences.load(this),
                hasDraft,
                draftSkin,
                draftMask);
    }

    private void applyRequest(PreviewRequest request) {
        if (request.skin.equals(activeSkin)
                && clusterRenderer instanceof ClassicCustomClusterView) {
            ((ClassicCustomClusterView) clusterRenderer)
                    .setBlockVisibility(request.visibility);
            return;
        }
        if (request.skin.equals(activeSkin) && clusterRenderer != null) {
            return;
        }

        View rendererView;
        if (SkinPreferences.SKIN_HORIZON.equals(request.skin)) {
            ClusterView horizonView = new ClusterView(this);
            clusterRenderer = horizonView;
            rendererView = horizonView;
        } else if (SkinPreferences.SKIN_CLASSIC_CUSTOM.equals(request.skin)) {
            ClassicCustomClusterView customView =
                    new ClassicCustomClusterView(this, request.visibility);
            clusterRenderer = customView;
            rendererView = customView;
        } else {
            ClassicClusterView classicView = new ClassicClusterView(this);
            clusterRenderer = classicView;
            rendererView = classicView;
        }
        activeSkin = request.skin;
        setContentView(rendererView);
        clusterRenderer.setClusterState(lastState);
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
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(IMMERSIVE_FLAGS);
    }
}
