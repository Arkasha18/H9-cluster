package net.adminrunet.h9cluster;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.Context;

import net.adminrunet.h9cluster.skins.SkinRegistry;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class ProductionSystemIconsContractTest {
    @Test
    public void productionNeverCreatesImitatedSystemIcons() {
        Context context = RuntimeEnvironment.getApplication();
        assertFalse(BuildConfig.DEMO_MODE);
        assertNull(PreviewSystemIcons.create(context, SkinRegistry.ION_AURORA));
        assertNull(PreviewSystemIcons.create(context, SkinRegistry.CLASSIC));
    }

    @Test
    public void productionAssetsDoNotContainTheDemoSystemIconPhoto() {
        Context context = RuntimeEnvironment.getApplication();
        try (InputStream ignored = context.getAssets().open("demo_system_icons/native.png")) {
            fail("Demo-only native system icon bitmap leaked into production assets");
        } catch (IOException expected) {
            // Android must report this Demo-only asset as absent in the release variant.
        }
    }
}
