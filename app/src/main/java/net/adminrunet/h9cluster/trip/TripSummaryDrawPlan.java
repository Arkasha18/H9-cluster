package net.adminrunet.h9cluster.trip;

/** Pure reference-to-screen geometry for the trip summary. */
public final class TripSummaryDrawPlan {
    private static final float REFERENCE_WIDTH = 1_920.0f;
    private static final float REFERENCE_HEIGHT = 720.0f;

    public final Box panel;
    public final Box title;
    public final Box[] metricRows;
    public final float innerDividerLeft;
    public final float innerDividerRight;
    public final float scaleX;
    public final float scaleY;

    private TripSummaryDrawPlan(
            Box panel,
            Box title,
            Box[] metricRows,
            float innerDividerLeft,
            float innerDividerRight,
            float scaleX,
            float scaleY) {
        this.panel = panel;
        this.title = title;
        this.metricRows = metricRows;
        this.innerDividerLeft = innerDividerLeft;
        this.innerDividerRight = innerDividerRight;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    public static TripSummaryDrawPlan forSize(int width, int height) {
        float scaleX = width / REFERENCE_WIDTH;
        float scaleY = height / REFERENCE_HEIGHT;
        return new TripSummaryDrawPlan(
                box(653.0f, 101.0f, 1_267.0f, 691.0f, scaleX, scaleY),
                box(653.0f, 101.0f, 1_267.0f, 207.0f, scaleX, scaleY),
                new Box[] {
                    box(653.0f, 207.0f, 1_267.0f, 328.0f, scaleX, scaleY),
                    box(653.0f, 328.0f, 1_267.0f, 449.0f, scaleX, scaleY),
                    box(653.0f, 449.0f, 1_267.0f, 570.0f, scaleX, scaleY),
                    box(653.0f, 570.0f, 1_267.0f, 691.0f, scaleX, scaleY)
                },
                739.0f * scaleX,
                1_181.0f * scaleX,
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

        public float centerX() {
            return (left + right) * 0.5f;
        }
    }
}
