package net.adminrunet.h9cluster.trip;

/** Tracks which application layers remain visible around an engine stop. */
public final class TripSummaryLayerState {
    private boolean rendererVisible = true;
    private boolean summaryVisible;

    public TripSummaryLayerState() {
        this(false);
    }

    public TripSummaryLayerState(boolean rendererSuppressed) {
        rendererVisible = !rendererSuppressed;
    }

    public void onSummaryShown() {
        rendererVisible = false;
        summaryVisible = true;
    }

    public void onSummaryDismissed() {
        summaryVisible = false;
    }

    /**
     * An explicit request from the settings screen outranks the hiding that
     * follows an engine stop: the driver asked to look at a skin right now.
     */
    public void onUserRequestedRenderer() {
        rendererVisible = true;
        summaryVisible = false;
    }

    public void onEngineStarted() {
        rendererVisible = true;
        summaryVisible = false;
    }

    public boolean isRendererVisible() {
        return rendererVisible;
    }

    public boolean isSummaryVisible() {
        return summaryVisible;
    }
}
