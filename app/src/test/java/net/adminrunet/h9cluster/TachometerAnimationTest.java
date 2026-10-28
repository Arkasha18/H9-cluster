package net.adminrunet.h9cluster;

import static org.junit.Assert.*;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.adminrunet.h9cluster.skins.ionaurora.IonAuroraClusterView;
import net.adminrunet.h9cluster.skins.sculptedtitanium.SculptedTitaniumClusterView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class TachometerAnimationTest {
    @Test
    public void bothSkinsAnimateTheGaugeWithoutChangingDigitsEveryFrame() throws Exception {
        verify(new SculptedTitaniumClusterView(RuntimeEnvironment.getApplication()), "updateRpm", "rpm");
        verify(new IonAuroraClusterView(RuntimeEnvironment.getApplication()), "updateMotion", "cachedRpmText");
    }

    private static void verify(View view, String updateName, String textName) throws Exception {
        Method update = view.getClass().getDeclaredMethod(updateName, long.class);
        update.setAccessible(true);
        Field number = view.getClass().getDeclaredField(textName);
        number.setAccessible(true);
        Field gauge = view.getClass().getDeclaredField("displayedRpm");
        gauge.setAccessible(true);
        ClusterRenderer renderer = (ClusterRenderer) view;
        renderer.setClusterState(state(2000));
        update.invoke(view, 1000L);
        assertEquals("2000", number.get(view));
        ClusterState acceleration = state(3500);
        renderer.setClusterState(acceleration);
        String lastText = (String) number.get(view);
        long lastChange = 1000;
        int numberChanges = 0, gaugeChanges = 0;
        float previous = gauge.getFloat(view);
        for (long now = 1020; now <= 2200; now += 20) {
            update.invoke(view, now);
            float animated = gauge.getFloat(view);
            assertTrue(animated >= previous && animated <= 3500);
            if (animated > previous) gaugeChanges++;
            previous = animated;
            String next = (String) number.get(view);
            if (!lastText.equals(next)) {
                assertTrue("Digits changed too fast in " + view.getClass().getSimpleName(), now - lastChange >= 160);
                lastText = next;
                lastChange = now;
                numberChanges++;
            }
        }
        assertTrue(gaugeChanges > 15);
        assertTrue(numberChanges >= 3 && numberChanges <= 7);
        assertEquals(3500, Integer.parseInt(lastText), 10);
        assertEquals(3500, acceleration.rpm);
        renderer.setClusterState(state(0));
        for (long now = 2220; now <= 4000; now += 20) update.invoke(view, now);
        assertEquals("0", number.get(view));
        assertEquals(0, gauge.getFloat(view), 0);
    }

    private static ClusterState state(int rpm) {
        return new ClusterState(40, rpm, 3, "D", 88, 78, 47, 486,
                28644, 44, 169.5f, 2.40f, 2.36f, 2.37f, 2.37f,
                15.8f, 14.8f, 12.3f, 13.8f, 18.8f, 12,
                40, 40, 40, 40, 197, 1000, 1000, 1000, 1000, 1000, "ECO");
    }
}
