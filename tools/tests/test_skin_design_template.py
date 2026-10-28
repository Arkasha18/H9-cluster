"""Protect the measured geometry and the separation of base/foreground alpha."""

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_forum_design_template as template


def support(mask):
    return mask.point(lambda alpha: 255 if alpha > 0 else 0)


class SkinDesignTemplateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.system = template.checked_image(template.DEFAULT_SYSTEM_MASK, "RGBA")
        cls.alpha = cls.system.getchannel("A")
        cls.lower_left, cls.factory = template.split_system_fields(cls.system)

    def test_calibration_sources_are_the_measured_files(self):
        for path, digest in (
            (template.DEFAULT_SYSTEM_MASK, "654f12772eecdf9cd5f581387acb964d5f06a15d13b7e36fb1f2b96a498f47a2"),
            (template.DEFAULT_GRADIENT, "c21978f23e6bcbcdbb255c8ee3b0599ee4494310741eeb84deb1298ff0957260"),
        ):
            self.assertEqual(digest, hashlib.sha256(path.read_bytes()).hexdigest())

    def test_guide_font_has_real_cyrillic_glyphs(self):
        font = template.font(22)
        missing = bytes(font.getmask("\u0378"))
        for codepoint in (*range(0x410, 0x450), 0x401, 0x451):
            self.assertNotEqual(missing, bytes(font.getmask(chr(codepoint))))

    def test_partition_preserves_every_fractional_edge_and_never_overlaps(self):
        self.assertEqual(40651, sum(self.lower_left.histogram()[1:]))
        self.assertGreater(sum(self.alpha.histogram()[1:255]), 0)
        self.assertIsNone(ImageChops.multiply(support(self.lower_left), support(self.factory)).getbbox())
        self.assertIsNone(ImageChops.difference(ImageChops.add(self.lower_left, self.factory), self.alpha).getbbox())
        # A rectangular replacement would fill empty corners and lose this shape.
        self.assertLess(sum(self.lower_left.histogram()[1:]), 111 * 69 + 318 * 148)
        self.assertGreater(self.factory.getpixel((1000, 40)), 0)
        self.assertEqual(0, self.lower_left.getpixel((1000, 40)))

    def test_overlay_colours_full_fields_without_inventing_geometry(self):
        overlay = template.create_fields_overlay(self.lower_left, self.factory)
        self.assertIsNone(ImageChops.difference(support(overlay.getchannel("A")), support(self.alpha)).getbbox())
        for mask, colour in ((self.lower_left, template.HARD_COLOUR), (self.factory, template.FACTORY_COLOUR)):
            for y in range(template.HEIGHT):
                for x in range(template.WIDTH):
                    if mask.getpixel((x, y)) > 0:
                        self.assertEqual((*colour, 160), overlay.getpixel((x, y)))
                        break

    def test_approved_titanium_art_preserves_fields_with_one_pixel_clearance(self):
        assets = template.ROOT / "app/src/main/assets/dashboard/skins/sculptedtitanium"
        art = template.checked_image(assets / "chrome.png", "RGBA").getchannel("A")
        protected = support(self.lower_left).filter(ImageFilter.MaxFilter(3))
        self.assertIsNone(ImageChops.multiply(support(art), protected).getbbox())
        self.assertIsNone(art.crop((560, 80, 1360, 720)).getbbox())
        self.assertGreater(sum(ImageChops.multiply(support(art), support(self.factory)).histogram()[1:]), 100000)
        base = template.create_black_map_gradient(template.DEFAULT_GRADIENT)
        self.assertIsNone(base.getchannel("A").crop(template.MAP_CORE).getbbox())
        composed = Image.alpha_composite(base, template.checked_image(assets / "chrome.png", "RGBA"))
        difference = ImageChops.difference(composed.getchannel("A"), base.getchannel("A"))
        self.assertIsNone(ImageChops.multiply(difference, support(self.lower_left)).getbbox())

    def test_generation_is_repeatable_and_keeps_original_sources(self):
        scratch = template.ROOT / ".gradle-ci/theme-template-test"
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            output = Path(directory)
            self.assertTrue(output.resolve().is_relative_to(scratch.resolve()))
            template.build_template(output)
            first = {name: (output / name).read_bytes() for name in template.TEMPLATE_FILES}
            template.build_template(output)
            self.assertEqual(first, {name: (output / name).read_bytes() for name in template.TEMPLATE_FILES})
            self.assertEqual(template.DEFAULT_SYSTEM_MASK.read_bytes(), first["03_system_icons_forbidden_mask.png"])
            self.assertEqual(template.DEFAULT_GRADIENT.read_bytes(), first["04_yandex_map_black_gradient_rgba.png"])
            info = json.loads(first["system_fields.json"])
            self.assertEqual(40651, info["lower_left"]["nonzero_pixels"])
            self.assertEqual([982, 28, 1018, 66], info["automatic_gear_exception"]["bounds"])
            for name in template.TEMPLATE_FILES:
                if name.endswith(".png"):
                    with Image.open(output / name) as image:
                        self.assertEqual((1920, 720), image.size)
                        # Guide glyph rasterization can differ between FreeType
                        # builds. Geometry masks and calibrated layers cannot.
                        if name != "01_technical_guide.png":
                            with Image.open(template.TEMPLATE_DIR / name) as committed:
                                self.assertEqual(image.mode, committed.mode)
                                self.assertEqual(image.tobytes(), committed.tobytes())
                else:
                    self.assertEqual(first[name], (template.TEMPLATE_DIR / name).read_bytes())


if __name__ == "__main__":
    unittest.main()
