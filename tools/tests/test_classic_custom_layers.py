from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

from tools import build_classic_custom_layers as builder


ROOT = Path(__file__).resolve().parents[2]
BACKGROUND = ROOT / "app/src/main/assets/dashboard/background_classic.png"
OVERLAY = ROOT / "app/src/main/assets/dashboard/background_classic_overlay.png"


class ClassicCustomLayersTest(unittest.TestCase):
    def generate(self, output_dir: Path) -> dict[str, Image.Image]:
        builder.generate_layers(BACKGROUND, OVERLAY, output_dir)
        return {
            name: Image.open(output_dir / f"{name}.png").convert("RGBA")
            for name in builder.RASTER_LAYER_NAMES
        }

    def test_every_generated_layer_is_nonempty_full_size_rgba(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            layers = self.generate(Path(directory))

        self.assertEqual(
            {
                "speedometer",
                "tachometer",
                "fuel_and_range",
                "engine_temperature",
                "tyre_pressure",
            },
            set(layers),
        )
        for name, layer in layers.items():
            self.assertEqual((1920, 720), layer.size, name)
            self.assertEqual("RGBA", layer.mode, name)
            self.assertGreater(
                np.count_nonzero(np.asarray(layer)[..., 3]),
                0,
                name,
            )

    def test_every_source_pixel_has_exactly_one_semantic_owner(self) -> None:
        source = Image.alpha_composite(
            Image.open(BACKGROUND).convert("RGBA"),
            Image.open(OVERLAY).convert("RGBA"),
        )
        with tempfile.TemporaryDirectory() as directory:
            layers = self.generate(Path(directory))

        ownership = np.zeros((720, 1920), dtype=np.uint8)
        for layer in layers.values():
            ownership += (np.asarray(layer)[..., 3] > 0).astype(np.uint8)

        source_visible = np.asarray(source)[..., 3] > 0
        self.assertTrue(np.all(ownership[source_visible] == 1))
        self.assertTrue(np.all(ownership[~source_visible] == 0))
        source_pixels = np.asarray(source)
        recomposed_pixels = np.asarray(builder.compose_layers(layers))
        self.assertTrue(np.array_equal(
            source_pixels[..., 3],
            recomposed_pixels[..., 3],
        ))
        self.assertTrue(np.array_equal(
            source_pixels[..., :3][source_visible],
            recomposed_pixels[..., :3][source_visible],
        ))

    def test_named_regions_are_not_absorbed_by_main_dials(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            layers = self.generate(Path(directory))

        regions = {
            "speedometer": (100, 640, 0, 490),
            "tachometer": (100, 640, 1430, 1920),
            "fuel_and_range": (105, 500, 485, 770),
            "engine_temperature": (105, 500, 1150, 1440),
            "tyre_pressure": (505, 585, 1370, 1430),
        }
        for name, (top, bottom, left, right) in regions.items():
            named_alpha = np.asarray(layers[name])[top:bottom, left:right, 3]
            self.assertGreater(np.count_nonzero(named_alpha), 0, name)

        speed_alpha = np.asarray(layers["speedometer"])[..., 3]
        tach_alpha = np.asarray(layers["tachometer"])[..., 3]
        for name in (
                "fuel_and_range",
                "engine_temperature",
                "tyre_pressure",
        ):
            owned = np.asarray(layers[name])[..., 3] > 0
            self.assertEqual(0, np.count_nonzero(speed_alpha[owned]), name)
            self.assertEqual(0, np.count_nonzero(tach_alpha[owned]), name)

    def test_main_dial_end_labels_belong_to_the_main_dials(self) -> None:
        source = Image.alpha_composite(
            Image.open(BACKGROUND).convert("RGBA"),
            Image.open(OVERLAY).convert("RGBA"),
        )
        source_alpha = np.asarray(source)[..., 3]
        with tempfile.TemporaryDirectory() as directory:
            layers = self.generate(Path(directory))

        speed_label = (slice(198, 273), slice(438, 537))
        tach_label = (slice(198, 273), slice(1384, 1479))
        self.assertEqual(
            np.count_nonzero(source_alpha[speed_label]),
            np.count_nonzero(np.asarray(layers["speedometer"])[..., 3][speed_label]),
        )
        self.assertEqual(
            0,
            np.count_nonzero(np.asarray(layers["fuel_and_range"])[..., 3][speed_label]),
        )
        self.assertEqual(
            np.count_nonzero(source_alpha[tach_label]),
            np.count_nonzero(np.asarray(layers["tachometer"])[..., 3][tach_label]),
        )
        self.assertEqual(
            0,
            np.count_nonzero(
                np.asarray(layers["engine_temperature"])[..., 3][tach_label]
            ),
            )

    def test_main_dials_keep_their_lower_rings_and_panel_edges(self) -> None:
        source = Image.alpha_composite(
            Image.open(BACKGROUND).convert("RGBA"),
            Image.open(OVERLAY).convert("RGBA"),
        )
        source_alpha = np.asarray(source)[..., 3]
        with tempfile.TemporaryDirectory() as directory:
            layers = self.generate(Path(directory))

        speed_alpha = np.asarray(layers["speedometer"])[..., 3]
        tach_alpha = np.asarray(layers["tachometer"])[..., 3]
        left_lower_ring = (slice(630, 720), slice(0, 390))
        right_lower_ring = (slice(630, 720), slice(1530, 1920))

        for region, owner in (
                (left_lower_ring, speed_alpha),
                (right_lower_ring, tach_alpha),
        ):
            self.assertEqual(
                np.count_nonzero(source_alpha[region]),
                np.count_nonzero(owner[region]),
            )

    def test_factory_indicator_and_center_regions_stay_transparent(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            layers = self.generate(Path(directory))

        protected_regions = (
            (0, 100, 578, 696),
            (0, 100, 1196, 1320),
            (140, 720, 770, 1150),
        )
        for name, layer in layers.items():
            alpha = np.asarray(layer)[..., 3]
            for top, bottom, left, right in protected_regions:
                self.assertEqual(
                    0,
                    np.count_nonzero(alpha[top:bottom, left:right]),
                    name,
                )


if __name__ == "__main__":
    unittest.main()
