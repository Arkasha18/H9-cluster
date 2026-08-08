#!/usr/bin/env python3
"""Apply the production geometry and alpha masks to the Classic dashboard artwork."""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter


CANVAS_WIDTH = 1920
CANVAS_HEIGHT = 720
DEFAULT_TRANSPARENCY_MASK = (
    Path(__file__).resolve().parent
    / "assets"
    / "dashboard_transparency_mask_1920x720.png"
)

# Five millimetres at the project's 160 dpi reference density is about 32 px.
# The original main-dial ellipse spans Y=164..656. Its bottom remains fixed.
SOURCE_DIAL_TOP = 164
DIAL_BOTTOM = 656
TOP_INSET_PX = 32
SOURCE_RADIUS_Y = 246.0
TARGET_RADIUS_Y = 230.0
VERTICAL_SCALE = TARGET_RADIUS_Y / SOURCE_RADIUS_Y

# The source artwork accidentally labels both upper speedometer marks as 180.
# Reuse the existing 6 from the lower "60" label so the corrected "160" keeps
# the original typeface, slant and antialiasing.
SPEED_TARGET_DIGIT_BOX = (263, 242, 280, 257)
SPEED_SOURCE_SIX_BOX = (161, 538, 180, 555)

# The user's reference mask has a straight, mirrored inner edge.
MASK_TOP_Y = 105
MASK_BOTTOM_Y = 719
LEFT_SOLID_TOP_X = 575.0
LEFT_SOLID_BOTTOM_X = 418.0


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


def left_solid_boundary(y: np.ndarray) -> np.ndarray:
    fraction = np.clip(
        (y - MASK_TOP_Y) / (MASK_BOTTOM_Y - MASK_TOP_Y),
        0.0,
        1.0,
    )
    return LEFT_SOLID_TOP_X + fraction * (
        LEFT_SOLID_BOTTOM_X - LEFT_SOLID_TOP_X
    )


def edge_connected_boundaries(alpha: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Return the old visible inner edges connected to the left and right canvas edges."""
    height, width = alpha.shape
    left = np.zeros(height, dtype=np.float32)
    right = np.full(height, width - 1, dtype=np.float32)
    visible = alpha > 1

    for y in range(height):
        row = visible[y]
        if row[0]:
            false_indices = np.flatnonzero(~row)
            left[y] = float(false_indices[0] - 1) if false_indices.size else width - 1
        if row[-1]:
            false_indices = np.flatnonzero(~row[::-1])
            right[y] = (
                float(width - false_indices[0])
                if false_indices.size
                else 0.0
            )
    return left, right


def vertically_compress_main_dials(source: Image.Image) -> Image.Image:
    """Compress both main dials vertically while anchoring their lower edge."""
    source_array = np.asarray(source, dtype=np.float32)
    warped = np.zeros_like(source_array)

    destination_top = int(round(
        DIAL_BOTTOM - (DIAL_BOTTOM - MASK_TOP_Y) * VERTICAL_SCALE
    ))
    source_crop = source.crop((0, MASK_TOP_Y, CANVAS_WIDTH, DIAL_BOTTOM + 1))
    destination_height = DIAL_BOTTOM - destination_top + 1
    resized = source_crop.resize(
        (CANVAS_WIDTH, destination_height),
        Image.Resampling.LANCZOS,
    )
    warped[destination_top:DIAL_BOTTOM + 1] = np.asarray(
        resized,
        dtype=np.float32,
    )

    yy, xx = np.mgrid[0:CANVAS_HEIGHT, 0:CANVAS_WIDTH].astype(np.float32)
    boundary = left_solid_boundary(yy)

    # Replace the main-dial areas completely and feather only their inner seams.
    # The top band includes the old position so it is cleared after the dial moves.
    vertical = smoothstep((yy - (MASK_TOP_Y - 4.0)) / 8.0)
    vertical *= 1.0 - smoothstep((yy - (DIAL_BOTTOM - 4.0)) / 8.0)
    left = 1.0 - smoothstep((xx - (boundary - 8.0)) / 32.0)
    right_boundary = (CANVAS_WIDTH - 1.0) - boundary
    right = smoothstep((xx - (right_boundary - 24.0)) / 32.0)
    blend = np.maximum(left, right) * vertical
    blend = blend[..., None]

    # Blend in premultiplied-alpha space to avoid dark or white edge fringes.
    source_alpha = source_array[..., 3:4] / 255.0
    warped_alpha = warped[..., 3:4] / 255.0
    source_premultiplied = source_array[..., :3] * source_alpha
    warped_premultiplied = warped[..., :3] * warped_alpha
    output_alpha = source_alpha * (1.0 - blend) + warped_alpha * blend
    output_rgb_premultiplied = (
        source_premultiplied * (1.0 - blend)
        + warped_premultiplied * blend
    )
    output_rgb = np.divide(
        output_rgb_premultiplied,
        output_alpha,
        out=np.zeros_like(output_rgb_premultiplied),
        where=output_alpha > 1.0e-6,
    )
    output = np.concatenate((output_rgb, output_alpha * 255.0), axis=2)
    return Image.fromarray(np.clip(output, 0.0, 255.0).astype(np.uint8), "RGBA")


def remove_bottom_light_streaks(source: Image.Image) -> Image.Image:
    """Cover the narrow horizontal remnants below both main dial arcs."""
    pixels = np.asarray(source, dtype=np.float32).copy()
    yy, xx = np.mgrid[0:CANVAS_HEIGHT, 0:CANVAS_WIDTH].astype(np.float32)

    vertical = smoothstep((yy - 638.0) / 6.0)
    vertical *= 1.0 - smoothstep((yy - 665.0) / 5.0)
    left = smoothstep((xx - 336.0) / 20.0)
    left *= 1.0 - smoothstep((xx - 515.0) / 8.0)
    right = left[:, ::-1]
    cover = np.maximum(left, right) * vertical

    pixels[..., :3] *= 1.0 - cover[..., None]
    return Image.fromarray(np.clip(pixels, 0.0, 255.0).astype(np.uint8), "RGBA")


def correct_speedometer_160(source: Image.Image) -> Image.Image:
    """Replace only the middle digit of the first upper 180 label with a 6."""
    pixels = np.asarray(source, dtype=np.float32).copy()
    left, top, right, bottom = SPEED_TARGET_DIGIT_BOX
    padding = 4
    crop_left = left - padding
    crop_top = top - padding
    crop_right = right + padding
    crop_bottom = bottom + padding
    crop = pixels[crop_top:crop_bottom, crop_left:crop_right].copy()

    digit = crop[
        padding:padding + (bottom - top),
        padding:padding + (right - left),
        :3,
    ]
    digit_luminance = np.mean(digit, axis=2)
    digit_chroma = np.max(digit, axis=2) - np.min(digit, axis=2)
    digit_mask = (
        (digit_luminance > 85.0)
        & (digit_chroma < 45.0)
    )

    mask = np.zeros(crop.shape[:2], dtype=np.uint8)
    mask[
        padding:padding + (bottom - top),
        padding:padding + (right - left),
    ] = digit_mask.astype(np.uint8) * 255
    mask_image = Image.fromarray(mask, "L").filter(ImageFilter.MaxFilter(5))
    hard_mask = np.asarray(mask_image, dtype=np.uint8) > 0

    # Harmonic inpainting removes the old 8 without introducing a rectangular
    # patch. Only the dilated glyph footprint is changed.
    filled = crop.copy()
    boundary_color = np.median(filled[~hard_mask], axis=0)
    filled[hard_mask] = boundary_color
    for _ in range(160):
        padded = np.pad(filled, ((1, 1), (1, 1), (0, 0)), mode="edge")
        averaged = (
            padded[:-2, 1:-1]
            + padded[2:, 1:-1]
            + padded[1:-1, :-2]
            + padded[1:-1, 2:]
        ) * 0.25
        filled[hard_mask] = averaged[hard_mask]

    feather = np.asarray(
        mask_image.filter(ImageFilter.GaussianBlur(0.6)),
        dtype=np.float32,
    ) / 255.0
    crop = crop * (1.0 - feather[..., None]) + filled * feather[..., None]
    pixels[crop_top:crop_bottom, crop_left:crop_right] = crop

    source_left, source_top, source_right, source_bottom = SPEED_SOURCE_SIX_BOX
    source_six = source.crop(
        (source_left, source_top, source_right, source_bottom)
    ).resize(
        (right - left, bottom - top),
        Image.Resampling.LANCZOS,
    )
    source_six_array = np.asarray(source_six, dtype=np.float32)
    source_rgb = source_six_array[..., :3]
    source_luminance = np.mean(source_rgb, axis=2)
    source_chroma = np.max(source_rgb, axis=2) - np.min(source_rgb, axis=2)
    glyph_alpha = np.clip(
        (source_luminance - 15.0) / 220.0,
        0.0,
        1.0,
    )
    glyph_alpha *= (source_chroma < 45.0).astype(np.float32)
    glyph_alpha = np.asarray(
        Image.fromarray(
            np.clip(glyph_alpha * 255.0, 0.0, 255.0).astype(np.uint8),
            "L",
        ).filter(ImageFilter.GaussianBlur(0.25)),
        dtype=np.float32,
    ) / 255.0

    target = pixels[top:bottom, left:right]
    glyph_color = np.full_like(target[..., :3], 238.0)
    target[..., :3] = (
        target[..., :3] * (1.0 - glyph_alpha[..., None])
        + glyph_color * glyph_alpha[..., None]
    )
    pixels[top:bottom, left:right] = target
    return Image.fromarray(np.clip(pixels, 0.0, 255.0).astype(np.uint8), "RGBA")


def split_center_layers(source: Image.Image) -> tuple[Image.Image, Image.Image]:
    """Split the fading black plate from accessory gauges and contour graphics."""
    pixels = np.asarray(source, dtype=np.float32)
    alpha = pixels[..., 3]
    old_left, _ = edge_connected_boundaries(alpha)
    # The original right mask contains a lower rectangular notch. Mirror the
    # clean left boundary so the final lower fades have identical geometry.
    old_right = (CANVAS_WIDTH - 1.0) - old_left
    # The source has an asymmetric lower-left alpha extension. Mirror the
    # lower-right edge onto the left so the black fade has the same clean
    # silhouette on both sides; foreground contour lines stay in the overlay.
    mirrored_right_edge = (CANVAS_WIDTH - 1.0) - old_right
    old_left = np.minimum(old_left, mirrored_right_edge)

    yy, xx = np.mgrid[0:CANVAS_HEIGHT, 0:CANVAS_WIDTH].astype(np.float32)
    new_left = left_solid_boundary(yy)
    new_right = (CANVAS_WIDTH - 1.0) - new_left

    left_width = np.maximum(old_left[:, None] - new_left, 1.0)
    left_progress = (xx - new_left) / left_width
    left_mask = 1.0 - smoothstep(left_progress)
    left_mask[xx > old_left[:, None]] = 0.0

    right_width = np.maximum(new_right - old_right[:, None], 1.0)
    right_progress = (new_right - xx) / right_width
    right_mask = 1.0 - smoothstep(right_progress)
    right_mask[xx < old_right[:, None]] = 0.0

    center_mask = np.maximum(left_mask, right_mask)
    center_mask[yy < MASK_TOP_Y] = 1.0

    rgb = pixels[..., :3]
    maximum = np.max(rgb, axis=2)
    minimum = np.min(rgb, axis=2)
    chroma = maximum - minimum
    luminance = (
        rgb[..., 0] * 0.2126
        + rgb[..., 1] * 0.7152
        + rgb[..., 2] * 0.0722
    )

    # Neutral grey contours are detected by luminance, while the red/yellow
    # gauge segments and warning icons are also protected by their chroma.
    grey_graphics = smoothstep((luminance - 14.0) / 44.0)
    coloured_graphics = (
        smoothstep((chroma - 12.0) / 36.0)
        * smoothstep((maximum - 24.0) / 48.0)
    )
    graphic_strength = np.maximum(grey_graphics, coloured_graphics)

    left_accessory = (
        (xx >= 475.0)
        & (xx <= 765.0)
        & (yy >= MASK_TOP_Y)
        & (yy <= 490.0)
    )
    right_accessory = (
        (xx >= 1155.0)
        & (xx <= 1445.0)
        & (yy >= MASK_TOP_Y)
        & (yy <= 605.0)
    )
    left_inner_contours = (
        (xx >= new_left - 70.0)
        & (xx <= old_left[:, None] + 8.0)
        & (yy >= MASK_TOP_Y)
    )
    right_inner_contours = (
        (xx <= new_right + 70.0)
        & (xx >= old_right[:, None] - 8.0)
        & (yy >= MASK_TOP_Y)
    )
    accessory_region = (
        left_accessory
        | right_accessory
        | left_inner_contours
        | right_inner_contours
    )
    graphic_strength *= accessory_region.astype(np.float32)

    # Expand only one pixel around detected artwork to keep antialiased edges
    # intact without carrying broad areas of the black plate into the overlay.
    strength_image = Image.fromarray(
        np.clip(graphic_strength * 255.0, 0.0, 255.0).astype(np.uint8),
        "L",
    )
    strength_image = strength_image.filter(ImageFilter.MaxFilter(3))
    strength_image = strength_image.filter(ImageFilter.GaussianBlur(0.55))
    graphic_strength = np.asarray(strength_image, dtype=np.float32) / 255.0
    graphic_strength *= (alpha > 0.0).astype(np.float32)

    base = pixels.copy()
    base[..., :3] = rgb * (1.0 - graphic_strength[..., None])
    base[..., 3] = np.clip(alpha * center_mask, 0.0, 255.0)

    # Below Y=615 the stock right plate has a rectangular alpha notch that is
    # not present on the left. Replace only this black lower base with a
    # mirrored copy of the left alpha profile; the independent overlay remains
    # untouched, so the car, gauges and contour lines keep their own geometry.
    lower_blend = smoothstep((yy - 615.0) / 20.0)
    right_lower_region = (
        (xx >= CANVAS_WIDTH * 0.5)
        & (xx <= 1555.0)
        & (yy >= 615.0)
    )
    symmetry_blend = lower_blend * right_lower_region.astype(np.float32)
    mirrored_base_alpha = base[..., 3][:, ::-1]
    base[..., 3] = (
        base[..., 3] * (1.0 - symmetry_blend)
        + mirrored_base_alpha * symmetry_blend
    )
    base[..., :3] *= 1.0 - symmetry_blend[..., None]

    overlay = np.zeros_like(pixels)
    overlay[..., :3] = rgb
    overlay[..., 3] = np.clip(alpha * graphic_strength, 0.0, 255.0)

    return (
        Image.fromarray(np.clip(base, 0.0, 255.0).astype(np.uint8), "RGBA"),
        Image.fromarray(np.clip(overlay, 0.0, 255.0).astype(np.uint8), "RGBA"),
    )


def apply_transparency_mask(source: Image.Image, mask: Image.Image) -> Image.Image:
    """Remove the mask's opaque white windows from one Classic artwork layer."""
    if mask.size != source.size:
        raise ValueError(
            f"Transparency mask must be {source.width}x{source.height}, "
            f"got {mask.width}x{mask.height}"
        )

    pixels = np.asarray(source.convert("RGBA"), dtype=np.float32).copy()
    mask_alpha = np.asarray(mask.convert("RGBA"), dtype=np.float32)[..., 3]
    pixels[..., 3] *= 1.0 - mask_alpha / 255.0
    return Image.fromarray(np.clip(pixels, 0.0, 255.0).astype(np.uint8), "RGBA")


def main() -> None:
    args = parse_args()
    source = Image.open(args.input).convert("RGBA")
    if source.size != (CANVAS_WIDTH, CANVAS_HEIGHT):
        raise ValueError(
            f"Expected {CANVAS_WIDTH}x{CANVAS_HEIGHT}, got {source.size}"
        )

    edited = vertically_compress_main_dials(source)
    edited = remove_bottom_light_streaks(edited)
    edited = correct_speedometer_160(edited)
    background, overlay = split_center_layers(edited)
    transparency_mask = Image.open(args.transparency_mask).convert("RGBA")
    background = apply_transparency_mask(background, transparency_mask)
    overlay = apply_transparency_mask(overlay, transparency_mask)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.overlay_output.parent.mkdir(parents=True, exist_ok=True)
    background.save(args.output, "PNG", optimize=True)
    overlay.save(args.overlay_output, "PNG", optimize=True)


if __name__ == "__main__":
    main()
