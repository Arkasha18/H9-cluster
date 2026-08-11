#!/usr/bin/env python3
"""Build the canonical 1920×720 H9 Cluster design template.

The template packages the vehicle-calibrated black edge background and the
v2.4 system-icon reserved-area overlay without changing the public filenames.
"""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


WIDTH = 1920
HEIGHT = 720
SIZE = (WIDTH, HEIGHT)

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_SYSTEM_MASK = (
    SCRIPT_DIR / "assets" / "h9_system_icon_forbidden_mask_1920x720.png"
)


def font(size: int) -> ImageFont.ImageFont:
    candidates = (
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/System/Library/Fonts/SFNS.ttf",
    )
    for candidate in candidates:
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            pass
    return ImageFont.load_default()


def checked_image(path: Path, mode: str) -> Image.Image:
    image = Image.open(path).convert(mode)
    if image.size != SIZE:
        raise ValueError(f"{path}: expected {WIDTH}×{HEIGHT}, got {image.size}")
    return image


def checkerboard() -> Image.Image:
    image = Image.new("RGBA", SIZE, (28, 34, 38, 255))
    draw = ImageDraw.Draw(image)
    cell = 48
    colours = ((31, 44, 50, 255), (47, 61, 67, 255))
    for top in range(0, HEIGHT, cell):
        for left in range(0, WIDTH, cell):
            draw.rectangle(
                (left, top, left + cell - 1, top + cell - 1),
                fill=colours[((left // cell) + (top // cell)) % 2],
            )
    return image


def create_map_transparency_mask(background: Image.Image) -> Image.Image:
    """Mark only pixels that are fully transparent in the calibrated layer."""
    return background.getchannel("A").point(
        lambda alpha: 255 if alpha == 0 else 0
    )


def create_black_map_gradient(source: Path) -> Image.Image:
    """Validate and return the calibrated pure-black RGBA layer."""
    gradient = checked_image(source, "RGBA")
    if any(gradient.getchannel(channel).getextrema()[1] != 0 for channel in "RGB"):
        raise ValueError(f"{source}: calibrated background RGB must be pure black")
    return gradient


def create_guide(
    system_overlay: Image.Image,
    map_mask: Image.Image,
    black_gradient: Image.Image,
) -> Image.Image:
    image = checkerboard()
    image.alpha_composite(black_gradient)

    map_overlay = Image.new("RGBA", SIZE, (34, 222, 158, 0))
    map_overlay.putalpha(map_mask.point(lambda value: 82 if value else 0))
    image.alpha_composite(map_overlay)

    reserved_overlay = Image.new("RGBA", SIZE, (255, 62, 21, 0))
    reserved_overlay.putalpha(
        system_overlay.getchannel("A").point(
            lambda value: round(value * 205 / 255)
        )
    )
    image.alpha_composite(reserved_overlay)

    draw = ImageDraw.Draw(image)
    map_bbox = map_mask.getbbox()
    if map_bbox is None:
        raise ValueError("calibrated background has no fully transparent map area")
    left, top, right, bottom = map_bbox
    draw.rectangle(
        (left, top, right - 1, bottom - 1),
        outline=(64, 255, 196, 255),
        width=4,
    )
    draw.rectangle(
        (0, 0, WIDTH - 1, HEIGHT - 1),
        outline=(255, 255, 255, 190),
        width=2,
    )

    title = font(23)
    label = font(20)
    small = font(17)

    panel = (664, 276, 1255, 458)
    draw.rounded_rectangle(
        panel,
        radius=18,
        fill=(3, 7, 9, 225),
        outline=(138, 157, 166, 220),
        width=2,
    )
    draw.text(
        (708, 300),
        "H9 CLUSTER — ТЕХНИЧЕСКИЙ ШАБЛОН 1920×720",
        font=title,
        fill=(255, 255, 255, 255),
    )
    draw.rectangle((708, 355, 728, 375), fill=(34, 222, 158, 210))
    draw.text(
        (744, 352),
        "1. Карта: откалиброванная чёрная подложка",
        font=label,
        fill=(232, 247, 242, 255),
    )
    draw.rectangle((708, 400, 728, 420), fill=(255, 62, 21, 255))
    draw.text(
        (744, 397),
        "2. Красные зоны: маска системных иконок v2.4",
        font=label,
        fill=(255, 238, 234, 255),
    )
    draw.text(
        (739, 438),
        "За пределами этих зон компоновка и стиль свободны.",
        font=small,
        fill=(188, 205, 211, 255),
    )
    return image


def write_readme(destination: Path, map_bbox: tuple[int, int, int, int]) -> None:
    left, top, right, bottom = map_bbox
    destination.write_text(
        f"""H9 CLUSTER — АКТУАЛЬНЫЙ ТЕХНИЧЕСКИЙ ШАБЛОН

Рабочая система координат всех файлов: 1920×720.

Шаблон объединяет откалиброванную на реальном автомобиле чёрную подложку и
маску зарезервированных зон системных иконок v2.4. Имена файлов сохранены для
совместимости с опубликованными ссылками.

1. ОТКАЛИБРОВАННАЯ ПОДЛОЖКА И ОКНО КАРТЫ

Файл `04_yandex_map_black_gradient_rgba.png` — готовый нижний слой нового
скина. Он содержит отдельную растушёвку слева и справа, верхнюю полосу,
раздельные нижние участки и локально осветлённые зоны системных значков.
Его нужно использовать непосредственно, не рисовать похожий градиент заново.

Карта выводится штатной системой под приложением. Полностью прозрачная область
готовой подложки отмечена белым в `02_yandex_map_transparency_mask.png`:
    X = {left}..{right - 1}
    Y = {top}..{bottom - 1}

Полупрозрачная растушёвка вокруг этой области намеренно остаётся частью
подложки: через неё карта плавно проявляется к центру.

2. ЗОНЫ СИСТЕМНЫХ ЗНАЧКОВ

Файл `03_system_icons_forbidden_mask.png` содержит актуальный RGBA-шаблон v2.4.
Непрозрачные и полупрозрачные чёрные пиксели отмечают области, занятые
системными иконками. Полностью прозрачные пиксели ограничений не задают.

При создании скина файл нужно открыть временным верхним слоем и не размещать
под ним графику, текст, шкалы, цифры, рамки, свечение и динамические показания.

Это контрольный слой, а не часть итогового оформления. Его нельзя запекать в
готовый фон и нельзя использовать для вырезания отверстий из уже нарисованного
скина. Размер, положение и полупрозрачные края шаблона нельзя менять.

ЧТО НЕ ОГРАНИЧЕНО

Всё остальное автор скина определяет самостоятельно: форму и положение шкал,
набор и расположение датчиков, цвета, шрифты, размеры, композицию, количество
слоёв и способ отрисовки. Требование только функциональное: реальные и
изменяющиеся значения должны оставаться динамическими, а не быть запечены в
статический фон.

СОСТАВ

00_blank_1920x720_rgba.png
    Полностью прозрачный рабочий холст.

01_technical_guide.png
    Контрольная схема двух обязательных зон. Не использовать как фон скина.

02_yandex_map_transparency_mask.png
    Одноканальная маска: белый = alpha=0 в готовой подложке.

03_system_icons_forbidden_mask.png
    Актуальный RGBA-шаблон системных иконок v2.4. Непрозрачный или
    полупрозрачный чёрный = зарезервированная область.

04_yandex_map_black_gradient_rgba.png
    Готовая откалиброванная чёрная RGBA-подложка нового скина.

ПРОВЕРКА

1. Все итоговые растровые слои имеют размер 1920×720.
2. Итоговый alpha равен 0 во всей белой области маски карты.
3. Ни статические, ни динамические элементы не пересекают непрозрачные и
   полупрозрачные области системного шаблона v2.4.
4. Файл 04 используется как нижняя подложка без изменения его альфа-канала.

Других обязательных правил компоновки этот шаблон не задаёт.
""",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument(
        "--gradient-source",
        "--opacity-source",
        dest="gradient_source",
        required=True,
        type=Path,
        help="calibrated pure-black 1920×720 RGBA background",
    )
    parser.add_argument(
        "--system-mask-source",
        type=Path,
        default=DEFAULT_SYSTEM_MASK,
        help="canonical 1920×720 RGBA system-icon overlay v2.4",
    )
    args = parser.parse_args()

    output = args.output_dir
    output.mkdir(parents=True, exist_ok=True)

    system_overlay = checked_image(args.system_mask_source, "RGBA")
    black_gradient = create_black_map_gradient(args.gradient_source)
    map_mask = create_map_transparency_mask(black_gradient)

    Image.new("RGBA", SIZE, (0, 0, 0, 0)).save(
        output / "00_blank_1920x720_rgba.png", "PNG", optimize=True
    )
    create_guide(system_overlay, map_mask, black_gradient).save(
        output / "01_technical_guide.png", "PNG", optimize=True
    )
    map_mask.save(
        output / "02_yandex_map_transparency_mask.png", "PNG", optimize=True
    )
    shutil.copyfile(
        args.system_mask_source,
        output / "03_system_icons_forbidden_mask.png",
    )
    shutil.copyfile(
        args.gradient_source,
        output / "04_yandex_map_black_gradient_rgba.png",
    )
    map_bbox = map_mask.getbbox()
    if map_bbox is None:
        raise ValueError("calibrated background has no fully transparent map area")
    write_readme(output / "README_RU.txt", map_bbox)


if __name__ == "__main__":
    main()
