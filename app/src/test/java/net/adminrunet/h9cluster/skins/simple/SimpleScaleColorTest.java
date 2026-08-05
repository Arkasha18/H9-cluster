package net.adminrunet.h9cluster.skins.simple;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public final class SimpleScaleColorTest {
    @Test
    public void theEightChoicesArriveInTheOrderTheyAreOffered() {
        String[] expected = {
            "red",
            "white",
            "moon",
            "yellow",
            "light_green",
            "green",
            "cyan",
            "blue"
        };
        assertEquals(expected.length, SimpleScaleColor.values().length);
        for (int index = 0; index < expected.length; index++) {
            assertEquals(
                    expected[index],
                    SimpleScaleColor.values()[index].id);
        }
    }

    @Test
    public void everyChoiceIsDistinctAndNamed() {
        Set<String> ids = new HashSet<>();
        Set<Integer> accents = new HashSet<>();
        for (SimpleScaleColor color : SimpleScaleColor.values()) {
            assertTrue("duplicate id " + color.id, ids.add(color.id));
            assertTrue(
                    "duplicate accent for " + color.id,
                    accents.add(color.accent));
            assertNotNull(color.title);
            assertTrue(
                    "a choice needs a name to pick it by",
                    color.title.length() > 0);
            assertEquals(
                    "accents are opaque",
                    0xFF,
                    color.accent >>> 24);
        }
    }

    @Test
    public void redIsTheDefaultAndKeepsTheSkinLookingAsItDid() {
        assertSame(
                SimpleScaleColor.RED,
                SimpleScaleColor.defaultColor());
        assertEquals("red", SimpleScaleColor.defaultColor().id);
        // The skin ships red, so the default must reproduce the colours
        // the renderer used before the setting existed.
        assertEquals(0xFFFF1C1C, SimpleScaleColor.RED.accent);
        assertEquals(0xFFFFD54F, SimpleScaleColor.RED.leading);
    }

    @Test
    public void derivedColoursOnlyChangeTheAlpha() {
        // Glow, band body and band start are the accent at different
        // transparencies. Keeping them derived is what stops them from
        // drifting apart from the accent when the palette is retuned.
        for (SimpleScaleColor color : SimpleScaleColor.values()) {
            int rgb = color.accent & 0x00FFFFFF;
            assertEquals(0xDC, color.glow() >>> 24);
            assertEquals(0xAA, color.bandBody() >>> 24);
            assertEquals(0x08, color.bandStart() >>> 24);
            assertEquals(rgb, color.glow() & 0x00FFFFFF);
            assertEquals(rgb, color.bandBody() & 0x00FFFFFF);
            assertEquals(rgb, color.bandStart() & 0x00FFFFFF);
        }
    }

    @Test
    public void theLeadingEdgeCarriesTheBloomAndStaysOpaque() {
        for (SimpleScaleColor color : SimpleScaleColor.values()) {
            assertEquals(
                    "the bloom is the leading edge spread out",
                    color.leading,
                    color.bloom());
            assertEquals(
                    "the leading edge is opaque",
                    0xFF,
                    color.leading >>> 24);
        }
    }

    @Test
    public void everyLeadingEdgeReadsBrighterThanItsAccent() {
        // The end of the band is marked by lightness rather than by a
        // borrowed hue, so it has to actually be lighter.
        for (SimpleScaleColor color : SimpleScaleColor.values()) {
            assertTrue(
                    "leading edge must be lighter for " + color.id,
                    luminance(color.leading) > luminance(color.accent));
        }
    }

    @Test
    public void unknownIdsFallBackToRedRatherThanFailing() {
        // Settings arrive from storage and from preview intents, so a
        // stale or hand-edited value must not take the skin down.
        assertSame(SimpleScaleColor.RED, SimpleScaleColor.byId(null));
        assertSame(SimpleScaleColor.RED, SimpleScaleColor.byId(""));
        assertSame(
                SimpleScaleColor.RED,
                SimpleScaleColor.byId("chartreuse"));
        assertSame(
                SimpleScaleColor.RED,
                SimpleScaleColor.byId("RED"));
    }

    @Test
    public void knownIdsRoundTrip() {
        for (SimpleScaleColor color : SimpleScaleColor.values()) {
            assertSame(color, SimpleScaleColor.byId(color.id));
        }
    }

    private static int luminance(int color) {
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return 299 * red + 587 * green + 114 * blue;
    }
}
