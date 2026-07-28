package net.adminrunet.h9cluster.trip;

/** Pure reference-to-screen geometry for the trip summary. */
public final class TripSummaryDrawPlan {
    private static final float REFERENCE_WIDTH = 1_920.0f;
    private static final float REFERENCE_HEIGHT = 720.0f;

    public final Box leftPanel;
    public final Box rightPanel;
    public final Box leftCritical;
    public final Box rightCritical;
    public final float scaleX;
    public final float scaleY;

    private TripSummaryDrawPlan(
            Box leftPanel,
            Box rightPanel,
            Box leftCritical,
            Box rightCritical,
            float scaleX,
            float scaleY) {
        this.leftPanel = leftPanel;
        this.rightPanel = rightPanel;
        this.leftCritical = leftCritical;
        this.rightCritical = rightCritical;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    public static TripSummaryDrawPlan forSize(int width, int height) {
        float scaleX = width / REFERENCE_WIDTH;
        float scaleY = height / REFERENCE_HEIGHT;
        return new TripSummaryDrawPlan(
                box(96.0f, 420.0f, 600.0f, 650.0f, scaleX, scaleY),
                box(1_320.0f, 420.0f, 1_880.0f, 650.0f, scaleX, scaleY),
                box(330.0f, 440.0f, 570.0f, 625.0f, scaleX, scaleY),
                box(1_350.0f, 440.0f, 1_590.0f, 625.0f, scaleX, scaleY),
                scaleX,
                scaleY);
    }

    private static Box box(
            float left,
            float top,
            float right,
            float bottom,
            float scaleX,
            float scaleY) {
        return new Box(
                left * scaleX,
                top * scaleY,
                right * scaleX,
                bottom * scaleY);
    }

    public static final class Box {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        Box(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
