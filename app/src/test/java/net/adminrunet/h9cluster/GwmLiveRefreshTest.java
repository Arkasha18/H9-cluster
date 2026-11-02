package net.adminrunet.h9cluster;

import static org.junit.Assert.*;
import android.content.ComponentName;
import android.os.Binder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import ipc.fdbus.FdbusMessage;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class GwmLiveRefreshTest {
    @Test
    public void transportGapRequiresNewAbs3BeforeOtherFramesCanRenewSpeed() throws Exception {
        Fixture f = new Fixture(true);
        FdbusRpmReader reader = (FdbusRpmReader) get(f.source, "fdbusRpmReader");
        set(reader, "started", true);
        set(reader, "online", true);
        f.source.onDataChanged("car.basic.vehicle_speed", "-1");
        broadcast(reader, "0A1700001000000265002040042200000000000000000000001017");
        assertEquals(4, f.latest().speedKph);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        broadcast(reader, "0A0F000008000001110000000200178440100F");
        assertEquals(-1, f.latest().speedKph);
        assertEquals(0, reader.speedStreamFrameAtMs());
        broadcast(reader, "0A1700001000000265002000042200000000000000000000001017");
        assertEquals(0, f.latest().speedKph);
        f.source.stop();
    }

    @Test
    public void mcuSpeedBypassesFreshFrozenGwmCallbacksAndExpiresWithoutBinder() throws Exception {
        Fixture f = new Fixture(true);
        FdbusRpmReader reader = (FdbusRpmReader) get(f.source, "fdbusRpmReader");
        set(reader, "started", true);
        set(reader, "online", true);
        broadcast(reader, "0A1700001000000265002040042200000000000000000000001017");
        for (int i = 0; i < 30; i++) {
            f.source.onDataChanged("car.basic.vehicle_speed", "0");
            // ABS3 is quiet at constant speed; unrelated MCU frames keep the stream live.
            broadcast(reader, "0A0F000008000001110000000200178440100F");
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        }
        assertEquals(4, f.latest().speedKph);
        broadcast(reader, "0A1700001000000265002000042200000000000000000000001017");
        assertEquals(0, f.latest().speedKph);
        f.source.onServiceDisconnected(new ComponentName("test.adapter", "Service"));
        reader.onOffline(1);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2500));
        assertEquals(-1, f.latest().speedKph);
        assertTrue(Float.isNaN(f.latest().instantFuelConsumption));
        f.source.stop();
    }

    @Test
    public void wrongCanChannelAndInvalidSpeedFlagCannotSupplyFallback() throws Exception {
        Fixture f = new Fixture(true);
        FdbusRpmReader reader = (FdbusRpmReader) get(f.source, "fdbusRpmReader");
        set(reader, "started", true);
        set(reader, "online", true);
        f.source.onDataChanged("car.basic.vehicle_speed", "-1");
        broadcast(reader, "0A1701001000000265002040042200000000000000000000001017");
        assertEquals(-1, f.latest().speedKph);
        broadcast(reader, "0A1700001000000265002040042200000000000000000000001017");
        assertEquals(4, f.latest().speedKph);
        broadcast(reader, "0A1700001000000265000040042200000000000000000000001017");
        assertEquals(-1, f.latest().speedKph);
        f.source.stop();
    }

    @Test
    public void invalidCallbackWinsAgainstAnOlderSuccessfulSnapshot() throws Exception {
        Fixture f = new Fixture(false);
        f.adapter.speed = "48";
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        f.executor.runNext();
        f.source.onDataChanged("car.basic.vehicle_speed", "-1");
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(-1, f.latest().speedKph);
        f.source.stop();
    }

    private static void broadcast(FdbusRpmReader reader, String hex) {
        reader.onBroadcast(new FdbusMessage(1, 0x0100, McuVehicleSpeedTest.hex(hex)));
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void outsideTemperatureErrorClearsWhenAdapterRecovers() throws Exception {
        Fixture f = new Fixture(true);
        assertTrue(Float.isNaN(f.latest().outsideTemperatureC));
        f.source.onDataChanged("car.basic.outside_temp", "11.0");
        assertEquals(11.0f, f.latest().outsideTemperatureC, 0.0f);
        f.source.onDataChanged("car.basic.outside_temp", "-1001");
        assertTrue(Float.isNaN(f.latest().outsideTemperatureC));
        f.source.stop();
    }

    @Test
    public void rejectedListenerDoesNotFreezeInitialZeroSpeed() throws Exception {
        Fixture f = new Fixture(false);
        assertEquals(0, f.latest().speedKph);
        f.adapter.speed = "35";
        f.poll();
        assertEquals(35, f.latest().speedKph);
        f.adapter.speed = "0";
        f.poll();
        assertEquals(0, f.latest().speedKph);
        f.source.stop();
        assertTrue(f.executor.stopped);
        assertTrue(f.adapter.lastIds.contains("car.basic.vehicle_speed"));
        assertFalse(f.adapter.lastIds.contains("car.ipk_info.tts_contents"));
        assertFalse(f.adapter.lastIds.contains("car.basic.engine_speed"));
    }

    @Test
    public void silentRegisteredListenerAlsoRecoversButFreshEventsAvoidPolling() throws Exception {
        Fixture f = new Fixture(true);
        f.source.onDataChanged("car.basic.vehicle_speed", "42");
        int reads = f.adapter.reads;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertTrue(f.executor.tasks.isEmpty());
        assertEquals(reads, f.adapter.reads);
        f.adapter.speed = "55";
        f.poll();
        assertEquals(55, f.latest().speedKph);
        f.source.stop();
    }

    @Test
    public void newerSpeedEventWinsAgainstAnOlderBackgroundSnapshot() throws Exception {
        Fixture f = new Fixture(false);
        f.adapter.speed = "20";
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        f.executor.runNext();
        f.source.onDataChanged("car.basic.vehicle_speed", "48");
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(48, f.latest().speedKph);
        f.source.stop();
    }

    @Test
    public void invalidSpeedCannotManufactureZeroAndStoppedReaderCannotPublish() throws Exception {
        Fixture f = new Fixture(false);
        f.source.onDataChanged("car.basic.vehicle_speed", "48");
        f.source.onDataChanged("car.basic.vehicle_speed", "-1");
        assertEquals(-1, f.latest().speedKph);
        f.adapter.speed = "-1";
        f.poll();
        assertEquals(-1, f.latest().speedKph);
        f.adapter.speed = "60";
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        f.executor.runNext();
        int count = f.received.size();
        f.source.stop();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(count, f.received.size());
    }

    private static final class Fixture {
        final GwmClusterDataSource source = new GwmClusterDataSource(RuntimeEnvironment.getApplication());
        final Adapter adapter;
        final QueuedExecutor executor = new QueuedExecutor();
        final List<ClusterState> received = new ArrayList<>();
        Fixture(boolean acceptsListener) throws Exception {
            ShadowSystemClock.advanceBy(Duration.ofSeconds(60));
            adapter = new Adapter(acceptsListener);
            set(source, "started", true);
            set(source, "listener", (ClusterDataSource.Listener) received::add);
            source.onServiceConnected(new ComponentName("test.adapter", "Service"), adapter);
            set(source, "adapterRefreshExecutor", executor);
            ((Runnable) get(source, "speedWatchdog")).run();
        }
        ClusterState latest() { return received.get(received.size() - 1); }
        void poll() {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
            executor.runNext();
            shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private static final class Adapter extends Binder {
        final boolean acceptsListener;
        String speed = "0";
        String outsideTemperature = "-1001";
        int reads;
        final List<String> lastIds = new ArrayList<>();
        Adapter(boolean acceptsListener) { this.acceptsListener = acceptsListener; }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            data.enforceInterface("com.gwm.android.adapter.IGwmAdapterService");
            reply.writeNoException();
            if (code == 3) {
                reply.writeInt(acceptsListener ? 1 : 0);
                return true;
            }
            if (code == 4) { reply.writeInt(1); return true; }
            assertEquals("The reader may only use GET_DATA", 1, code);
            assertEquals(1, data.readInt());
            data.readString();
            String[] ids = data.createStringArray();
            assertNotNull(ids);
            lastIds.clear();
            Collections.addAll(lastIds, ids);
            String[] values = new String[ids.length];
            for (int i = 0; i < ids.length; i++) {
                values[i] = "car.basic.vehicle_speed".equals(ids[i]) ? speed
                        : "car.basic.outside_temp".equals(ids[i]) ? outsideTemperature : "0";
            }
            reply.writeStringArray(values);
            reads++;
            return true;
        }
    }

    private static final class QueuedExecutor extends AbstractExecutorService {
        final Queue<Runnable> tasks = new ArrayDeque<>();
        boolean stopped;
        @Override public void execute(Runnable runnable) { tasks.add(runnable); }
        void runNext() { assertFalse("No refresh scheduled", tasks.isEmpty()); tasks.remove().run(); }
        @Override public void shutdown() { stopped = true; }
        @Override public List<Runnable> shutdownNow() { stopped = true; tasks.clear(); return Collections.emptyList(); }
        @Override public boolean isShutdown() { return stopped; }
        @Override public boolean isTerminated() { return stopped; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return stopped; }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
