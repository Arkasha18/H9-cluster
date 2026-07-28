#!/usr/bin/env python3
"""Build separately hideable raster layers for the Classic Custom skin."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Mapping

import numpy as np
from PIL import Image, ImageDraw


CANVAS_SIZE = (1920, 720)
RASTER_LAYER_NAMES = (
    "speedometer",
    "tachometer",
    "fuel_and_range",
    "engine_temperature",
    "tyre_pressure",
)
REMOVED_RASTER_LAYER_NAMES = (
    "odometers",
    "fuel_consumption",
    "battery_voltage",
)


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    dashboard = root / "app/src/main/assets/dashboard"
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--background",
        type=Path,
        default=dashboard / "background_classic.png",
    )
    parser.add_argument(
        "--overlay",
        type=Path,
        default=dashboard / "background_classic_overlay.png",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=dashboard / "classic_custom",
    )
    return parser.parse_args()


def polygon_mask(points: tuple[tuple[int, int], ...]) -> np.ndarray:
    image = Image.new("L", CANVAS_SIZE, 0)
    ImageDraw.Draw(image).polygon(points, fill=255)
    return np.asarray(image, dtype=np.uint8) > 0


def rectangle_mask(bounds: tuple[int, int, int, int]) -> np.ndarray:
    image = Image.new("L", CANVAS_SIZE, 0)
    ImageDraw.Draw(image).rectangle(bounds, fill=255)
    return np.asarray(image, dtype=np.uint8) > 0


def semantic_masks(source: np.ndarray) -> dict[str, np.ndarray]:
    """Return explicit visual ownership regions, ordered narrowest to widest."""
    fuel_and_range = polygon_mask((
        (470, 95),
        (780, 95),
        (780, 510),
        (720, 719),
        (475, 719),
        (445, 510),
    ))
    fuel_and_range &= ~rectangle_mask((438, 198, 536, 272))

    engine_temperature = polygon_mask((
        (1140, 95),
        (1450, 95),
        (1475, 510),
        (1445, 719),
        (1200, 719),
        (1140, 510),
    ))
    engine_temperature &= ~rectangle_mask((1384, 198, 1478, 272))

    tyre_icon_bounds = rectangle_mask((1370, 505, 1430, 585))
    tyre_icon = tyre_icon_bounds & (
        np.max(source[..., :3], axis=2) >= 48
    )

    return {
        "tyre_pressure": tyre_icon,
        "fuel_and_range": fuel_and_range,
        "engine_temperature": engine_temperature,
        "speedometer": polygon_mask((
            (0, 0),
            (780, 0),
            (780, 719),
            (0, 719),
        )),
        "tachometer": polygon_mask((
            (1140, 0),
            (1919, 0),
            (1919, 719),
            (1140, 719),
        )),
    }


def partition_composite(composite: Image.Image) -> dict[str, Image.Image]:
    source = np.asarray(composite.convert("RGBA"), dtype=np.uint8)
    visible = source[..., 3] > 0
    assigned = np.zeros(visible.shape, dtype=bool)
    ownership: dict[str, np.ndarray] = {}

    for name, candidate in semantic_masks(source).items():
        owned = visible & candidate & ~assigned
        ownership[name] = owned
        assigned |= owned

    unassigned_y, unassigned_x = np.nonzero(visible & ~assigned)
    if unassigned_x.size:
        examples = list(zip(
            unassigned_x[:8].tolist(),
            unassigned_y[:8].tolist(),
        ))
        raise ValueError(
            "Visible Classic pixels have no semantic owner; "
            f"first coordinates: {examples}"
        )

    layers: dict[str, Image.Image] = {}
    for name in RASTER_LAYER_NAMES:
        pixels = np.zeros_like(source)
        pixels[ownership[name]] = source[ownership[name]]
        layers[name] = Image.fromarray(pixels, "RGBA")
    return layers


def compose_layers(layers: Mapping[str, Image.Image]) -> Image.Image:
    composite = Image.new("RGBA", CANVAS_SIZE, (0, 0, 0, 0))
    for name in RASTER_LAYER_NAMES:
        composite = Image.alpha_composite(
            composite,
            layers[name].convert("RGBA"),
        )
    return composite


def generate_layers(
        background_path: Path,
        overlay_path: Path,
        output_dir: Path,
) -> None:
    background = Image.open(background_path).convert("RGBA")
    overlay = Image.open(overlay_path).convert("RGBA")
    if background.size != CANVAS_SIZE or overlay.size != CANVAS_SIZE:
        raise ValueError(
            f"Expected both inputs to be {CANVAS_SIZE[0]}x{CANVAS_SIZE[1]}"
        )

    layers = partition_composite(Image.alpha_composite(background, overlay))
    output_dir.mkdir(parents=True, exist_ok=True)
    for old_name in RASTER_LAYER_NAMES + REMOVED_RASTER_LAYER_NAMES:
        old_layer = output_dir / f"{old_name}.png"
        if old_layer.exists():
            old_layer.unlink()
    for name, layer in layers.items():
        layer.save(output_dir / f"{name}.png", "PNG", optimize=True)


def main() -> None:
    args = parse_args()
    generate_layers(args.background, args.overlay, args.output_dir)


if __name__ == "__main__":
    main()
