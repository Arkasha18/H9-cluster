#!/usr/bin/env python3
"""Cut the shared dashboard windows from Sport while preserving 20 and 0."""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image


CANVAS_SIZE = (1920, 720)
DEFAULT_TRANSPARENCY_MASK = (
    Path(__file__).resolve().parent
    / "assets"
    / "dashboard_transparency_mask_1920x720.png"
)

# Tight source boxes around the printed speedometer values intersected by the
# lower transparency window. The red scale line and neighbouring 40 stay out.
SPEED_DIGIT_REGIONS = (
    (304, 617, 355, 655),  # 20
    (428, 627, 461, 666),  # 0
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--overlay-output", required=True, type=Path)
    parser.add_argument(
        "--transparency-mask",
        type=Path,
        default=DEFAULT_TRANSPARENCY_MASK,
    )
    return parser.parse_args()


def smoothstep(value: np.ndarray) -> np.ndarray:
    value = np.clip(value, 0.0, 1.0)
    return value * value * (3.0 - 2.0 * value)


def apply_transparency_mask(source: Image.Image, mask: Image.Image) -> Image.Image:
    if source.size != CANVAS_SIZE or mask.size != CANVAS_SIZE:
        raise ValueError(
            f"Sport artwork and mask must both be {CANVAS_SIZE[0]}x{CANVAS_SIZE[1]}"
        )
    pixels = np.asarray(source.convert("RGBA"), dtype=np.float32).copy()
    mask_alpha = np.asarray(mask.convert("RGBA"), dtype=np.float32)[..., 3]
    pixels[..., 3] *= 1.0 - mask_alpha / 255.0
    return Image.fromarray(np.clip(pixels, 0.0, 255.0).astype(np.uint8), "RGBA")


def extract_speed_digit_overlay(source: Image.Image) -> Image.Image:
    """Isolate the neutral light glyph pixels without retaining their black plate."""
    pixels = np.asarray(source.convert("RGBA"), dtype=np.float32)
    overlay = np.zeros_like(pixels)

    for left, top, right, bottom in SPEED_DIGIT_REGIONS:
        region = pixels[top:bottom, left:right]
        overlay[top:bottom, left:right, :3] = region[..., :3]
        rgb = region[..., :3]
        maximum = np.max(rgb, axis=2)
        minimum = np.min(rgb, axis=2)
        chroma = maximum - minimum
        luminance = (
            rgb[..., 0] * 0.2126
            + rgb[..., 1] * 0.7152
            + rgb[..., 2] * 0.0722
        )
        light_strength = smoothstep((luminance - 18.0) / 190.0)
        colour_strength = smoothstep((chroma - 18.0) / 42.0)
        glyph_alpha = 255.0 * light_strength * (1.0 - colour_strength)
        overlay[top:bottom, left:right, 3] = np.maximum(
            overlay[top:bottom, left:right, 3],
            glyph_alpha,
        )

    return Image.fromarray(np.clip(overlay, 0.0, 255.0).astype(np.uint8), "RGBA")


def main() -> None:
    args = parse_args()
    source = Image.open(args.input).convert("RGBA")
    mask = Image.open(args.transparency_mask).convert("RGBA")
    background = apply_transparency_mask(source, mask)
    overlay = extract_speed_digit_overlay(source)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.overlay_output.parent.mkdir(parents=True, exist_ok=True)
    background.save(args.output, "PNG", optimize=True)
    overlay.save(args.overlay_output, "PNG", optimize=True)


if __name__ == "__main__":
    main()
