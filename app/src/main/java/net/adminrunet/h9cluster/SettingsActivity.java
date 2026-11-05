package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinSettings;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;

import java.util.Map;

/** Main-display settings window. Boot startup never opens this activity. */
public final class SettingsActivity extends Activity {
    private SkinSettingsSession session;
    private SettingsView settingsView;
    private boolean unsavedPreviewActive;
    private UpdateManager updateManager;
    private LogUploadManager logUploadManager;
    private VehicleIdentityReader identityReader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(0xFF071014);
        getWindow().setNavigationBarColor(0xFF071014);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);
        AutostartPreferences.setAutostartSuspended(this, false);
        session = new SkinSettingsSession(
                SkinPreferences.getSelectedSkin(this),
                new SkinSettingsSession.Loader() {
                    @Override
                    public SkinSettings load(String skinId) {
                        return SkinSettingsStore.load(
                                SettingsActivity.this,
                                skinId);
                    }
                });
        session.selectSpeedDisplayMode(
                SkinPreferences.getSpeedDisplayMode(this));
        session.selectFuelTankCapacity(
                SkinPreferences.getFuelTankCapacityLiters(this));
        session.setGearNumberVisible(SkinPreferences.isGearNumberVisible(this));
        settingsView = new SettingsView(
                this,
                session,
                new SettingsView.Listener() {
                    @Override
                    public void onDraftChanged(
                            SkinSettingsSession.Snapshot draft) {
                        if (ClusterLauncher.previewOnClusterDisplay(
                                SettingsActivity.this,
                                draft)) {
                            unsavedPreviewActive = true;
                        }
                    }

                    @Override
                    public void onSaveRequested(
                            SkinSettingsSession.Snapshot draft) {
                        SkinPreferences.setSelectedSkin(
                                SettingsActivity.this,
                                draft.skinId);
                        SkinPreferences.setSpeedDisplayMode(
                                SettingsActivity.this,
                                draft.speedDisplayMode);
                        SkinPreferences.setFuelTankCapacityLiters(
                                SettingsActivity.this,
                                draft.fuelTankCapacityLiters);
                        SkinPreferences.setGearNumberVisible(
                                SettingsActivity.this,
                                draft.gearNumberVisible);
                        for (Map.Entry<String, SkinSettings> entry
                                : session.drafts().entrySet()) {
                            SkinSettingsStore.save(
                                    SettingsActivity.this,
                                    entry.getKey(),
                                    entry.getValue());
                        }
                        unsavedPreviewActive = false;
                        boolean launched =
                                ClusterLauncher.applyOnClusterDisplay(
                                        SettingsActivity.this);
                        settingsView.showSaveResult(launched);
                    }

                    @Override
                    public void onCheckForUpdatesRequested() {
                        if (updateManager != null) {
                            updateManager.checkForUpdatesManually();
                        }
                    }

                    @Override
                    public void onSendLogsRequested() {
                        if (logUploadManager != null) {
                            logUploadManager.uploadLastDay();
                        }
                    }

                    @Override
                    public void onExitRequested() {
                        exitApplication();
                    }
                });
        setContentView(settingsView);
        settingsView.setMachineIdentity(MachineIdentity.current(this));

        identityReader = new VehicleIdentityReader(
                this,
                new VehicleIdentityReader.Listener() {
                    @Override
                    public void onIdentity(MachineIdentity.Identity identity) {
                        settingsView.setMachineIdentity(identity);
                    }
                });
        identityReader.start();

        logUploadManager = new LogUploadManager(
                this,
                new LogUploadManager.Listener() {
                    @Override
                    public void onUploadStarted(String machineId) {
                        settingsView.showLogUploadStarted(machineId);
                    }

                    @Override
                    public void onUploadFinished(LogUploadManager.Result result) {
                        settingsView.showLogUploadResult(result);
                    }
                });

        boolean updatesEnabled = !BuildConfig.DEBUG && !BuildConfig.DEMO_MODE;
        updateManager = new UpdateManager(
                this,
                BuildConfig.VERSION_NAME,
                updatesEnabled);
        updateManager.checkForUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        settingsView.setMachineIdentity(MachineIdentity.current(this));
        if (updateManager != null) {
            updateManager.onResume();
        }
    }

    @Override
    public void onBackPressed() {
        restorePersistedPreview();
        super.onBackPressed();
    }

    @Override
    protected void onStop() {
        if (isFinishing()) {
            restorePersistedPreview();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (updateManager != null) {
            updateManager.destroy();
        }
        if (logUploadManager != null) {
            logUploadManager.destroy();
        }
        if (identityReader != null) {
            identityReader.stop();
        }
        super.onDestroy();
    }

    private void restorePersistedPreview() {
        if (unsavedPreviewActive) {
            unsavedPreviewActive = false;
            ClusterLauncher.applyOnClusterDisplay(this);
        }
    }

    /**
     * Closes the cluster window on Display 2 first, otherwise it would keep
     * reading vehicle data after the settings window is gone. The unsaved
     * preview is dropped so leaving the screen cannot restart the cluster.
     */
    private void exitApplication() {
        DiagnosticLog.info("settings", "exit_application_requested");
        unsavedPreviewActive = false;
        AutostartPreferences.setAutostartSuspended(this, true);
        ClusterDisplayRecovery.cancel();
        ClusterWindowRegistry.closeAll();
        finishAndRemoveTask();
    }
}
