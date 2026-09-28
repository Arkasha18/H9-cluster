package net.adminrunet.h9cluster.skins;

/**
 * How the renderers translate the measured bus speed into the indicated one.
 *
 * <p>The bus reports a GPS-derived speed while factory speedometers read the
 * wheels and deliberately over-read a little. The selected mode is global:
 * every skin applies it to its pointer, tape and digital readout together.</p>
 */
public enum SpeedDisplayMode {
    REAL("real") {
        @Override
        public float indicatedKph(float measuredKph) {
            return measuredKph;
        }
    },
    FACTORY_PLUS_4_5_PERCENT("factory_plus_4_5_percent") {
        @Override
        public float indicatedKph(float measuredKph) {
            return measuredKph * FACTORY_INDICATION_FACTOR;
        }
    },
    FACTORY_PLUS_5_KPH("factory_plus_5_kph") {
        @Override
        public float indicatedKph(float measuredKph) {
            float finiteSpeed = Float.isFinite(measuredKph)
                    ? Math.max(0.0f, measuredKph)
                    : 0.0f;
            // The offset ramps in over the first 20 km/h so the zero stays
            // pinned at the printed scale origin.
            float ramp = Math.min(finiteSpeed / OFFSET_RAMP_KPH, 1.0f);
            return finiteSpeed + FACTORY_OFFSET_KPH * ramp;
        }
    };

    private static final float FACTORY_INDICATION_FACTOR = 1.045f;
    private static final float FACTORY_OFFSET_KPH = 5.0f;
    private static final float OFFSET_RAMP_KPH = 20.0f;

    private final String key;

    SpeedDisplayMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** The speed the pointer, tape and digital readout show together. */
    public abstract float indicatedKph(float measuredKph);

    public static SpeedDisplayMode fromKey(String key) {
        if (key != null) {
            for (SpeedDisplayMode mode : values()) {
                if (mode.key.equals(key)) {
                    return mode;
                }
            }
        }
        return REAL;
    }
}
