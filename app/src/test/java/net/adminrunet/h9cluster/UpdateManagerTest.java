package net.adminrunet.h9cluster;

import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public final class UpdateManagerTest {
    private static final String RELEASE_URL =
            "https://github.com/Arkasha18/H9-cluster/releases/download/"
                    + "v9.5.0/H9_Cluster_v9.5.0_adminrunet_release.apk";

    @Test
    public void newerVersionRequiresStrictlyGreaterSemanticVersion() {
        assertTrue(UpdateManager.isNewerVersion("9.4.0", "v9.5.0"));
        assertTrue(UpdateManager.isNewerVersion("v9.4", "9.4.1"));
        assertFalse(UpdateManager.isNewerVersion("9.4.0", "v9.4.0"));
        assertFalse(UpdateManager.isNewerVersion("9.5.0", "v9.4.0"));
        assertFalse(UpdateManager.isNewerVersion(
                "9.4.0-debug",
                "v9.4.0"));
        assertFalse(UpdateManager.isNewerVersion("unknown", "v9.5.0"));
    }

    @Test
    public void releaseAssetSelectionIgnoresUnrelatedApks() throws Exception {
        JSONArray assets = new JSONArray()
                .put(asset(
                        "H9-frame-calibrator.apk",
                        "https://github.com/Arkasha18/H9-cluster/"
                                + "releases/download/v9.5.0/"
                                + "H9-frame-calibrator.apk"))
                .put(asset(
                        "H9_Cluster_v9.5.0_adminrunet_release.apk",
                        RELEASE_URL));

        assertEquals(
                RELEASE_URL,
                UpdateManager.findReleaseApkUrl(assets, "v9.5.0"));
    }

    @Test
    public void releaseAssetSelectionRejectsWrongNameOrHost()
            throws Exception {
        JSONArray wrongName = new JSONArray().put(asset(
                "app-release.apk",
                RELEASE_URL));
        JSONArray wrongHost = new JSONArray().put(asset(
                "H9_Cluster_v9.5.0_adminrunet_release.apk",
                "https://example.com/H9_Cluster_v9.5.0_adminrunet_release.apk"));

        assertNull(UpdateManager.findReleaseApkUrl(
                wrongName,
                "v9.5.0"));
        assertNull(UpdateManager.findReleaseApkUrl(
                wrongHost,
                "v9.5.0"));
    }

    @Test
    public void apkIdentityMustMatchPackageVersionAndSigner() {
        UpdateManager.PackageIdentity installed = identity(
                "net.adminrunet.h9cluster",
                10,
                "release-signer");

        assertEquals(
                UpdateManager.ApkValidationResult.OK,
                UpdateManager.validateApkIdentity(
                        installed,
                        identity(
                                "net.adminrunet.h9cluster",
                                11,
                                "release-signer")));
        assertEquals(
                UpdateManager.ApkValidationResult.WRONG_PACKAGE,
                UpdateManager.validateApkIdentity(
                        installed,
                        identity(
                                "net.adminrunet.framecalibrator",
                                11,
                                "release-signer")));
        assertEquals(
                UpdateManager.ApkValidationResult.NOT_NEWER,
                UpdateManager.validateApkIdentity(
                        installed,
                        identity(
                                "net.adminrunet.h9cluster",
                                10,
                                "release-signer")));
        assertEquals(
                UpdateManager.ApkValidationResult.SIGNATURE_MISMATCH,
                UpdateManager.validateApkIdentity(
                        installed,
                        identity(
                                "net.adminrunet.h9cluster",
                                11,
                                "other-signer")));
    }

    @Test
    public void permissionReturnContinuesOnlyWithGrantAndPendingFile() {
        assertEquals(
                UpdateManager.PermissionResumeAction.INSTALL,
                UpdateManager.permissionResumeAction(true, true, true));
        assertEquals(
                UpdateManager.PermissionResumeAction.DENIED,
                UpdateManager.permissionResumeAction(true, false, true));
        assertEquals(
                UpdateManager.PermissionResumeAction.DENIED,
                UpdateManager.permissionResumeAction(true, true, false));
        assertEquals(
                UpdateManager.PermissionResumeAction.NONE,
                UpdateManager.permissionResumeAction(false, true, true));
    }

    private static JSONObject asset(String name, String url)
            throws Exception {
        return new JSONObject()
                .put("name", name)
                .put("browser_download_url", url);
    }

    private static UpdateManager.PackageIdentity identity(
            String packageName,
            long versionCode,
            String signer) {
        return new UpdateManager.PackageIdentity(
                packageName,
                versionCode,
                signer);
    }
}
