package net.adminrunet.h9cluster;

/** Immutable renderer decision for one Classic Custom visibility snapshot. */
final class ClassicCustomDrawPlan {
    private final BlockVisibility visibility;

    private ClassicCustomDrawPlan(BlockVisibility visibility) {
        this.visibility = visibility;
    }

    static ClassicCustomDrawPlan forVisibility(BlockVisibility visibility) {
        return new ClassicCustomDrawPlan(
                visibility == null
                        ? BlockVisibility.allVisible()
                        : visibility);
    }

    boolean shouldDraw(BlockVisibility.Block block) {
        return visibility.isVisible(block);
    }

    boolean hasAnyBlock() {
        return visibility.hasVisibleBlocks();
    }

    int visibleBlockCount() {
        int count = 0;
        for (BlockVisibility.Block block : BlockVisibility.Block.values()) {
            if (shouldDraw(block)) {
                count++;
            }
        }
        return count;
    }
}
