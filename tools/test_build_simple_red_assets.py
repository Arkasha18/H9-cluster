#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

import build_simple_red_assets as assets


ROOT = Path(__file__).resolve().parents[1]
SPORT_BACKGROUND = (
    ROOT / "app/src/main/assets/dashboard/skins/sport/background.png"
)
MAP_MASK = (
    ROOT
    / "docs/H9_Cluster_Neutral_Design_Template_1920x720"
    / "02_yandex_map_transparency_mask.png"
)
SYSTEM_MASK = ROOT / "tools/assets/h9_system_icon_forbidden_mask_1920x720.png"


class SimpleRedAssetsTest(unittest.TestCase):
    def render_background(self, source=None):
        checked_source = source or Image.open(
            SPORT_BACKGROUND
        ).convert("RGBA")
        map_mask = Image.open(MAP_MASK).convert("L")
        return assets.build_background(checked_source, map_mask)

    def test_scale_points_follow_matching_upper_semicircles(self):
        left_start = assets.scale_point(0.0, False)
        left_top = assets.scale_point(0.5, False)
        left_end = assets.scale_point(1.0, False)
        right_start = assets.scale_point(0.0, True)
        right_end = assets.scale_point(1.0, True)

        self.assertAlmostEqual(
            assets.LEFT_GAUGE_CENTER_X - assets.GAUGE_RADIUS,
            left_start[0],
        )
        self.assertAlmostEqual(
            assets.GAUGE_CENTER_Y - assets.GAUGE_RADIUS,
            left_top[1],
        )
        self.assertAlmostEqual(
            assets.LEFT_GAUGE_CENTER_X + assets.GAUGE_RADIUS,
            left_end[0],
        )
        self.assertAlmostEqual(
            assets.RIGHT_GAUGE_CENTER_X - assets.GAUGE_RADIUS,
            right_start[0],
        )
        self.assertAlmostEqual(
            assets.RIGHT_GAUGE_CENTER_X + assets.GAUGE_RADIUS,
            right_end[0],
        )

    def test_labels_are_monotonic_and_complete(self):
        self.assertEqual(
            (0, 20, 40, 60, 80, 100, 120, 140, 160, 180, 200),
            assets.SPEED_LABELS,
        )
        self.assertEqual(tuple(range(7)), assets.TACH_LABELS)

    def test_background_draws_both_arc_endpoints_and_tops(self):
        alpha = np.asarray(self.render_background())[:, :, 3]
        for right_gauge in (False, True):
            for fraction in (0.0, 0.5, 1.0):
                x, y, _, _ = assets.scale_point(
                    fraction,
                    right_gauge,
                )
                left = max(0, round(x) - 8)
                top = max(0, round(y) - 8)
                right = min(assets.SIZE[0], round(x) + 9)
                bottom = min(assets.SIZE[1], round(y) + 9)
                self.assertGreater(
                    np.count_nonzero(alpha[top:bottom, left:right]),
                    0,
                )

    def test_gauge_centers_and_lower_factory_zones_remain_clear(self):
        alpha = np.asarray(self.render_background())[:, :, 3]

        self.assertEqual(
            0,
            alpha[
                round(assets.GAUGE_CENTER_Y),
                round(assets.LEFT_GAUGE_CENTER_X),
            ],
        )
        self.assertEqual(
            0,
            alpha[
                round(assets.GAUGE_CENTER_Y),
                round(assets.RIGHT_GAUGE_CENTER_X),
            ],
        )
        self.assertEqual(
            0,
            np.count_nonzero(alpha[630:720, 250:690]),
        )
        self.assertEqual(
            0,
            np.count_nonzero(alpha[670:720, 1230:1670]),
        )

    def test_notification_mask_covers_only_outer_corners(self):
        result = np.asarray(self.render_background())

        self.assertEqual(
            (
                assets.RIGHT_GAUGE_CENTER_X + 30,
                assets.GAUGE_CENTER_Y - 95,
            ),
            assets.NOTIFICATION_APERTURE_CENTER,
        )
        self.assertEqual(
            assets.GAUGE_RADIUS - 80,
            assets.NOTIFICATION_APERTURE_RADIUS,
        )
        self.assertGreater(result[580, 1450, 3], 240)
        center_x, center_y = assets.NOTIFICATION_APERTURE_CENTER
        self.assertEqual(0, result[center_y, center_x, 3])

        mask = Image.new(
            "RGBA",
            (
                assets.SIZE[0] * assets.RENDER_SCALE,
                assets.SIZE[1] * assets.RENDER_SCALE,
            ),
            (0, 0, 0, 0),
        )
        assets._draw_notification_corner_mask(mask)
        mask = np.asarray(mask.resize(assets.SIZE))
        feather_alpha = mask[440, 1405, 3]
        self.assertGreater(feather_alpha, 0)
        self.assertLess(feather_alpha, 255)

    def test_background_does_not_reuse_factory_sport_pixels(self):
        opaque_source = Image.new("RGBA", assets.SIZE, (255, 0, 0, 255))
        transparent_source = Image.new("RGBA", assets.SIZE, (0, 0, 0, 0))

        self.assertTrue(
            np.array_equal(
                np.asarray(self.render_background(opaque_source)),
                np.asarray(self.render_background(transparent_source)),
            )
        )

    def test_target_factory_indicator_zones_are_clear(self):
        system_mask = np.asarray(Image.open(SYSTEM_MASK).convert("L")) > 0
        alpha = np.asarray(self.render_background())[:, :, 3] > 0

        for left, top, right, bottom in (
            (577, 20, 693, 93),
            (916, 17, 1001, 75),
            (1196, 23, 1353, 96),
            (1418, 672, 1536, 714),
        ):
            self.assertEqual(
                0,
                np.count_nonzero(
                    alpha[top:bottom, left:right]
                    & system_mask[top:bottom, left:right]
                ),
            )

    def test_write_assets_creates_skin_owned_files(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)

            assets.write_assets(ROOT, output)

            self.assertEqual(
                {"background.png"},
                {path.name for path in output.iterdir()},
            )


if __name__ == "__main__":
    unittest.main()
