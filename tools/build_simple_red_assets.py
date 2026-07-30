#!/usr/bin/env python3

import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


SIZE = (1920, 720)
RENDER_SCALE = 2
LEFT_GAUGE_CENTER_X = 290.0
RIGHT_GAUGE_CENTER_X = 1610.0
GAUGE_CENTER_Y = 435.0
GAUGE_RADIUS = 235.0
SCALE_START_ANGLE = math.radians(-10.0)
SCALE_SWEEP_ANGLE = math.radians(200.0)
SPEED_LABELS = (0, 20, 40, 60, 80, 100, 120, 140, 160, 180, 200)
TACH_LABELS = tuple(range(7))

NOTIFICATION_RECT = (1320, 200, 1890, 620)
NOTIFICATION_APERTURE_CENTER = (1640, 445)
NOTIFICATION_APERTURE_RADIUS_X = 145
NOTIFICATION_APERTURE_RADIUS_Y = 115
NOTIFICATION_EDGE_BLUR = 18.0


def scale_point(
    fraction: float,
    right_gauge: bool,
) -> tuple[float, float, float, float]:
    checked = max(0.0, min(1.0, fraction))
    angle = SCALE_START_ANGLE + SCALE_SWEEP_ANGLE * checked
    center_x = (
        RIGHT_GAUGE_CENTER_X
        if right_gauge
        else LEFT_GAUGE_CENTER_X
    )
    x = center_x - GAUGE_RADIUS * math.cos(angle)
    y = GAUGE_CENTER_Y - GAUGE_RADIUS * math.sin(angle)
    tangent_x = (
        SCALE_SWEEP_ANGLE * GAUGE_RADIUS * math.sin(angle)
    )
    tangent_y = (
        -SCALE_SWEEP_ANGLE * GAUGE_RADIUS * math.cos(angle)
    )
    return x, y, tangent_x, tangent_y


def inward_normal(
    tangent_x: float,
    tangent_y: float,
) -> tuple[float, float]:
    normal_x = -tangent_y
    normal_y = tangent_x
    length = math.hypot(normal_x, normal_y)
    if length < 0.001:
        return 0.0, 1.0
    return normal_x / length, normal_y / length


def offset_scale_point(
    fraction: float,
    offset: float,
    right_gauge: bool,
) -> tuple[float, float]:
    x, y, tangent_x, tangent_y = scale_point(
        fraction,
        right_gauge,
    )
    normal_x, normal_y = inward_normal(tangent_x, tangent_y)
    return x + normal_x * offset, y + normal_y * offset


def _scaled_points(
    points: list[tuple[float, float]],
) -> list[tuple[int, int]]:
    return [
        (
            round(x * RENDER_SCALE),
            round(y * RENDER_SCALE),
        )
        for x, y in points
    ]


def _sample_scale(
    right_gauge: bool,
    offset: float = 0.0,
    samples: int = 600,
) -> list[tuple[float, float]]:
    return [
        offset_scale_point(
            index / samples,
            offset,
            right_gauge,
        )
        for index in range(samples + 1)
    ]


def _draw_line(
    draw: ImageDraw.ImageDraw,
    points: list[tuple[float, float]],
    fill: tuple[int, int, int, int],
    width: float,
) -> None:
    draw.line(
        _scaled_points(points),
        fill=fill,
        width=max(1, round(width * RENDER_SCALE)),
        joint="curve",
    )


def _draw_notification_corner_mask(background: Image.Image) -> None:
    mask = Image.new(
        "L",
        (
            SIZE[0] * RENDER_SCALE,
            SIZE[1] * RENDER_SCALE,
        ),
        0,
    )
    draw = ImageDraw.Draw(mask)
    left, top, right, bottom = NOTIFICATION_RECT
    draw.rectangle(
        (
            left * RENDER_SCALE,
            top * RENDER_SCALE,
            right * RENDER_SCALE,
            bottom * RENDER_SCALE,
        ),
        fill=255,
    )
    center_x, center_y = NOTIFICATION_APERTURE_CENTER
    draw.ellipse(
        (
            (
                center_x - NOTIFICATION_APERTURE_RADIUS_X
            ) * RENDER_SCALE,
            (
                center_y - NOTIFICATION_APERTURE_RADIUS_Y
            ) * RENDER_SCALE,
            (
                center_x + NOTIFICATION_APERTURE_RADIUS_X
            ) * RENDER_SCALE,
            (
                center_y + NOTIFICATION_APERTURE_RADIUS_Y
            ) * RENDER_SCALE,
        ),
        fill=0,
    )
    mask = mask.filter(
        ImageFilter.GaussianBlur(
            NOTIFICATION_EDGE_BLUR * RENDER_SCALE
        )
    )
    black = Image.new("RGBA", background.size, (0, 0, 0, 255))
    background.alpha_composite(
        Image.composite(black, Image.new("RGBA", background.size), mask)
    )


def _draw_ticks(
    draw: ImageDraw.ImageDraw,
    right_gauge: bool,
    divisions: int,
    major_every: int,
) -> None:
    for index in range(divisions + 1):
        fraction = index / divisions
        major = index % major_every == 0
        start = offset_scale_point(fraction, 2.0, right_gauge)
        end = offset_scale_point(
            fraction,
            27.0 if major else 16.0,
            right_gauge,
        )
        _draw_line(
            draw,
            [start, end],
            (241, 242, 242, 255),
            3.0 if major else 1.5,
        )


def _draw_centered_text(
    draw: ImageDraw.ImageDraw,
    text: str,
    position: tuple[float, float],
    font: ImageFont.FreeTypeFont,
    fill: tuple[int, int, int, int],
) -> None:
    draw.text(
        (
            round(position[0] * RENDER_SCALE),
            round(position[1] * RENDER_SCALE),
        ),
        text,
        font=font,
        fill=fill,
        anchor="mm",
        stroke_width=1,
        stroke_fill=(12, 14, 16, 230),
    )


def _draw_labels(
    draw: ImageDraw.ImageDraw,
    font_path: Path,
    right_gauge: bool,
) -> None:
    labels = TACH_LABELS if right_gauge else SPEED_LABELS
    label_size = 27 if right_gauge else 30
    label_font = ImageFont.truetype(
        str(font_path),
        label_size * RENDER_SCALE,
    )
    last_index = len(labels) - 1
    for index, value in enumerate(labels):
        _draw_centered_text(
            draw,
            str(value),
            offset_scale_point(
                index / last_index,
                55.0,
                right_gauge,
            ),
            label_font,
            (239, 240, 240, 255),
        )

    unit_font = ImageFont.truetype(
        str(font_path),
        (17 if right_gauge else 20) * RENDER_SCALE,
    )
    _draw_centered_text(
        draw,
        "1/min x1000" if right_gauge else "km/h",
        offset_scale_point(
            0.94,
            80.0,
            right_gauge,
        ),
        unit_font,
        (194, 198, 201, 255),
    )


def build_scale_background(font_path: Path) -> Image.Image:
    del font_path
    render_size = (
        SIZE[0] * RENDER_SCALE,
        SIZE[1] * RENDER_SCALE,
    )
    background = Image.new("RGBA", render_size, (0, 0, 0, 0))
    _draw_notification_corner_mask(background)

    glow = Image.new("RGBA", render_size, (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    for right_gauge in (False, True):
        _draw_line(
            glow_draw,
            _sample_scale(right_gauge, 34.0),
            (255, 18, 18, 220),
            17.0,
        )
    glow = glow.filter(
        ImageFilter.GaussianBlur(11.0 * RENDER_SCALE)
    )
    background = Image.alpha_composite(background, glow)

    draw = ImageDraw.Draw(background)
    for right_gauge in (False, True):
        _draw_line(
            draw,
            _sample_scale(right_gauge),
            (239, 241, 241, 255),
            3.0,
        )
        _draw_ticks(
            draw,
            right_gauge,
            60 if right_gauge else 40,
            10 if right_gauge else 4,
        )
        _draw_line(
            draw,
            _sample_scale(right_gauge, 34.0),
            (255, 28, 28, 255),
            4.0,
        )

    return background.resize(SIZE, Image.Resampling.LANCZOS)


def build_background(
    source: Image.Image,
    map_mask: Image.Image,
    font_path: Path | None = None,
) -> Image.Image:
    del source
    del map_mask
    checked_font_path = font_path or (
        Path(__file__).resolve().parents[1]
        / "app/src/main/assets/fonts/Rajdhani-Medium.ttf"
    )
    return build_scale_background(checked_font_path)


def write_assets(root: Path, output: Path) -> None:
    sport = root / "app/src/main/assets/dashboard/skins/sport"
    map_mask_path = (
        root
        / "docs/H9_Cluster_Neutral_Design_Template_1920x720"
        / "02_yandex_map_transparency_mask.png"
    )
    output.mkdir(parents=True, exist_ok=True)

    source = Image.open(sport / "background.png").convert("RGBA")
    map_mask = Image.open(map_mask_path).convert("L")
    font_path = root / "app/src/main/assets/fonts/Rajdhani-Medium.ttf"
    build_background(source, map_mask, font_path).save(
        output / "background.png"
    )

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build deterministic Simple Red dashboard assets."
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
    )
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    output = args.output or (
        args.root
        / "app/src/main/assets/dashboard/skins/simplered"
    )
    write_assets(args.root, output)


if __name__ == "__main__":
    main()
