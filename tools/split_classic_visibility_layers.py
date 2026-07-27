#!/usr/bin/env python3
"""Partition the approved Classic composite into independently visible layers."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Mapping

import numpy as np
from PIL import Image, ImageDraw


CANVAS_SIZE = (1920, 720)
LAYER_NAMES = (
    "common",
    "speedometer",
    "tachometer",
    "fuel_and_range",
    "engine_temperature",
    "tyre_pressure",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--background", required=True, type=Path)
    parser.add_argument("--overlay", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    return parser.parse_args()


def polygon_mask(points: list[tuple[int, int]]) -> np.ndarray:
    mask = Image.new("L", CANVAS_SIZE, 0)
    ImageDraw.Draw(mask).polygon(points, fill=255)
    return np.asarray(mask, dtype=np.uint8) > 0


def rectangle_mask(bounds: tuple[int, int, int, int]) -> np.ndarray:
    mask = Image.new("L", CANVAS_SIZE, 0)
    ImageDraw.Draw(mask).rectangle(bounds, fill=255)
    return np.asarray(mask, dtype=np.uint8) > 0


def semantic_masks() -> dict[str, np.ndarray]:
    fuel = polygon_mask([
        (485, 105),
        (770, 105),
        (770, 500),
        (704, 720),
        (514, 720),
        (480, 500),
    ])
    fuel &= ~rectangle_mask((438, 198, 536, 272))

    coolant = polygon_mask([
        (1150, 105),
        (1435, 105),
        (1440, 500),
        (1406, 720),
        (1216, 720),
        (1150, 500),
    ])
    coolant &= ~rectangle_mask((1384, 198, 1478, 272))

    tyre = polygon_mask([
        (1368, 500),
        (1428, 500),
        (1428, 590),
        (1368, 590),
    ])
    return {
        "tyre_pressure": tyre,
        "fuel_and_range": fuel,
        "engine_temperature": coolant,
    }


def partition_composite(composite: Image.Image) -> dict[str, Image.Image]:
    source = np.asarray(composite.convert("RGBA"), dtype=np.uint8)
    visible = source[..., 3] > 0
    assigned = np.zeros(visible.shape, dtype=bool)
    ownership: dict[str, np.ndarray] = {}

    for name, candidate in semantic_masks().items():
        owned = visible & candidate & ~assigned
        ownership[name] = owned
        assigned |= owned

    xx = np.broadcast_to(np.arange(CANVAS_SIZE[0]), visible.shape)
    speedometer = visible & (xx < CANVAS_SIZE[0] // 2) & ~assigned
    ownership["speedometer"] = speedometer
    assigned |= speedometer

    tachometer = visible & (xx >= CANVAS_SIZE[0] // 2) & ~assigned
    ownership["tachometer"] = tachometer
    assigned |= tachometer

    ownership["common"] = visible & ~assigned

    layers: dict[str, Image.Image] = {}
    for name in LAYER_NAMES:
        pixels = np.zeros_like(source)
        pixels[ownership[name]] = source[ownership[name]]
        layers[name] = Image.fromarray(pixels, "RGBA")
    return layers


def compose_layers(layers: Mapping[str, Image.Image]) -> Image.Image:
    composite = Image.new("RGBA", CANVAS_SIZE, (0, 0, 0, 0))
    for name in LAYER_NAMES:
        composite = Image.alpha_composite(composite, layers[name].convert("RGBA"))
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

    composite = Image.alpha_composite(background, overlay)
    layers = partition_composite(composite)
    output_dir.mkdir(parents=True, exist_ok=True)
    for name, layer in layers.items():
        layer.save(output_dir / f"{name}.png", "PNG", optimize=True)


def main() -> None:
    args = parse_args()
    generate_layers(args.background, args.overlay, args.output_dir)


if __name__ == "__main__":
    main()
