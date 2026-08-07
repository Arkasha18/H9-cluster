#!/usr/bin/env python3
"""Build a smooth calibration outline from the system-icon forbidden mask.

`h9_system_icon_forbidden_mask_1920x720.png` (a.k.a.
`docs/H9_Cluster_Neutral_Design_Template_1920x720/03_system_icons_forbidden_mask.png`)
was traced by hand from a photo of the real cluster, so its boundary is a
raw pixel staircase (lots of small straight steps instead of a clean curve).

This script produces a *new* transparent PNG that traces a smooth outline
right at the edge of each of the mask's 6 regions, with the staircase noise
removed: gentle curves everywhere a curve is expected, and clean right
angles preserved on the rectangular tabs. The outline is meant to be baked
into the calibration APK and nudged against real dashboard photos, so it is
drawn as a thin bright core line with a soft dark halo to stay visible over
any background.

The canonical mask itself is never modified or resized — only a smoothed
*copy* of its silhouette is used to place the outline.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import cv2
import numpy as np
from PIL import Image

SIZE = (1920, 720)

# Supersampling factor used while smoothing/stroking, for anti-aliased,
# perfectly even edges with no visible pixel steps in the final PNG.
SUPERSAMPLE = 6

# How aggressively to round off staircase noise, in *original* pixels.
# The two big hand-traced arcs need a fairly wide radius to erase their
# staircase; the small rectangular tabs only need a light touch so they
# keep their real right angles.
SMOOTH_RADIUS_BIG = 22
SMOOTH_RADIUS_SMALL = 3
BIG_COMPONENT_MIN_AREA = 20000

STROKE_WIDTH = 4
HALO_WIDTH = 3
CORE_COLOR = (255, 255, 255)
HALO_COLOR = (0, 0, 0)
HALO_ALPHA = 150


def load_binary_mask(path: Path) -> np.ndarray:
    image = Image.open(path).convert("L")
    if image.size != SIZE:
        raise ValueError(f"{path}: expected {SIZE}, got {image.size}")
    array = np.array(image)
    return (array > 127).astype(np.uint8) * 255


def supersampled_smooth(mask_roi: np.ndarray, radius: int) -> np.ndarray:
    """Upscale, blur and re-threshold, then let the caller downsample later.

    Working at a higher resolution before downsampling is what turns a
    coarse pixel staircase into a properly anti-aliased, evenly smooth edge.
    """
    big = cv2.resize(
        mask_roi,
        None,
        fx=SUPERSAMPLE,
        fy=SUPERSAMPLE,
        interpolation=cv2.INTER_NEAREST,
    )
    sigma = max(1.0, radius * SUPERSAMPLE / 3.0)
    blurred = cv2.GaussianBlur(big, (0, 0), sigma)
    _, hard = cv2.threshold(blurred, 127, 255, cv2.THRESH_BINARY)
    return hard


def outward_ring_big(mask_big: np.ndarray, width_px: int) -> np.ndarray:
    kernel_size = width_px * SUPERSAMPLE * 2 + 1
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (kernel_size, kernel_size))
    dilated = cv2.dilate(mask_big, kernel)
    return cv2.subtract(dilated, mask_big)


def downsample(mask_big: np.ndarray, roi_shape: tuple[int, int]) -> np.ndarray:
    height, width = roi_shape
    return cv2.resize(mask_big, (width, height), interpolation=cv2.INTER_AREA)


def render_component_outline(mask_roi: np.ndarray, radius: int) -> np.ndarray:
    """Return an RGBA outline layer (white core + dark halo) for one region."""
    smooth_big = supersampled_smooth(mask_roi, radius)

    halo_big = outward_ring_big(smooth_big, STROKE_WIDTH + HALO_WIDTH)
    core_big = outward_ring_big(smooth_big, STROKE_WIDTH)
    halo_only_big = cv2.subtract(halo_big, core_big)

    core_alpha = downsample(core_big, mask_roi.shape)
    halo_alpha = downsample(halo_only_big, mask_roi.shape)

    height, width = mask_roi.shape
    layer = np.zeros((height, width, 4), dtype=np.uint8)

    halo_norm = (halo_alpha.astype(np.float32) / 255.0) * HALO_ALPHA
    layer[:, :, 0] = HALO_COLOR[0]
    layer[:, :, 1] = HALO_COLOR[1]
    layer[:, :, 2] = HALO_COLOR[2]
    layer[:, :, 3] = halo_norm.astype(np.uint8)

    core_pixels = core_alpha > 0
    layer[core_pixels, 0:3] = CORE_COLOR
    layer[core_pixels, 3] = core_alpha[core_pixels]

    return layer


def build_outline(mask: np.ndarray) -> np.ndarray:
    count, labels, stats, _ = cv2.connectedComponentsWithStats(mask, connectivity=8)
    canvas = np.zeros((*mask.shape, 4), dtype=np.uint8)

    for label in range(1, count):
        x, y, w, h, area = stats[label]
        if area < 5:
            continue

        radius = SMOOTH_RADIUS_BIG if area >= BIG_COMPONENT_MIN_AREA else SMOOTH_RADIUS_SMALL
        pad = radius * 3 + STROKE_WIDTH + HALO_WIDTH + 4

        x0 = max(0, x - pad)
        y0 = max(0, y - pad)
        x1 = min(mask.shape[1], x + w + pad)
        y1 = min(mask.shape[0], y + h + pad)

        component_mask = np.where(labels[y0:y1, x0:x1] == label, mask[y0:y1, x0:x1], 0)
        layer = render_component_outline(component_mask, radius)

        region = canvas[y0:y1, x0:x1]
        alpha_f = layer[:, :, 3:4].astype(np.float32) / 255.0
        region[:, :, 0:3] = (
            layer[:, :, 0:3].astype(np.float32) * alpha_f
            + region[:, :, 0:3].astype(np.float32) * (1 - alpha_f)
        ).astype(np.uint8)
        region[:, :, 3] = np.maximum(region[:, :, 3], layer[:, :, 3])

    return canvas


def build_smoothed_silhouette(mask: np.ndarray) -> np.ndarray:
    """A clean, anti-aliased copy of the mask silhouette (for reference/QA)."""
    count, labels, stats, _ = cv2.connectedComponentsWithStats(mask, connectivity=8)
    canvas = np.zeros(mask.shape, dtype=np.uint8)

    for label in range(1, count):
        x, y, w, h, area = stats[label]
        if area < 5:
            continue

        radius = SMOOTH_RADIUS_BIG if area >= BIG_COMPONENT_MIN_AREA else SMOOTH_RADIUS_SMALL
        pad = radius * 3 + 4

        x0 = max(0, x - pad)
        y0 = max(0, y - pad)
        x1 = min(mask.shape[1], x + w + pad)
        y1 = min(mask.shape[0], y + h + pad)

        component_mask = np.where(labels[y0:y1, x0:x1] == label, mask[y0:y1, x0:x1], 0)
        smooth_big = supersampled_smooth(component_mask, radius)
        smooth = downsample(smooth_big, component_mask.shape)
        canvas[y0:y1, x0:x1] = np.maximum(canvas[y0:y1, x0:x1], smooth)

    return canvas


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()

    mask = load_binary_mask(args.source)

    outline = build_outline(mask)
    smoothed = build_smoothed_silhouette(mask)

    args.output_dir.mkdir(parents=True, exist_ok=True)

    Image.fromarray(outline, "RGBA").save(
        args.output_dir / "system_icons_forbidden_mask_outline_1920x720.png",
        "PNG",
        optimize=True,
    )
    Image.fromarray(smoothed, "L").save(
        args.output_dir / "system_icons_forbidden_mask_smoothed_1920x720.png",
        "PNG",
        optimize=True,
    )

    preview = Image.new("RGBA", SIZE, (30, 30, 30, 255))
    mask_rgba = np.zeros((*mask.shape, 4), dtype=np.uint8)
    mask_rgba[:, :, 0:3] = 90
    mask_rgba[:, :, 3] = mask
    preview.alpha_composite(Image.fromarray(mask_rgba, "RGBA"))
    preview.alpha_composite(Image.fromarray(outline, "RGBA"))
    preview.convert("RGB").save(
        args.output_dir / "system_icons_forbidden_mask_outline_preview_1920x720.png",
        "PNG",
        optimize=True,
    )


if __name__ == "__main__":
    main()
