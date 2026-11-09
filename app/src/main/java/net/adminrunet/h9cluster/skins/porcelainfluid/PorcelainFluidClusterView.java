package net.adminrunet.h9cluster.skins.porcelainfluid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import net.adminrunet.h9cluster.ClusterRenderer;
import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.FuelTankCapacity;
import net.adminrunet.h9cluster.GearSelector;
import net.adminrunet.h9cluster.RpmReadoutStabilizer;
import net.adminrunet.h9cluster.TransmissionTemperatureAlert;
import net.adminrunet.h9cluster.skins.DashboardClock;
import net.adminrunet.h9cluster.skins.SpeedDisplayMode;
import net.adminrunet.h9cluster.skins.WifiIndicator;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/** Native transparent instrument layer. All measurements come from ClusterState. */
public final class PorcelainFluidClusterView extends View implements ClusterRenderer {
    private static final String ASSETS = "dashboard/skins/porcelainfluid/";
    private static final String DASH = "—";
    private static final int WHITE = 0xFFF2F5F3, MUTED = 0xFFD4E0E1;
    private static final int DARK = 0xFF102120, AQUA = 0xFF8EF4EF;
    private static final int WARNING = 0xFFFFD54F, RED = 0xFFFF654F;
    private static final long ATF_STALE_MS = 15_000L;
    private static final float SHIMMER = .9f;
    private static final String[] POSITIONS = {"FL", "FR", "RL", "RR"};
    private static final String[] PRESSURES = {"tyreFrontLeftBar", "tyreFrontRightBar", "tyreRearLeftBar", "tyreRearRightBar"};
    private static final String[] WHEELS = {"wheelFrontLeftKph", "wheelFrontRightKph", "wheelRearLeftKph", "wheelRearRightKph"};
    private static final float[][] SPEED_PATH = {{0,555,640},{20,280,630},{40,201,565},{60,137,492},{80,91,425},{100,96,354},{120,145,283},{140,223,218},{180,381,153},{220,557,137}};
    private static final float[][] RPM_PATH = {{0,1516,642},{1000,1683,629},{1500,1745,606},{2000,1784,513},{3000,1810,402},{4000,1784,319},{5000,1718,247},{6000,1642,185},{7000,1460,145},{8000,1280,143}};
    private static final float[][] LEFT_Y = {{0,0},{220,220},{360,344},{440,440},{540,540},{600,650},{690,706},{720,720}};
    private static final float[][] LEFT_LABELS={{0,500,628},{40,220,558},{80,128,430},{120,175,284},{180,366,188},{220,559,165}};
    private static final float[][] RIGHT_LABELS={{0,1516,624},{2,1765,540},{4,1750,300},{6,1565,190},{8,1277,170}};
    private static final String[] DISTANCES={"odometerKm","dayKm","tripKm"},DISTANCE_TITLES={"ODO","DAY","TRIP"};

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wifiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path(), inkPath = new Path();
    private final Path fuelCavity = new Path(), coolantCavity = new Path();
    private final Path[] scaleTracks = {new Path(), new Path()};
    private final PathMeasure[] scaleMeasures = {new PathMeasure(), new PathMeasure()};
    private final Path scaleTrail = new Path();
    private final RectF ink = new RectF();
    private final float[] point = new float[2];
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, RectF> bounds = new LinkedHashMap<>();
    private final Typeface display, numeric, caption, gear;
    private final Bitmap backdrop, chrome;
    private final LinearGradient glint;
    private final Matrix glintMatrix = new Matrix();
    private final Shader fuelGradient=liquidGradient(1256,44),coolantGradient=liquidGradient(1215,77);
    private final Shader batteryGradient = new LinearGradient(0, 571, 0, 596,
            new int[]{0xFF58DCD4, 0xFF1B888F, 0xFF093D49}, null, Shader.TileMode.CLAMP);
    private final SpeedDisplayMode speedMode;
    private final LongSupplier wallClock, monotonicClock;
    private final DashboardClock clock = new DashboardClock();
    private final WifiIndicator wifi;
    private final TransmissionTemperatureAlert atfAlert = new TransmissionTemperatureAlert();
    private RpmReadoutStabilizer rpmReadout = new RpmReadoutStabilizer();
    private ClusterState state;
    private boolean gearNumberVisible = true, rpmInitialized, recordBounds;
    private float indicatedSpeed = Float.NaN, displayedRpm = Float.NaN;
    private float liquidFuel = Float.NaN, liquidCoolant = Float.NaN, cellVoltage = Float.NaN;
    private int rpmDigits = -1;
    private long lastRpmFrameMs, lastLiquidFrameMs;

    public PorcelainFluidClusterView(Context context, SpeedDisplayMode mode) {
        this(context, mode, true, true, System::currentTimeMillis, SystemClock::elapsedRealtime);
    }

    // Layer switches and clocks are only exposed to native renderer tests.
    PorcelainFluidClusterView(Context context, SpeedDisplayMode mode, boolean includeBackdrop,
            boolean includeChrome, LongSupplier wallClock, LongSupplier monotonicClock) {
        super(context);
        this.speedMode = mode == null ? SpeedDisplayMode.REAL : mode;
        this.wallClock = wallClock;
        this.monotonicClock = monotonicClock;
        display = Typeface.createFromAsset(context.getAssets(), ASSETS + "PFDisplay.ttf");
        numeric = Typeface.createFromAsset(context.getAssets(), ASSETS + "PFData.ttf");
        caption = Typeface.createFromAsset(context.getAssets(), ASSETS + "PFLabel.ttf");
        gear = Typeface.createFromAsset(context.getAssets(), "fonts/Rajdhani-Medium.ttf");
        backdrop = includeBackdrop ? loadBitmap(context, "map_black_gradient.png") : null;
        chrome = includeChrome ? loadBitmap(context, "chrome.png") : null;
        glint = new LinearGradient(-260, 0, 260, 720,
                new int[]{0x00B8FFFA,0x079AF2FF,0x92FFFFFF,0x12F8CFEF,0x00F8CFEF},
                new float[]{0,.32f,.5f,.69f,1}, Shader.TileMode.CLAMP);
        if (includeChrome) {
            Bitmap material = loadBitmap(context, "pearl-material.png");
            glintPaint.setShader(new ComposeShader(new BitmapShader(material,
                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP), glint, PorterDuff.Mode.SRC_IN));
            glintPaint.setAlpha(Math.round(255 * SHIMMER));
        }
        wifi = new WifiIndicator(context);
        prepareScaleTrack(false);
        prepareScaleTrack(true);
        fuelCavity.moveTo(1257,463);fuelCavity.cubicTo(1256,450,1299,450,1299,466);
        fuelCavity.lineTo(1299,597);fuelCavity.cubicTo(1298,624,1257,624,1256,599);fuelCavity.close();
        coolantCavity.moveTo(1213,256);coolantCavity.cubicTo(1219,280,1245,287,1269,300);
        coolantCavity.cubicTo(1299,317,1290,342,1269,347);
        coolantCavity.cubicTo(1238,354,1232,331,1225,308);coolantCavity.close();
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        setContentDescription("Приборная панель Porcelain Fluid");
        updateReadouts();
    }

    @Override public void setGearNumberVisible(boolean visible) { gearNumberVisible = visible; invalidate(); }

    @Override public void setClusterState(ClusterState snapshot) {
        if (snapshot == null) return;
        state = snapshot;
        updateReadouts();
        updateRpm(monotonicClock.getAsLong());
        postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        float scale = Math.min(getWidth()/1920f, getHeight()/720f);
        int saved = canvas.save();
        canvas.translate((getWidth()-1920*scale)/2, (getHeight()-720*scale)/2);
        canvas.scale(scale, scale);
        if (backdrop != null) canvas.drawBitmap(backdrop,0,0,bitmapPaint);
        if (chrome != null) canvas.drawBitmap(chrome,0,0,bitmapPaint);
        long now = monotonicClock.getAsLong();
        updateRpm(now);
        updateLiquids(now);
        float seconds = (now % 120_000L)/1000f;
        if (chrome != null) {
            glintMatrix.setTranslate((seconds % 40f)*68-450,0);
            glint.setLocalMatrix(glintMatrix);
            canvas.drawRect(0,0,1920,720,glintPaint);
        }
        drawLiquid(canvas,fuelCavity,1256,456,44,160,fuelFraction(),seconds,1.5f,fuelGradient);
        drawLiquid(canvas,coolantCavity,1215,265,77,86,(liquidCoolant-40)/90,seconds*.7f,1.1f,coolantGradient);
        drawBattery(canvas,seconds);
        drawScale(canvas,false,seconds);
        drawScale(canvas,true,seconds);
        if(recordBounds)bounds.clear();
        drawReadouts(canvas,now);
        canvas.restoreToCount(saved);
        if (isShown() && getWindowVisibility() == VISIBLE) postInvalidateDelayed(33);
    }

    private void drawReadouts(Canvas c,long now) {
        clock.update(wallClock.getAsLong());
        values.put("transmissionTemperatureC",formatAtf(state,now));
        label(c,"WHEEL · км/ч",140,18,18,200);
        for(int i=0;i<4;i++) {
            float x=i%2==0?93:226, y=i<2?44:71;
            label(c,POSITIONS[i],x-59,y-2,21,32);
            value(c,WHEELS[i],x,y,28,90,WHITE);
        }
        label(c,"МОМЕНТ",337,18,18,115);
        value(c,"engineFlywheelTorque",337,60,38,110,WHITE);
        label(c,"Н·м",409,50,18,36);
        text(c,"DashboardClock.date",clock.getDateText(),545,23,25,165,WHITE,numeric);
        text(c,"DashboardClock.time",clock.getTimeText(),779,58,46,155,WHITE,numeric);
        wifi.draw(c,wifiPaint,890,42,now);
        if(gearNumberVisible) text(c,"currentGear",values.get("currentGear"),1000,63,44,36,WHITE,gear);
        label(c,"РУЛЬ",1144,20,18,90);
        value(c,"steeringAngleDeg",1144,60,36,130,WHITE);
        label(c,"СНАРУЖИ · °C",1634,20,18,180);
        text(c,"outsideTemperatureC",values.get("outsideTemperatureC")+"°",1634,60,38,150,WHITE,numeric);
        label(c,"ATF · °C",1825,20,18,135);
        TransmissionTemperatureAlert.Level level=atfAlert.update(state==null?Float.NaN:state.transmissionTemperatureC,isAtfFresh(state,now));
        text(c,"transmissionTemperatureC",values.get("transmissionTemperatureC")+"°",1825,60,38,130,atfColor(level),numeric);
        text(c,"speedCaption","СКОРОСТЬ",355,215,22,190,DARK,caption);
        text(c,"speedKph",values.get("speedKph"),355,318,128,260,DARK,display);
        text(c,"speedUnit","км/ч",355,348,26,110,DARK,caption);
        text(c,"rpm",values.get("rpm"),1595,450,118,326,DARK,display);
        text(c,"rpmUnit","об/мин",1595,482,26,140,DARK,caption);
        for(int i=0;i<3;i++) {
            float y=419+i*29;
            label(c,DISTANCE_TITLES[i],189,y-1,18,51);
            value(c,DISTANCES[i],274,y,27,116,WHITE);
            label(c,"км",350,y-1,17,28);
        }
        label(c,"ДАВЛЕНИЕ ШИН · bar",1560,222,17,250);
        for(int i=0;i<4;i++) {
            float x=i%2==0?1513:1661,y=i<2?254:290;
            label(c,POSITIONS[i],x-51,y-2,18,28);
            value(c,PRESSURES[i],x,y,28,76,WHITE);
        }
        label(c,"ОЖ · °C",1269,218,20,110);
        value(c,"coolantC",1270,261,42,115,state!=null && validCoolant(state.coolantC) && state.coolantC>=115?RED:WHITE);
        label(c,"ТОПЛИВО",1394,494,22,160);
        text(c,"fuelLiters",values.get("fuelLiters")+" л",1395,543,50,160,state!=null && validNonnegative(state.fuelLiters) && state.fuelLiters<10?WARNING:WHITE,numeric);
        text(c,"rangeKm",values.get("rangeKm")+" км",1395,584,36,176,WHITE,numeric);
        label(c,"АКБ",1665,514,22,90);
        text(c,"voltage",values.get("voltage")+" В",1665,554,42,180,state!=null && validNonnegative(state.voltage) && state.voltage<12?WARNING:WHITE,numeric);
        label(c,"РАСХОД",52,580,18,96);
        label(c,"МИГ",25,621,19,42);
        value(c,"instantFuelConsumption",86,626,36,86,WHITE);
        label(c,values.get("instantUnit"),86,648,18,100);
        label(c,"СРЕД",23,681,18,44);
        value(c,"consumptionLitersPer100Km",86,686,36,86,WHITE);
        label(c,"л/100 км",86,708,18,100);
        for(float[] label:LEFT_LABELS) text(c,recordBounds?"speedScale"+(int)label[0]:null,Integer.toString((int)label[0]),leftX(label[1]),leftY(label[2]),30,70,WHITE,numeric);
        for(float[] label:RIGHT_LABELS) text(c,recordBounds?"rpmScale"+(int)label[0]:null,Integer.toString((int)label[0]),label[1],label[2],30,50,WHITE,numeric);
    }

    private void prepareScaleTrack(boolean rpm) {
        int index = rpm ? 1 : 0;
        Path track = scaleTracks[index];
        for (int i = 0; i <= 192; i++) {
            gaugePoint(rpm, (rpm ? 8000f : 220f) * i / 192f, point);
            if (i == 0) track.moveTo(point[0], point[1]);
            else track.lineTo(point[0], point[1]);
        }
        scaleMeasures[index].setPath(track, false);
    }

    private void drawScaleFlow(Canvas c, boolean rpm, float seconds) {
        int index = rpm ? 1 : 0;
        PathMeasure measure = scaleMeasures[index];
        float length = measure.getLength();
        float trailLength = length * .22f;
        float duration = rpm ? 6f : 8f;
        float head = ((seconds + (rpm ? 2f : 0f)) % duration) / duration
                * (length + trailLength);
        shapePaint.setShader(null);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeCap(Paint.Cap.ROUND);
        shapePaint.setStrokeWidth(1.25f);
        shapePaint.setColor(0x383CE0D8);
        c.drawPath(scaleTracks[index], shapePaint);
        // A thin moving reflection stays on the rim; the actual reading keeps
        // its separate, full-length white tick. Nothing rotates across digits.
        for (int i = 0; i < 18; i++) {
            float start = head - trailLength + trailLength * i / 18;
            float end = start + trailLength / 18;
            if (end <= 0 || start >= length) continue;
            scaleTrail.rewind();
            if (!measure.getSegment(Math.max(0, start), Math.min(length, end),
                    scaleTrail, true)) continue;
            float intensity = (i + 1) / 18f;
            intensity *= intensity;
            shapePaint.setColor(Color.argb(Math.round(70 * intensity), 48, 234, 223));
            shapePaint.setStrokeWidth(6);
            c.drawPath(scaleTrail, shapePaint);
            shapePaint.setColor(Color.argb(Math.round(235 * intensity), 165, 255, 243));
            shapePaint.setStrokeWidth(2.2f);
            c.drawPath(scaleTrail, shapePaint);
        }
    }

    private void drawScale(Canvas c,boolean rpm,float seconds) {
        int max=rpm?8000:220,step=rpm?200:5;
        float reading=rpm?displayedRpm:indicatedSpeed;
        if (Float.isFinite(reading)) drawScaleFlow(c, rpm, seconds);
        shapePaint.setShader(null);shapePaint.setStyle(Paint.Style.STROKE);shapePaint.setStrokeCap(Paint.Cap.BUTT);
        for(int v=0;v<=max;v+=step) {
            gaugePoint(rpm,v,point);
            boolean major=rpm?v%1000==0:(v==0||v==40||v==80||v==120||v==180||v==220);
            float dx=(rpm?1560:340)-point[0],dy=365-point[1],length=(float)Math.hypot(dx,dy);
            float tick=major?12:5;
            boolean onWhite=rpm && point[1]>440 && point[1]<530 && point[0]>1770;
            shapePaint.setColor(onWhite?DARK:major?0xFFDEFCF9:0xFF94C9C8);shapePaint.setStrokeWidth(major?2:1.25f);
            c.drawLine(point[0],point[1],point[0]+dx/length*tick,point[1]+dy/length*tick,shapePaint);
        }
        if(!Float.isFinite(reading)){shapePaint.setStyle(Paint.Style.FILL);return;}
        gaugePoint(rpm,reading,point);
        float dx=(rpm?1560:340)-point[0],dy=365-point[1],length=(float)Math.hypot(dx,dy);
        shapePaint.setStrokeCap(Paint.Cap.ROUND);shapePaint.setColor(0x5532E9E5);shapePaint.setStrokeWidth(7);
        c.drawLine(point[0],point[1],point[0]+dx/length*17,point[1]+dy/length*17,shapePaint);
        shapePaint.setColor(rpm && point[1]>440 && point[1]<530 && point[0]>1770?DARK:0xFFE1FFFC);shapePaint.setStrokeWidth(3);
        c.drawLine(point[0],point[1],point[0]+dx/length*17,point[1]+dy/length*17,shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);
    }

    private void drawLiquid(Canvas c,Path cavity,float x,float y,float w,float h,float fraction,float seconds,float amplitude,Shader gradient) {
        if(!Float.isFinite(fraction)||fraction<=0)return;
        int saved=c.save();c.clipPath(cavity);
        float surface=y+h-h*clamp(fraction,0,1);
        shape.reset();shape.moveTo(x-15,y+h+20);shape.lineTo(x-15,surface);
        for(float xx=x-15;xx<=x+w+15;xx+=2)shape.lineTo(xx,surface+wave(xx,seconds,amplitude));
        shape.lineTo(x+w+15,y+h+20);shape.close();
        shapePaint.setShader(gradient);
        shapePaint.setStyle(Paint.Style.FILL);c.drawPath(shape,shapePaint);shapePaint.setShader(null);
        shape.reset();
        for(float xx=x-15;xx<=x+w+15;xx+=2){float yy=surface+wave(xx,seconds,amplitude);if(xx==x-15)shape.moveTo(xx,yy);else shape.lineTo(xx,yy);}
        shapePaint.setColor(0xFFD7FFF7);shapePaint.setStyle(Paint.Style.STROKE);shapePaint.setStrokeWidth(1.6f);c.drawPath(shape,shapePaint);
        shapePaint.setStrokeWidth(.8f);shapePaint.setColor(0x72D5FFF8);
        for(int i=0;i<4;i++){float yy=y+h-(seconds*(7+i)+i*29)%(h*clamp(fraction,0,1)+1);float xx=x+w*(.3f+.11f*i);c.drawCircle(xx,yy,1.4f+(i%2),shapePaint);}
        shapePaint.setStyle(Paint.Style.FILL);c.restoreToCount(saved);
    }

    private void drawBattery(Canvas c,float seconds) {
        if(!Float.isFinite(cellVoltage))return;
        float head = (seconds % 3f) / 3f * 7f - 1f;
        for(int i=0;i<5;i++) {
            float x=(1682+i*31.5f)*.9375f,y=570,w=27*.9375f,h=27;
            // Five fixed glass cells, one travelling pulse. The cells are
            // decorative: only the numeric voltage represents a measurement.
            shapePaint.setStyle(Paint.Style.FILL);
            shapePaint.setColor(WHITE);
            shapePaint.setShader(cellVoltage < 12 ? null : batteryGradient);
            if (cellVoltage < 12) shapePaint.setColor(0xFF846126);
            c.drawRoundRect(x,y,x+w,y+h,4,4,shapePaint);
            shapePaint.setShader(null);
            float strength = Math.max(0, 1 - Math.abs(head - (i + .5f)) / 1.1f);
            strength = strength * strength * (3 - 2 * strength);
            shapePaint.setColor(Color.argb(Math.round(215 * strength),
                    cellVoltage < 12 ? 255 : 190, cellVoltage < 12 ? 218 : 255,
                    cellVoltage < 12 ? 112 : 244));
            c.drawRoundRect(x+1,y+1,x+w-1,y+h-1,3,3,shapePaint);
            shapePaint.setStyle(Paint.Style.STROKE);
            shapePaint.setStrokeWidth(1);
            shapePaint.setColor(0xA0C8FFF6);
            c.drawRoundRect(x,y,x+w,y+h,4,4,shapePaint);
        }
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(WHITE);
    }

    private static float wave(float x,float time,float amplitude) { return (float)(Math.sin(x*.096-time*2.5)+.35*Math.sin(x*.15+time*1.7))*amplitude; }
    private static Shader liquidGradient(float x,float w) { return new LinearGradient(x,0,x+w,0,new int[]{0x8026B7BE,0xC052EEE3,0xA010ABB8,0xC0C1FFF2},new float[]{0,.3f,.7f,1},Shader.TileMode.CLAMP); }

    private void updateLiquids(long now) {
        float dt=lastLiquidFrameMs==0?1:clamp((now-lastLiquidFrameMs)/1000f,0,.1f);lastLiquidFrameMs=now;
        liquidFuel=approach(liquidFuel,state!=null && validNonnegative(state.fuelLiters)?state.fuelLiters:Float.NaN,dt,.6f);
        liquidCoolant=approach(liquidCoolant,state!=null && validCoolant(state.coolantC)?state.coolantC:Float.NaN,dt,.9f);
        cellVoltage=approach(cellVoltage,state!=null && validNonnegative(state.voltage)?state.voltage:Float.NaN,dt,.4f);
    }

    private static float approach(float current,float target,float dt,float tau) {
        if(!Float.isFinite(target))return Float.NaN;
        return !Float.isFinite(current)?target:current+(target-current)*(1-(float)Math.exp(-dt/tau));
    }

    float fuelFraction() { return liquidFuel/(state==null?FuelTankCapacity.DEFAULT_LITERS:state.fuelTankCapacityLiters); }
    static float leftX(float x) { return x*.9f; }
    static float leftY(float y) {
        for(int i=0;i<LEFT_Y.length-1;i++)if(y<=LEFT_Y[i+1][0]){
            float h=LEFT_Y[i+1][0]-LEFT_Y[i][0],t=(y-LEFT_Y[i][0])/h,t2=t*t,t3=t2*t;
            return (2*t3-3*t2+1)*LEFT_Y[i][1]+(t3-2*t2+t)*h*yTangent(i)+(-2*t3+3*t2)*LEFT_Y[i+1][1]+(t3-t2)*h*yTangent(i+1);
        }
        return y;
    }
    private static float ySlope(int i){return (LEFT_Y[i+1][1]-LEFT_Y[i][1])/(LEFT_Y[i+1][0]-LEFT_Y[i][0]);}
    private static float yTangent(int i){if(i==0)return ySlope(0);if(i==LEFT_Y.length-1)return ySlope(i-1);float a=ySlope(i-1),b=ySlope(i);return 2*a*b/(a+b);}

    static void gaugePoint(boolean rpm,float value,float[] result) {
        float[][] table=rpm?RPM_PATH:SPEED_PATH;
        float v=clamp(value,0,table[table.length-1][0]);int i=0;
        while(i<table.length-2&&v>table[i+1][0])i++;
        float[] p0=table[Math.max(0,i-1)],p1=table[i],p2=table[i+1],p3=table[Math.min(table.length-1,i+2)];
        float u=(v-p1[0])/(p2[0]-p1[0]),u2=u*u,u3=u2*u;
        for(int k=1;k<=2;k++)result[k-1]=.5f*(2*p1[k]+(-p0[k]+p2[k])*u+(2*p0[k]-5*p1[k]+4*p2[k]-p3[k])*u2+(-p0[k]+3*p1[k]-3*p2[k]+p3[k])*u3);
        if(!rpm){result[0]=leftX(result[0]);result[1]=leftY(result[1]);float dx=340-result[0],dy=365-result[1],length=(float)Math.hypot(dx,dy);result[0]+=dx/length*7;result[1]+=dy/length*7;}
    }

    private void label(Canvas c,String value,float x,float y,float size,float width) { text(c,recordBounds?"label:"+value+":"+x+":"+y:null,value,x,y,size,width,MUTED,caption); }
    private void value(Canvas c,String source,float x,float y,float size,float width,int color) { text(c,source,values.get(source),x,y,size,width,color,numeric); }
    private void text(Canvas c,String key,String value,float x,float baseline,float size,float maxWidth,int color,Typeface face) {
        if(value==null||value.isEmpty())return;
        textPaint.setShader(null);textPaint.setTypeface(face);textPaint.setTextAlign(Paint.Align.CENTER);textPaint.setStyle(Paint.Style.FILL);textPaint.setColor(color);textPaint.setTextSize(size);
        float width=textPaint.measureText(value);
        if(width>maxWidth)textPaint.setTextSize(size*maxWidth/width);
        if(recordBounds){inkPath.reset();textPaint.getTextPath(value,0,value.length(),x,baseline,inkPath);inkPath.computeBounds(ink,true);bounds.put(key,new RectF(ink));}
        c.drawText(value,x,baseline,textPaint);
    }

    private void updateReadouts() {
        indicatedSpeed=state!=null && state.speedKph>=0?Math.round(speedMode.indicatedKph(state.speedKph)):Float.NaN;
        values.put("speedKph",Float.isFinite(indicatedSpeed)?Integer.toString(Math.round(indicatedSpeed)):DASH);
        values.put("rpm",rpmDigits<0?DASH:Integer.toString(rpmDigits));
        values.put("currentGear",state==null?"":currentGearLabel(state.gearSelector,state.currentGear));
        values.put("fuelLiters",state!=null && validNonnegative(state.fuelLiters)?whole(state.fuelLiters):DASH);
        values.put("rangeKm",state!=null && state.rangeKm>=0?Integer.toString(state.rangeKm):DASH);
        values.put("coolantC",state!=null && validCoolant(state.coolantC)?Integer.toString(state.coolantC):DASH);
        values.put("transmissionTemperatureC",formatAtf(state,monotonicClock.getAsLong()));
        values.put("outsideTemperatureC",state!=null && Float.isFinite(state.outsideTemperatureC)?String.format(Locale.US,"%+.1f",state.outsideTemperatureC):DASH);
        values.put("engineFlywheelTorque",state!=null && Float.isFinite(state.engineFlywheelTorque)?whole(state.engineFlywheelTorque):DASH);
        values.put("instantFuelConsumption",state!=null && state.speedKph>=0?decimal(state.instantFuelConsumption,1):DASH);
        values.put("instantUnit",state==null?DASH:instantUnit(state.speedKph));
        values.put("consumptionLitersPer100Km",state==null?DASH:decimal(state.consumptionLitersPer100Km,1));
        values.put("voltage",state==null?DASH:decimal(state.voltage,1));
        values.put("odometerKm",state!=null && Double.isFinite(state.odometerKm) && state.odometerKm>=0?String.format(Locale.US,"%.0f",state.odometerKm):DASH);
        values.put("dayKm",state==null?DASH:decimal(state.dayKm,1));
        values.put("tripKm",state==null?DASH:decimal(state.tripKm,1));
        values.put("steeringAngleDeg",state!=null && Float.isFinite(state.steeringAngleDeg)?String.format(Locale.US,"%+d°",Math.round(state.steeringAngleDeg)):DASH);
        float[] pressure=state==null?null:new float[]{state.tyreFrontLeftBar,state.tyreFrontRightBar,state.tyreRearLeftBar,state.tyreRearRightBar};
        float[] wheel=state==null?null:new float[]{state.wheelFrontLeftKph,state.wheelFrontRightKph,state.wheelRearLeftKph,state.wheelRearRightKph};
        for(int i=0;i<4;i++){values.put(PRESSURES[i],pressure==null?DASH:decimal(pressure[i],2));values.put(WHEELS[i],wheel==null?DASH:decimal(wheel[i],1));}
    }

    private void updateRpm(long now) {
        if(state==null||state.rpm<0){if(rpmInitialized)rpmReadout=new RpmReadoutStabilizer();rpmInitialized=false;displayedRpm=Float.NaN;rpmDigits=-1;values.put("rpm",DASH);return;}
        float target=state.rpm;
        if(!rpmInitialized||now<lastRpmFrameMs){displayedRpm=target;rpmInitialized=true;}
        else {long dt=Math.min(100,now-lastRpmFrameMs);float tau=target<displayedRpm-350?100:140;displayedRpm+=(target-displayedRpm)*(1-(float)Math.exp(-dt/tau));if(Math.abs(target-displayedRpm)<1)displayedRpm=target;}
        lastRpmFrameMs=now;rpmDigits=rpmReadout.update(displayedRpm,now);values.put("rpm",Integer.toString(rpmDigits));
    }

    String displayedValue(String source) {
        if("transmissionTemperatureC".equals(source))return formatAtf(state,monotonicClock.getAsLong());
        if(source.startsWith("DashboardClock.")){clock.update(wallClock.getAsLong());return source.endsWith("time")?clock.getTimeText():clock.getDateText();}
        return values.get(source);
    }
    Map<String,RectF> textBounds() { return new LinkedHashMap<>(bounds); }
    void recordTextBounds() { recordBounds=true; }
    float indicatedSpeedKph() { return indicatedSpeed; }
    float displayedRpmValue() { return displayedRpm; }
    int rpmDigitValue() { return rpmDigits; }
    static String currentGearLabel(String selector,int ratio) { return GearSelector.DRIVE.equals(selector)?GearSelector.ratio(ratio):""; }
    static String instantUnit(int measuredSpeed) { return measuredSpeed<0?DASH:measuredSpeed<=1?"л/ч":"л/100 км"; }
    static boolean isAtfFresh(ClusterState s,long now) { return s!=null && Float.isFinite(s.transmissionTemperatureC) && s.transmissionTemperatureUpdatedAtMs>0 && s.transmissionTemperatureUpdatedAtMs<=now && now-s.transmissionTemperatureUpdatedAtMs<=ATF_STALE_MS; }
    private static String formatAtf(ClusterState s,long now) { return isAtfFresh(s,now)?whole(s.transmissionTemperatureC):DASH; }
    private static String whole(float v) { return String.format(Locale.US,"%.0f",v); }
    private static String decimal(float v,int places) { return validNonnegative(v)?String.format(Locale.US,"%."+places+"f",v):DASH; }
    private static boolean validNonnegative(float v) { return Float.isFinite(v)&&v>=0; }
    private static boolean validCoolant(int v) { return v>=-40&&v<=150; }
    private static float clamp(float v,float min,float max) { return Math.max(min,Math.min(max,v)); }
    private static int atfColor(TransmissionTemperatureAlert.Level level) {
        switch(level){case ELEVATED:return WARNING;case HOT:return 0xFFFF8A3D;case CRITICAL:return RED;default:return WHITE;}
    }
    private static Bitmap loadBitmap(Context context,String filename) {
        try(InputStream stream=context.getAssets().open(ASSETS+filename)) {
            Bitmap b=BitmapFactory.decodeStream(stream);
            if(b==null||b.getWidth()!=1920||b.getHeight()!=720)throw new IllegalStateException("Invalid Porcelain Fluid asset: "+filename);
            b.setDensity(Bitmap.DENSITY_NONE);return b;
        }catch(IOException e){throw new IllegalStateException("Cannot load Porcelain Fluid asset: "+filename,e);}
    }
}
