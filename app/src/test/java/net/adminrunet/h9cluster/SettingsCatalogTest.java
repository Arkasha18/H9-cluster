package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;

import java.util.EnumSet;

import org.junit.Test;

public final class SettingsCatalogTest {
    @Test
    public void catalogContainsEveryBlockExactlyOnce() {
        SettingsCatalog.Option[] options = SettingsCatalog.options();
        EnumSet<BlockVisibility.Block> blocks =
                EnumSet.noneOf(BlockVisibility.Block.class);

        for (SettingsCatalog.Option option : options) {
            blocks.add(option.block);
        }

        assertEquals(15, options.length);
        assertEquals(15, blocks.size());
        assertEquals(EnumSet.allOf(BlockVisibility.Block.class), blocks);
    }

    @Test
    public void catalogUsesApprovedGroupSizes() {
        int main = 0;
        int top = 0;
        int bottom = 0;

        for (SettingsCatalog.Option option : SettingsCatalog.options()) {
            if (option.group == SettingsCatalog.Group.MAIN) {
                main++;
            } else if (option.group == SettingsCatalog.Group.TOP) {
                top++;
            } else if (option.group == SettingsCatalog.Group.BOTTOM) {
                bottom++;
            }
        }

        assertEquals(4, main);
        assertEquals(7, top);
        assertEquals(4, bottom);
    }
}
