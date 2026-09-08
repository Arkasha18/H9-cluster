package net.adminrunet.h9cluster.skins.ionaurora;

import static org.junit.Assert.assertEquals;

import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.GearSelector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Live-data parity checks independent of dashboard coordinates and artwork. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class IonAuroraTelemetryTest {
    private static final int WHITE = 0xFFFAFDFF;
    private static final int YELLOW = 0xFFFFD54F;

    private IonAuroraClusterView view;
    private Method updateTelemetryCache;

    @Before
    public void setUp() throws ReflectiveOperationException {
        view = new IonAuroraClusterView(RuntimeEnvironment.getApplication(), false);
        updateTelemetryCache = IonAuroraClusterView.class.getDeclaredMethod(
                "updateTelemetryCache", ClusterState.class);
        updateTelemetryCache.setAccessible(true);
    }

    @Test
    public void instantConsumptionUsesLiveDisplayValueWithoutRecalculation()
            throws ReflectiveOperationException {
        update(state(86, 12.6f, 14.8f, 72.3f));

        assertEquals("12.6", cached("instantValueText"));
        assertEquals("л/100 км", cached("instantUnitText"));
        assertEquals("14.8", cached("averageValueText"));
    }

    @Test
    public void averageUsesClusterAverageAndNeverSubstitutesJourneyAverage()
            throws ReflectiveOperationException {
        update(state(86, 12.6f, 14.8f, 72.3f));
        assertEquals("14.8", cached("averageValueText"));

        update(state(86, 12.6f, 14.8f, 5.1f));
        assertEquals("14.8", cached("averageValueText"));

        update(state(86, 12.6f, Float.NaN, 5.1f));
        assertEquals("—", cached("averageValueText"));

        update(state(86, 12.6f, 9.2f, Float.NaN));
        assertEquals("9.2", cached("averageValueText"));
    }

    @Test
    public void instantUnitsSwitchOnlyAboveOneKilometrePerHour()
            throws ReflectiveOperationException {
        for (int speed : new int[] {0, 1}) {
            update(state(speed, 1.4f, 14.8f, 72.3f));
            assertEquals("Stationary/creeping speed " + speed,
                    "л/ч", cached("instantUnitText"));
            assertEquals("1.4", cached("instantValueText"));
        }
        for (int speed : new int[] {2, 86, 220}) {
            update(state(speed, 8.6f, 14.8f, 72.3f));
            assertEquals("Moving speed " + speed,
                    "л/100 км", cached("instantUnitText"));
            assertEquals("8.6", cached("instantValueText"));
        }
    }

    @Test
    public void invalidConsumptionClearsPreviouslyCachedValues()
            throws ReflectiveOperationException {
        for (float invalid : new float[] {-0.1f, -100.0f, Float.NaN,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            update(state(86, 12.6f, 14.8f, 72.3f));
            update(state(86, invalid, invalid, 72.3f));
            assertEquals("Invalid instant value " + invalid,
                    "—", cached("instantValueText"));
            assertEquals("Invalid average value " + invalid,
                    "—", cached("averageValueText"));
        }
    }

    @Test
    public void zeroConsumptionIsValidAndUsesOneDecimal()
            throws ReflectiveOperationException {
        update(state(0, 0.0f, 0.0f, 72.3f));
        assertEquals("0.0", cached("instantValueText"));
        assertEquals("0.0", cached("averageValueText"));
    }

    @Test
    public void wheelSpeedsWithinTheSharedFourKphThresholdRemainWhite() {
        ClusterState matching = wheels(10.0f, 10.0f, 10.0f, 10.0f);
        assertEquals(WHITE, IonAuroraClusterView.wheelSpeedColor(matching, 10.0f));

        ClusterState belowThreshold = wheels(13.9f, 10.0f, 10.0f, 10.0f);
        assertEquals(WHITE, IonAuroraClusterView.wheelSpeedColor(belowThreshold, 13.9f));

        ClusterState atThreshold = wheels(14.0f, 10.0f, 10.0f, 10.0f);
        assertEquals(YELLOW, IonAuroraClusterView.wheelSpeedColor(atThreshold, 14.0f));

        ClusterState slowerOutlier = wheels(6.0f, 10.0f, 10.0f, 10.0f);
        assertEquals(YELLOW, IonAuroraClusterView.wheelSpeedColor(slowerOutlier, 6.0f));
    }

    @Test
    public void wheelSpeedWarningUsesEighteenPercentAtHigherSpeeds() {
        ClusterState belowThreshold = wheels(117.9f, 100.0f, 100.0f, 100.0f);
        assertEquals(WHITE, IonAuroraClusterView.wheelSpeedColor(belowThreshold, 117.9f));

        ClusterState aboveThreshold = wheels(118.1f, 100.0f, 100.0f, 100.0f);
        assertEquals(YELLOW, IonAuroraClusterView.wheelSpeedColor(aboveThreshold, 118.1f));
        assertEquals(WHITE, IonAuroraClusterView.wheelSpeedColor(aboveThreshold, 100.0f));

        ClusterState slowerOutlier = wheels(81.9f, 100.0f, 100.0f, 100.0f);
        assertEquals(YELLOW, IonAuroraClusterView.wheelSpeedColor(slowerOutlier, 81.9f));
    }

    @Test
    public void wheelWarningsRequireUsableReadingsAndAtLeastFourKph() {
        ClusterState stationary = wheels(0.0f, 0.0f, 0.0f, 0.0f);
        assertEquals(WHITE, IonAuroraClusterView.wheelSpeedColor(stationary, 0.0f));

        ClusterState creeping = wheels(3.9f, 0.0f, 0.0f, 0.0f);
        assertEquals(WHITE, IonAuroraClusterView.wheelSpeedColor(creeping, 3.9f));

        ClusterState atFourKph = wheels(4.0f, 0.0f, 0.0f, 0.0f);
        assertEquals(YELLOW, IonAuroraClusterView.wheelSpeedColor(atFourKph, 4.0f));

        ClusterState missingWheel = wheels(100.0f, Float.NaN, 100.0f, 100.0f);
        assertEquals(WHITE, IonAuroraClusterView.wheelSpeedColor(missingWheel, 100.0f));
        assertEquals(WHITE, IonAuroraClusterView.wheelSpeedColor(missingWheel, Float.NaN));
    }

    private void update(ClusterState state) throws ReflectiveOperationException {
        updateTelemetryCache.invoke(view, state);
    }

    private String cached(String fieldName) throws ReflectiveOperationException {
        Field field = IonAuroraClusterView.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(view);
    }

    private static ClusterState state(int speed, float instant, float average,
            float journeyAverage) {
        return state(speed, instant, average, journeyAverage,
                85.8f, 86.2f, 85.9f, 86.1f);
    }

    private static ClusterState wheels(float frontLeft, float frontRight,
            float rearLeft, float rearRight) {
        return state(86, 12.6f, 14.8f, 72.3f,
                frontLeft, frontRight, rearLeft, rearRight);
    }

    private static ClusterState state(int speed, float instant, float average,
            float journeyAverage, float frontLeft, float frontRight,
            float rearLeft, float rearRight) {
        return new ClusterState(speed, 2400, 5, GearSelector.DRIVE,
                92, 78.0f, 47.0f, 421,
                28642.0, 42.3f, 167.8f, 2.35f, 2.37f, 2.42f, 2.40f,
                instant, average, journeyAverage, 13.8f, 18.5f, -4.0f,
                frontLeft, frontRight, rearLeft, rearRight, 224.0f,
                1L, 1L, 1L, 1L, 1L, "NORMAL");
    }
}
