#!/usr/bin/env python3
"""Build the calibrated template and typed technical fields, never skin cutouts."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont

WIDTH, HEIGHT = 1920, 720
SIZE = (WIDTH, HEIGHT)
SCRIPT_DIR = Path(__file__).resolve().parent
ROOT = SCRIPT_DIR.parent
DEFAULT_SYSTEM_MASK = SCRIPT_DIR / "assets/h9_system_icon_forbidden_mask_1920x720.png"
DEFAULT_GRADIENT = ROOT / "app/src/main/assets/dashboard/skins/common/calibrated_edge_background.png"
TEMPLATE_DIR = ROOT / "docs/H9_Cluster_Neutral_Design_Template_1920x720"
FONT_PATH = SCRIPT_DIR / "assets/fonts/NotoSans.ttf"
LOWER_LEFT_ROIS = ((387, 373, 498, 442), (230, 494, 548, 642))
GEAR_BOUNDS = (982, 28, 1018, 66)
MAP_CORE = (672, 80, 1096, 720)
HARD_COLOUR = (255, 94, 91)
FACTORY_COLOUR = (70, 196, 239)
TEMPLATE_FILES = (
    "00_blank_1920x720_rgba.png",
    "01_technical_guide.png",
    "02_yandex_map_transparency_mask.png",
    "03_system_icons_forbidden_mask.png",
    "04_yandex_map_black_gradient_rgba.png",
    "05_system_fields_overlay.png",
    "06_lower_left_reserved_mask.png",
    "07_factory_overlay_fields_mask.png",
    "system_fields.json",
    "README_RU.txt",
)


def font(size: int) -> ImageFont.FreeTypeFont:
    # Same Cyrillic glyphs on Windows, Linux CI and macOS; no OS fallback.
    return ImageFont.truetype(str(FONT_PATH), size)


def checked_image(path: Path, mode: str) -> Image.Image:
    with Image.open(path) as source:
        if source.size != SIZE or source.mode != mode:
            raise ValueError(f"{path}: expected {SIZE} {mode}, got {source.size} {source.mode}")
        return source.copy()


def create_black_map_gradient(source: Path) -> Image.Image:
    gradient = checked_image(source, "RGBA")
    if any(gradient.getchannel(channel).getextrema()[1] != 0 for channel in "RGB"):
        raise ValueError(f"{source}: calibrated background RGB must be pure black")
    return gradient


def create_map_transparency_mask(background: Image.Image) -> Image.Image:
    """Legacy filename only. Not a map-boundary or renderer mask."""
    return background.getchannel("A").point(lambda alpha: 255 if alpha == 0 else 0)


def split_system_fields(system_overlay: Image.Image) -> tuple[Image.Image, Image.Image]:
    """Partition the exact source alpha, including fractional-alpha edges."""
    alpha = system_overlay.getchannel("A")
    lower_left = Image.new("L", SIZE, 0)
    for box in LOWER_LEFT_ROIS:
        lower_left.paste(alpha.crop(box), box)
    factory = ImageChops.subtract(alpha, lower_left)
    return lower_left, factory


def create_fields_overlay(lower_left: Image.Image, factory: Image.Image) -> Image.Image:
    image = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    for mask, colour in ((factory, FACTORY_COLOUR), (lower_left, HARD_COLOUR)):
        layer = Image.new("RGBA", SIZE, (*colour, 0))
        # Full field geometry stays visible even where original alpha is tiny.
        layer.putalpha(mask.point(lambda value: 160 if value > 0 else 0))
        image.alpha_composite(layer)
    return image


def checkerboard() -> Image.Image:
    image = Image.new("RGBA", SIZE)
    draw = ImageDraw.Draw(image)
    colours = ((26, 36, 43, 255), (39, 50, 58, 255))
    for y in range(0, HEIGHT, 48):
        for x in range(0, WIDTH, 48):
            draw.rectangle((x, y, x + 47, y + 47), fill=colours[(x // 48 + y // 48) % 2])
    return image


def create_guide(fields: Image.Image, gradient: Image.Image) -> Image.Image:
    image = checkerboard()
    image.alpha_composite(gradient)
    image.alpha_composite(fields)
    draw = ImageDraw.Draw(image)
    ink, muted, gold = (242, 246, 249), (185, 202, 212), (255, 206, 109)
    draw.text((34, 21), "H9 / ТЕХНИЧЕСКИЕ ПОЛЯ", font=font(25), fill=ink)
    draw.text((1630, 25), "1920 × 720 / v2.4", font=font(22), fill=ink)
    draw.rectangle((982, 28, 1017, 65), outline=gold, width=2)

    def panel(box: tuple, lines: list[str], colour: tuple, size: int = 19) -> None:
        draw.rounded_rectangle(box, radius=12, fill=(7, 15, 22, 246), outline=colour, width=2)
        for index, line in enumerate(lines):
            draw.text((box[0] + 20, box[1] + 13 + index * 28), line,
                      font=font(size), fill=ink if index == 0 else muted)

    draw.line((1000, 67, 1000, 104), fill=gold, width=2)
    panel((680, 104, 1240, 177), ["Коробка: декор под штатным слоем",
          "Скин добавляет только 1…8 в D; буквы — система"], gold)
    panel((570, 232, 1350, 525), [
        "НАЛОЖЕНИЕ ПОЛЕЙ, А НЕ ИМИТАЦИЯ ИКОНОК",
        "Голубые: декоративный фон может проходить снизу.",
        "Важные цифры и шкалы держите вне штатных полей.",
        "Красные слева: добавленная графика запрещена + 1 px.",
        "Сохраните здесь alpha исходной подложки 04.",
        "04 задаёт видимость карты. 02 больше не используется.",
        "Контрольный слой 05 накладывается на готовый макет.",
        "03 / 05 / 06 / 07 не запекаются в оформление.",
        "Точные маски и правила: README_RU.txt, system_fields.json",
    ], (95, 122, 140), size=18)
    draw.line((1476, 635, 1476, 667), fill=FACTORY_COLOUR, width=2)
    panel((1140, 562, 1780, 635), ["Режим движения: декор допустим под полем",
          "NORMAL / ECO и другие значения рисует система"], FACTORY_COLOUR)
    draw.text((30, 676), "Справочная схема. Не использовать как фон скина.", font=font(18), fill=muted)
    return image


def metadata(system_source: Path, gradient_source: Path, lower_left: Image.Image) -> dict:
    return {
        "schema_version": 1,
        "canvas": {"width": WIDTH, "height": HEIGHT},
        "coordinate_convention": "[left, top, right, bottom); right and bottom excluded",
        "system_mask_version": "2.4",
        "sources": {
            "03_system_icons_forbidden_mask.png": hashlib.sha256(system_source.read_bytes()).hexdigest(),
            "04_yandex_map_black_gradient_rgba.png": hashlib.sha256(gradient_source.read_bytes()).hexdigest(),
        },
        "nonzero_alpha_is_reserved": True,
        "lower_left": {
            "mask": "06_lower_left_reserved_mask.png",
            "source_alpha_intersect_rois": LOWER_LEFT_ROIS,
            "nonzero_pixels": sum(lower_left.histogram()[1:]),
            "foreground_alpha": 0,
            "clearance_px": 1,
            "composite_rule": "preserve calibrated base alpha, do not force composite alpha to zero",
        },
        "factory_overlay": {
            "mask": "07_factory_overlay_fields_mask.png",
            "decorative_underlay_allowed": True,
            "critical_readouts_allowed": False,
            "selector_letters_and_drive_mode": "factory owned; no production duplicates",
        },
        "automatic_gear_exception": {
            "bounds": GEAR_BOUNDS, "center_x": 1000, "baseline_y": 63,
            "font": "Rajdhani-Medium.ttf", "font_size_px": 44,
            "selector": "D", "valid_ratios": list(range(1, 9)),
        },
        "map": {
            "calibrated_clear_core": MAP_CORE,
            "artwork_corridor": "design-specific; record in the skin layout specification",
            "source_04_immutable": True,
            "derived_edge_treatment": "allowed with documented transform and alpha tests",
            "legacy_02_is_not_a_map_boundary": True,
        },
        "overlay": "05_system_fields_overlay.png",
        "masks_are_validation_only": True,
    }


def build_template(output: Path, gradient_source: Path = DEFAULT_GRADIENT,
                   system_source: Path = DEFAULT_SYSTEM_MASK) -> None:
    system = checked_image(system_source, "RGBA")
    gradient = create_black_map_gradient(gradient_source)
    lower_left, factory = split_system_fields(system)
    fields = create_fields_overlay(lower_left, factory)
    output.mkdir(parents=True, exist_ok=True)
    images = {
        "00_blank_1920x720_rgba.png": Image.new("RGBA", SIZE, (0, 0, 0, 0)),
        "01_technical_guide.png": create_guide(fields, gradient),
        "02_yandex_map_transparency_mask.png": create_map_transparency_mask(gradient),
        "05_system_fields_overlay.png": fields,
        "06_lower_left_reserved_mask.png": lower_left,
        "07_factory_overlay_fields_mask.png": factory,
    }
    for name, image in images.items():
        image.save(output / name, "PNG", optimize=True)
    for source, name in ((system_source, "03_system_icons_forbidden_mask.png"),
                         (gradient_source, "04_yandex_map_black_gradient_rgba.png")):
        if source.resolve() != (output / name).resolve():
            shutil.copyfile(source, output / name)
    (output / "system_fields.json").write_text(
        json.dumps(metadata(system_source, gradient_source, lower_left), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8", newline="\n",
    )
    shutil.copyfile(SCRIPT_DIR / "assets/skin_template_readme_ru.txt", output / "README_RU.txt")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=TEMPLATE_DIR)
    parser.add_argument("--gradient-source", "--opacity-source", dest="gradient_source",
                        type=Path, default=DEFAULT_GRADIENT)
    parser.add_argument("--system-mask-source", type=Path, default=DEFAULT_SYSTEM_MASK)
    args = parser.parse_args()
    build_template(args.output_dir, args.gradient_source, args.system_mask_source)


if __name__ == "__main__":
    main()
