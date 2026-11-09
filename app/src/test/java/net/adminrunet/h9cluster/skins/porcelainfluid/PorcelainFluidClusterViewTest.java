package net.adminrunet.h9cluster.skins.porcelainfluid;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.ClusterRenderer;
import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.SpeedDisplayMode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class PorcelainFluidClusterViewTest {
    private static final int W=1920,H=720;
    private static final String TEMPLATE="docs/H9_Cluster_Neutral_Design_Template_1920x720/";
    private static final String[] FIELDS={"speedKph","rpm","fuelLiters","rangeKm","coolantC","transmissionTemperatureC","outsideTemperatureC","engineFlywheelTorque","instantFuelConsumption","consumptionLitersPer100Km","voltage","odometerKm","dayKm","tripKm","steeringAngleDeg","tyreFrontLeftBar","tyreFrontRightBar","tyreRearLeftBar","tyreRearRightBar","wheelFrontLeftKph","wheelFrontRightKph","wheelRearLeftKph","wheelRearRightKph"};
    private final AtomicLong mono=new AtomicLong(60_000);
    private final AtomicLong wall=new AtomicLong(Instant.parse("2026-11-09T11:54:00Z").toEpochMilli());

    private PorcelainFluidClusterView view(boolean backdrop,boolean chrome) {
        PorcelainFluidClusterView v=new PorcelainFluidClusterView(RuntimeEnvironment.getApplication(),SpeedDisplayMode.REAL,backdrop,chrome,wall::get,mono::get);
        v.recordTextBounds();
        v.measure(View.MeasureSpec.makeMeasureSpec(W,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(H,View.MeasureSpec.EXACTLY));v.layout(0,0,W,H);return v;
    }

    @Test public void nativeLayersPreserveFactoryFieldsAndCalibratedMap() throws Exception {
        int[] mask=pixels(decode(TEMPLATE+"03_system_icons_forbidden_mask.png"));
        int[] base=pixels(decode(TEMPLATE+"04_yandex_map_black_gradient_rgba.png"));
        boolean[] reserved=new boolean[W*H],expanded=new boolean[W*H];int count=0;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(lowerLeft(x,y)&&Color.alpha(mask[y*W+x])>0){
            reserved[y*W+x]=true;count++;
            for(int yy=y-1;yy<=y+1;yy++)for(int xx=x-1;xx<=x+1;xx++)if(xx>=0&&xx<W&&yy>=0&&yy<H)expanded[yy*W+xx]=true;
        }
        assertEquals(40651,count);
        List<String> failures=new ArrayList<>();
        for(String name:new String[]{"motion","vehicle","zero","maximum","negative","missing","stale"}){
            Fixture fixture=fixture(name);
            PorcelainFluidClusterView full=view(true,true),fg=view(false,true),critical=view(false,false);
            full.setClusterState(fixture.build());fg.setClusterState(fixture.build());critical.setClusterState(fixture.build());
            Bitmap fullFrame=render(full),fgFrame=render(fg),criticalFrame=render(critical);
            write(fullFrame,"native-"+name+"-alpha.png");
            write(onBackground(fullFrame,Color.BLACK),"native-"+name+".png");
            if(name.equals("motion")){
                write(fgFrame,"native-foreground.png");write(criticalFrame,"native-critical.png");
                Bitmap overlay=onBackground(fullFrame,Color.BLACK);new Canvas(overlay).drawBitmap(decode(TEMPLATE+"05_system_fields_overlay.png"),0,0,null);write(overlay,"native-technical-overlay.png");
                write(onBackground(fullFrame,0xFFD6DDCF),"native-light-background.png");
                write(onBackground(fullFrame,0xFF22333A),"native-dark-background.png");
            }
            int[] a=pixels(fullFrame),b=pixels(fgFrame),c=pixels(criticalFrame);
            int strict=0,changedBase=0,core=0,criticalMask=0;
            String firstStrict="",firstCritical="";
            for(int y=0;y<H;y++)for(int x=0;x<W;x++){
                int i=y*W+x;
                if(expanded[i]&&Color.alpha(b[i])!=0){strict++;if(firstStrict.isEmpty())firstStrict=x+","+y;}
                if(reserved[i]&&Color.alpha(a[i])!=Color.alpha(base[i]))changedBase++;
                if(x>=672&&x<1096&&y>=80&&Color.alpha(a[i])!=0)core++;
                if(Color.alpha(mask[i])!=0&&!gearException(x,y)&&Color.alpha(c[i])!=0){criticalMask++;if(firstCritical.isEmpty())firstCritical=x+","+y;}
            }
            if(strict+changedBase+core+criticalMask>0)failures.add(name+": strict="+strict+" first="+firstStrict+", base="+changedBase+", map="+core+", critical="+criticalMask+" first="+firstCritical);
            for(Map.Entry<String,RectF> item:full.textBounds().entrySet()){
                RectF box=item.getValue();if(box.left<0||box.top<0||box.right>W||box.bottom>H)failures.add(name+" text outside: "+item.getKey()+" "+box);
            }
            fullFrame.recycle();fgFrame.recycle();criticalFrame.recycle();
        }
        assertTrue(String.join("\n",failures),failures.isEmpty());
    }

    @Test public void realStateDrivesEveryReadoutAndTankCapacity() {
        Fixture f=new Fixture();PorcelainFluidClusterView v=view(false,false);v.setClusterState(f.build());render(v).recycle();
        String[] expected={"154","2120","42","347","88","78","+24.0","184","9.8","12.2","14.2","58314","27.0","5804.2","+1°","2.40","2.36","2.37","2.37","154.0","154.1","153.9","154.0"};
        assertEquals(FIELDS.length,expected.length);
        for(int i=0;i<FIELDS.length;i++)assertEquals(FIELDS[i],expected[i],v.displayedValue(FIELDS[i]));
        assertEquals("7",v.displayedValue("currentGear"));assertEquals(42f/80,v.fuelFraction(),.0001f);
        PorcelainFluidClusterView largeTank=view(false,false);largeTank.setClusterState(f.build().withFuelTankCapacity(120));render(largeTank).recycle();
        assertEquals("63",largeTank.displayedValue("fuelLiters"));assertEquals(42f/80,largeTank.fuelFraction(),.0001f);
        assertEquals("347",largeTank.displayedValue("rangeKm"));
        Bitmap standardPixels=render(v),largeTankPixels=render(largeTank);
        for(int y=440;y<630;y++)for(int x=1250;x<1308;x++)assertEquals("Equal fuel percentage must draw the same liquid level",standardPixels.getPixel(x,y),largeTankPixels.getPixel(x,y));
        standardPixels.recycle();largeTankPixels.recycle();
        assertTrue(SkinRegistry.createRenderer(RuntimeEnvironment.getApplication(),SkinRegistry.PORCELAIN_FLUID) instanceof ClusterRenderer);
    }

    @Test public void missingDataAndStaleAtfNeverFabricateReadings() {
        PorcelainFluidClusterView v=view(false,false);render(v).recycle();
        for(String field:FIELDS)assertEquals(field,DASH(),v.displayedValue(field));
        assertEquals("",v.displayedValue("currentGear"));assertTrue(Float.isNaN(v.fuelFraction()));
        v.setClusterState(fixture("missing").build());render(v).recycle();
        for(String field:FIELDS)assertEquals(field,DASH(),v.displayedValue(field));
        assertTrue(Float.isNaN(v.displayedRpmValue()));assertTrue(Float.isNaN(v.indicatedSpeedKph()));
        v.setClusterState(new Fixture().build());mono.addAndGet(15_001);render(v).recycle();assertEquals(DASH(),v.displayedValue("transmissionTemperatureC"));
        Fixture future=new Fixture();future.atfTime=mono.get()+1;assertFalse(PorcelainFluidClusterView.isAtfFresh(future.build(),mono.get()));
        future.atfTime=0;assertFalse(PorcelainFluidClusterView.isAtfFresh(future.build(),mono.get()));
    }

    @Test public void gearOwnershipAndSharedSpeedModeArePreserved() {
        for(String selector:new String[]{"P","R","N","M","M3","",null})for(int ratio=0;ratio<=9;ratio++)assertEquals("",PorcelainFluidClusterView.currentGearLabel(selector,ratio));
        for(int ratio=1;ratio<=8;ratio++)assertEquals(Integer.toString(ratio),PorcelainFluidClusterView.currentGearLabel("D",ratio));
        assertEquals("",PorcelainFluidClusterView.currentGearLabel("D",9));
        Fixture f=new Fixture();
        for(SpeedDisplayMode mode:SpeedDisplayMode.values()){
            PorcelainFluidClusterView v=new PorcelainFluidClusterView(RuntimeEnvironment.getApplication(),mode,false,false,wall::get,mono::get);
            for(int speed:new int[]{0,1,8,80,154}){f.speed=speed;v.setClusterState(f.build());assertEquals(Math.round(mode.indicatedKph(speed)),v.indicatedSpeedKph(),.001f);assertEquals(speed<=1?"л/ч":"л/100 км",v.displayedValue("instantUnit"));}
        }
        PorcelainFluidClusterView hidden=view(false,false);hidden.setClusterState(f.build());hidden.setGearNumberVisible(false);Bitmap b=render(hidden);
        for(int y=19;y<79;y++)for(int x=922;x<1048;x++)assertEquals(0,Color.alpha(b.getPixel(x,y)));b.recycle();
    }

    @Test public void animatedMaterialDoesNotChangeDigitsAndRpmDigitsAreStabilized() throws Exception {
        Fixture f=new Fixture();PorcelainFluidClusterView v=view(false,false);v.setClusterState(f.build());Bitmap before=render(v);
        mono.addAndGet(600);Bitmap after=render(v);
        RectF speed=v.textBounds().get("speedKph");
        for(int y=(int)speed.top;y<=speed.bottom;y++)for(int x=(int)speed.left;x<=speed.right;x++)assertEquals(before.getPixel(x,y),after.getPixel(x,y));
        f.rpm=4500;v.setClusterState(f.build());boolean moving=false,held=false;int previous=v.rpmDigitValue();float previousNeedle=v.displayedRpmValue();
        for(int i=0;i<70;i++){mono.addAndGet(20);render(v).recycle();float needle=v.displayedRpmValue();int digits=v.rpmDigitValue();if(needle>previousNeedle && needle<4500)moving=true;if(needle>previousNeedle&&digits==previous)held=true;assertEquals(0,digits%10);previous=digits;previousNeedle=needle;}
        assertTrue(moving);assertTrue(held);assertEquals(4500,v.displayedRpmValue(),1);before.recycle();after.recycle();
        PorcelainFluidClusterView material=view(false,true);material.setClusterState(f.build());Bitmap a=render(material);mono.addAndGet(6000);Bitmap b=render(material);
        Bitmap ceramic=decode("app/src/main/assets/dashboard/skins/porcelainfluid/pearl-material.png");int changedCeramic=0;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(Color.alpha(ceramic.getPixel(x,y))>200 && a.getPixel(x,y)!=b.getPixel(x,y))changedCeramic++;
        assertTrue("Pearl highlights must move on ceramic, independently of liquid waves",changedCeramic>100);
        write(onBackground(a,Color.BLACK),"native-material-phase-a.png");write(onBackground(b,Color.BLACK),"native-material-phase-b.png");
        ceramic.recycle();a.recycle();b.recycle();
    }

    @Test public void dynamicClockAndAlphaAssetsKeepProductionContract() throws Exception {
        TimeZone original=TimeZone.getDefault();try{
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"));PorcelainFluidClusterView v=view(false,false);
            assertEquals("14:54",v.displayedValue("DashboardClock.time"));assertEquals("09.11.2026",v.displayedValue("DashboardClock.date"));
            wall.addAndGet(60_000);assertEquals("14:55",v.displayedValue("DashboardClock.time"));
        }finally{TimeZone.setDefault(original);}
        File calibration=file("app/src/main/assets/dashboard/skins/porcelainfluid/map_black_gradient.png");
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file(TEMPLATE+"04_yandex_map_black_gradient_rgba.png").toPath())),MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(calibration.toPath())));
        for(File asset:calibration.getParentFile().listFiles())assertFalse(asset.getName(),asset.getName().matches("0[23567]_.*"));
    }

    @Test public void movingScaleReflectionsKeepFactoryIndicatorsAndDigitsClear() throws Exception {
        int[] mask=pixels(decode(TEMPLATE+"03_system_icons_forbidden_mask.png"));
        boolean[] protectedPixels=new boolean[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(Color.alpha(mask[y*W+x])>0&&!gearException(x,y)) {
            protectedPixels[y*W+x]=true;
            if(lowerLeft(x,y))for(int yy=y-1;yy<=y+1;yy++)for(int xx=x-1;xx<=x+1;xx++)
                if(xx>=0&&xx<W&&yy>=0&&yy<H)protectedPixels[yy*W+xx]=true;
        }
        PorcelainFluidClusterView v=view(false,false);
        v.setClusterState(fixture("vehicle").build());
        Bitmap first=render(v);
        Map<String,RectF> digitBounds=v.textBounds();
        int speedChanges=0,rpmChanges=0;
        for(int frame=1;frame<=24;frame++) {
            mono.set(60_000+frame*375L);
            Bitmap current=render(v);
            int[] pixels=pixels(current);
            for(int i=0;i<pixels.length;i++)if(protectedPixels[i])
                assertEquals("Animation overlaps factory field at "+(i%W)+","+(i/W)+" frame "+frame,0,Color.alpha(pixels[i]));
            for(String key:new String[]{"speedKph","rpm","voltage","odometerKm","dayKm","tripKm","tyreFrontLeftBar","tyreFrontRightBar","tyreRearLeftBar","tyreRearRightBar"}) {
                RectF box=digitBounds.get(key);
                for(int y=(int)box.top;y<=box.bottom;y++)for(int x=(int)box.left;x<=box.right;x++)
                    assertEquals("Animation changes "+key,first.getPixel(x,y),current.getPixel(x,y));
            }
            for(int y=130;y<200;y++)for(int x=200;x<500;x++)if(first.getPixel(x,y)!=current.getPixel(x,y))speedChanges++;
            for(int y=120;y<180;y++)for(int x=1300;x<1500;x++)if(first.getPixel(x,y)!=current.getPixel(x,y))rpmChanges++;
            current.recycle();
        }
        assertTrue("Speed rim must visibly animate",speedChanges>100);
        assertTrue("RPM rim must visibly animate",rpmChanges>100);
        first.recycle();
    }

    @Test public void batteryPulseTravelsLeftToRightWithoutChangingVoltage() {
        PorcelainFluidClusterView v=view(false,false);
        v.setClusterState(fixture("vehicle").build());
        mono.set(60_643);Bitmap first=render(v);
        mono.set(61_071);Bitmap second=render(v);
        int leftBefore=brightness(first.getPixel(1589,583)),rightBefore=brightness(first.getPixel(1619,583));
        int leftAfter=brightness(second.getPixel(1589,583)),rightAfter=brightness(second.getPixel(1619,583));
        assertTrue("First fixed cell lights before the second",leftBefore>rightBefore+100);
        assertTrue("Pulse moves to the next fixed cell",rightAfter>leftAfter+100);
        assertEquals("14.3",v.displayedValue("voltage"));
        first.recycle();second.recycle();
    }

    @Test public void rpmPointerNeverCutsAcrossBatteryCells() {
        Fixture f=fixture("vehicle");f.rpm=0;
        PorcelainFluidClusterView baseline=view(false,false);baseline.setClusterState(f.build());
        Bitmap reference=render(baseline);
        for(int rpm=100;rpm<=2200;rpm+=100) {
            f.rpm=rpm;
            PorcelainFluidClusterView v=view(false,false);v.setClusterState(f.build());
            Bitmap current=render(v);
            for(int y=569;y<=598;y++)for(int x=1576;x<=1722;x++)
                assertEquals("RPM "+rpm+" obscures battery at "+x+","+y,reference.getPixel(x,y),current.getPixel(x,y));
            current.recycle();
        }
        reference.recycle();
    }

    private static String DASH(){return "—";}
    private static int brightness(int color){return Color.red(color)+Color.green(color)+Color.blue(color);}
    private static Bitmap render(View v){Bitmap b=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);b.setDensity(Bitmap.DENSITY_NONE);v.draw(new Canvas(b));return b;}
    private static Bitmap onBackground(Bitmap foreground,int color){Bitmap b=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(color);c.drawBitmap(foreground,0,0,null);return b;}
    private static int[] pixels(Bitmap b){int[] p=new int[W*H];b.getPixels(p,0,W,0,0,W,H);return p;}
    private static File file(String path){File f=new File(path);return f.isFile()?f:new File("../"+path);}
    private static Bitmap decode(String path){Bitmap b=BitmapFactory.decodeFile(file(path).getAbsolutePath());assertNotNull(path,b);b.setDensity(Bitmap.DENSITY_NONE);return b;}
    private static void write(Bitmap b,String name)throws Exception{File f=new File("build/reports/porcelainfluid",name);assertTrue(f.getParentFile().isDirectory()||f.getParentFile().mkdirs());try(FileOutputStream stream=new FileOutputStream(f)){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,stream));}}
    private static boolean lowerLeft(int x,int y){return (x>=387&&x<498&&y>=373&&y<442)||(x>=230&&x<548&&y>=494&&y<642);}
    private static boolean gearException(int x,int y){return x>=982&&x<1018&&y>=28&&y<66;}
    private Fixture fixture(String name){
        Fixture f=new Fixture();
        if(name.equals("vehicle")){f.speed=0;f.rpm=850;f.selector="P";f.gear=3;f.odo=9109;f.day=53.8f;f.trip=9100.3f;f.fuel=74;f.range=551;f.coolant=89;f.atf=79;f.voltage=14.3f;f.outside=Float.NaN;f.steering=-9;f.torque=31;f.pressure=new float[]{2.47f,2.42f,2.33f,2.39f};f.wheels=new float[]{35.3f,35.2f,35.5f,35.7f};}
        if(name.equals("zero")){f.speed=0;f.rpm=0;f.fuel=0;f.range=0;f.selector="P";f.gear=0;}
        if(name.equals("maximum")){f.speed=220;f.rpm=8000;f.odo=9999999;f.day=9999.9f;f.trip=9999.9f;f.range=9999;f.fuel=80;f.coolant=130;f.atf=140;f.torque=2000;f.steering=1080;}
        if(name.equals("negative")){f.outside=-50;f.coolant=-40;f.atf=-40;f.torque=-2000;f.steering=-1080;}
        if(name.equals("missing")){f.speed=-1;f.rpm=-1;f.gear=-1;f.selector=null;f.coolant=Integer.MIN_VALUE;f.atf=Float.NaN;f.fuel=Float.NaN;f.range=-1;f.odo=Double.NaN;f.day=Float.POSITIVE_INFINITY;f.trip=-1;f.instant=Float.NaN;f.average=-1;f.voltage=Float.POSITIVE_INFINITY;f.outside=Float.NaN;f.steering=Float.NaN;f.torque=Float.NaN;for(int i=0;i<4;i++){f.wheels[i]=Float.NaN;f.pressure[i]=Float.NaN;}}
        if(name.equals("stale"))f.atfTime=mono.get()-15_001;
        return f;
    }
    private final class Fixture {
        int speed=154,rpm=2120,gear=7,coolant=88,range=347;String selector="D";
        float atf=78,fuel=42,day=27,trip=5804.2f,instant=9.8f,average=12.2f,voltage=14.2f,outside=24,steering=1,torque=184;
        double odo=58314;long atfTime=mono.get();float[] pressure={2.4f,2.36f,2.37f,2.37f},wheels={154,154.1f,153.9f,154};
        ClusterState build(){long now=mono.get();return new ClusterState(speed,rpm,gear,selector,coolant,atf,fuel,range,odo,day,trip,pressure[0],pressure[1],pressure[2],pressure[3],instant,average,37.5f,voltage,outside,steering,wheels[0],wheels[1],wheels[2],wheels[3],torque,now,now,now,now,atfTime,"ECO");}
    }
}
