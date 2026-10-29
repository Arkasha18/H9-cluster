package net.adminrunet.h9cluster;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNetworkCapabilities;
import org.robolectric.shadows.ShadowNetworkInfo;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public final class UpdateManagerNetworkTest {
    private ActivityController<Activity> activityController;
    private ConnectivityManager connectivity;
    private UpdateManager updates;

    @Before
    public void setUp() {
        activityController = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = activityController.get();
        connectivity = (ConnectivityManager) activity.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(connectivity).clearAllNetworks();
        updates = new UpdateManager(activity, "9.7.2", true);
    }

    @After
    public void tearDown() {
        updates.destroy();
        activityController.pause().stop().destroy();
    }

    @Test
    public void vpnOnlyTransportAllowsUpdateRequestWithoutSystemValidation() {
        connect(ConnectivityManager.TYPE_VPN,
                NetworkCapabilities.TRANSPORT_VPN, true);

        assertTrue(isNetworkAvailable());
    }

    @Test
    public void wifiWithInternetAllowsUpdateRequest() {
        connect(ConnectivityManager.TYPE_WIFI,
                NetworkCapabilities.TRANSPORT_WIFI, true);

        assertTrue(isNetworkAvailable());
    }

    @Test
    public void localWifiWithoutInternetDoesNotAllowUpdateRequest() {
        connect(ConnectivityManager.TYPE_WIFI,
                NetworkCapabilities.TRANSPORT_WIFI, false);

        assertFalse(isNetworkAvailable());
    }

    @Test
    public void disconnectedNetworkDoesNotAllowUpdateRequest() {
        shadowOf(connectivity).setActiveNetworkInfo(null);

        assertFalse(isNetworkAvailable());
    }

    @Test
    public void missingCapabilitiesDuringDisconnectDoNotAllowUpdateRequest() {
        connect(ConnectivityManager.TYPE_VPN,
                NetworkCapabilities.TRANSPORT_VPN, true);
        shadowOf(connectivity).setNetworkCapabilities(
                connectivity.getActiveNetwork(), null);

        assertFalse(isNetworkAvailable());
    }

    private void connect(int type, int transport, boolean internet) {
        NetworkInfo info = ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED, type, 0, true, true);
        shadowOf(connectivity).setActiveNetworkInfo(info);
        NetworkCapabilities capabilities = ShadowNetworkCapabilities.newInstance();
        shadowOf(capabilities).addTransportType(transport);
        if (internet) {
            shadowOf(capabilities).addCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        shadowOf(connectivity).setNetworkCapabilities(
                connectivity.getActiveNetwork(), capabilities);
    }

    private boolean isNetworkAvailable() {
        return ReflectionHelpers.callInstanceMethod(updates, "isNetworkAvailable");
    }
}
