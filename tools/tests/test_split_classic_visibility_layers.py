from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

from tools import split_classic_visibility_layers as splitter


ROOT = Path(__file__).resolve().parents[2]
BACKGROUND = ROOT / "app/src/main/assets/dashboard/background_classic.png"
OVERLAY = ROOT / "app/src/main/assets/dashboard/background_classic_overlay.png"


class SplitClassicVisibilityLayersTest(unittest.TestCase):
    def generate(self, output_dir: Path) -> dict[str, Image.Image]:
        splitter.generate_layers(BACKGROUND, OVERLAY, output_dir)
        return {
            name: Image.open(output_dir / f"{name}.png").convert("RGBA")
            for name in splitter.LAYER_NAMES
        }

    def test_every_layer_is_full_size_rgba(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            layers = self.generate(Path(directory))

            self.assertEqual(set(splitter.LAYER_NAMES), set(layers))
            for name, layer in layers.items():
                self.assertEqual((1920, 720), layer.size, name)
                self.assertEqual("RGBA", layer.mode, name)

    def test_layers_recompose_to_approved_static_composite(self) -> None:
        source = Image.alpha_composite(
            Image.open(BACKGROUND).convert("RGBA"),
            Image.open(OVERLAY).convert("RGBA"),
        )
        with tempfile.TemporaryDirectory() as directory:
            layers = self.generate(Path(directory))
            recomposed = splitter.compose_layers(layers)

        source_pixels = np.asarray(source)
        recomposed_pixels = np.asarray(recomposed)
        visible = source_pixels[..., 3] > 0
        self.assertTrue(np.array_equal(
            source_pixels[..., 3],
            recomposed_pixels[..., 3],
        ))
        self.assertTrue(
            np.array_equal(
                source_pixels[..., :3][visible],
                recomposed_pixels[..., :3][visible],
            ),
            "all enabled layers must reproduce every visible Classic pixel",
        )

    def test_each_visible_source_pixel_belongs_to_one_layer(self) -> None:
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

    def test_semantic_accessories_have_separate_pixels(self) -> None:
        source = Image.alpha_composite(
            Image.open(BACKGROUND).convert("RGBA"),
            Image.open(OVERLAY).convert("RGBA"),
        )
        with tempfile.TemporaryDirectory() as directory:
            layers = self.generate(Path(directory))

        fuel_alpha = np.asarray(layers["fuel_and_range"])[..., 3]
        coolant_alpha = np.asarray(layers["engine_temperature"])[..., 3]
        tyre_alpha = np.asarray(layers["tyre_pressure"])[..., 3]
        self.assertGreater(np.count_nonzero(fuel_alpha[120:480, 490:765]), 0)
        self.assertGreater(np.count_nonzero(coolant_alpha[120:480, 1155:1430]), 0)

        source_car = np.asarray(source)[512:581, 1380:1417, 3]
        tyre_car = tyre_alpha[512:581, 1380:1417]
        coolant_car = coolant_alpha[512:581, 1380:1417]
        self.assertEqual(
            np.count_nonzero(source_car),
            np.count_nonzero(tyre_car),
            "the tyre layer must own the complete baked vehicle region",
        )
        self.assertEqual(0, np.count_nonzero(coolant_car))


if __name__ == "__main__":
    unittest.main()
