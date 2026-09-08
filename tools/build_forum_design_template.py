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
    """Build the deprecated link-compatible mask from the calibrated layer."""
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
    black_gradient: Image.Image,
) -> Image.Image:
    image = checkerboard()
    image.alpha_composite(black_gradient)

    reserved_overlay = Image.new("RGBA", SIZE, (255, 62, 21, 0))
    reserved_overlay.putalpha(
        system_overlay.getchannel("A").point(
            lambda value: round(value * 205 / 255)
        )
    )
    image.alpha_composite(reserved_overlay)

    draw = ImageDraw.Draw(image)
    draw.rectangle(
        (0, 0, WIDTH - 1, HEIGHT - 1),
        outline=(255, 255, 255, 190),
        width=2,
    )

    title = font(23)
    label = font(20)
    small = font(17)
    callout_title = font(18)
    callout_note = font(15)

    gear_panel = (770, 94, 1150, 158)
    draw.rounded_rectangle(
        gear_panel,
        radius=12,
        fill=(3, 7, 9, 225),
        outline=(255, 178, 73, 235),
        width=2,
    )
    draw.line((960, 94, 960, 77), fill=(255, 178, 73, 255), width=3)
    draw.ellipse((955, 72, 965, 82), fill=(255, 178, 73, 255))
    draw.text(
        (790, 104),
        "ТЕКУЩАЯ ПЕРЕДАЧА",
        font=callout_title,
        fill=(255, 255, 255, 255),
    )
    draw.text(
        (790, 132),
        "штатная система — скин не рисует",
        font=callout_note,
        fill=(220, 226, 229, 255),
    )

    drive_mode_panel = (1220, 584, 1620, 648)
    draw.rounded_rectangle(
        drive_mode_panel,
        radius=12,
        fill=(3, 7, 9, 225),
        outline=(255, 178, 73, 235),
        width=2,
    )
    draw.line((1476, 648, 1476, 669), fill=(255, 178, 73, 255), width=3)
    draw.ellipse((1471, 664, 1481, 674), fill=(255, 178, 73, 255))
    draw.text(
        (1240, 594),
        "РЕЖИМ ДВИЖЕНИЯ (driveMode)",
        font=callout_title,
        fill=(255, 255, 255, 255),
    )
    draw.text(
        (1240, 622),
        "штатная система — скин не рисует",
        font=callout_note,
        fill=(220, 226, 229, 255),
    )

    panel = (505, 238, 1415, 510)
    draw.rounded_rectangle(
        panel,
        radius=18,
        fill=(3, 7, 9, 232),
        outline=(138, 157, 166, 230),
        width=2,
    )
    draw.text(
        (545, 262),
        "H9 CLUSTER — ТЕХНИЧЕСКИЙ ГАЙД 1920×720",
        font=title,
        fill=(255, 255, 255, 255),
    )
    draw.text(
        (545, 301),
        "Справочная визуализация — не использовать как фон скина",
        font=small,
        fill=(188, 205, 211, 255),
    )

    draw.rectangle(
        (545, 345, 569, 369),
        fill=(36, 44, 48, 255),
        outline=(214, 224, 228, 255),
        width=2,
    )
    draw.line((547, 367, 567, 347), fill=(214, 224, 228, 255), width=2)
    draw.text(
        (589, 342),
        "04 — точная RGBA-подложка; видимость карты задаёт её альфа-канал",
        font=label,
        fill=(232, 239, 242, 255),
    )

    draw.rectangle((545, 389, 569, 413), fill=(255, 62, 21, 255))
    draw.text(
        (589, 386),
        "03 — красные области заняты штатной системой",
        font=label,
        fill=(255, 238, 234, 255),
    )

    draw.rectangle(
        (545, 433, 569, 457),
        fill=(36, 44, 48, 255),
        outline=(151, 163, 168, 255),
        width=2,
    )
    draw.line((547, 435, 567, 455), fill=(255, 178, 73, 255), width=3)
    draw.line((567, 435, 547, 455), fill=(255, 178, 73, 255), width=3)
    draw.text(
        (589, 430),
        "02 — УСТАРЕЛА: не использовать как границу или маску карты",
        font=label,
        fill=(255, 205, 139, 255),
    )

    draw.text(
        (545, 476),
        "Обязательный состав показаний: FORUM_DESIGN_PROMPTS_RU.md",
        font=small,
        fill=(188, 205, 211, 255),
    )
    return image


def write_readme(destination: Path) -> None:
    destination.write_text(
        """H9 CLUSTER — АКТУАЛЬНЫЙ ТЕХНИЧЕСКИЙ ШАБЛОН

Рабочая система координат всех файлов: 1920×720.

Шаблон объединяет откалиброванную на реальном автомобиле чёрную подложку и
маску зарезервированных зон системных иконок v2.4. Имена файлов сохранены для
совместимости с опубликованными ссылками.

1. ОТКАЛИБРОВАННАЯ ПОДЛОЖКА И ОКНО КАРТЫ

Файл `04_yandex_map_black_gradient_rgba.png` — готовый нижний слой нового
скина. Он содержит отдельную растушёвку слева и справа, верхнюю полосу,
раздельные нижние участки и локально осветлённые зоны системных значков.
Его нужно использовать непосредственно, не рисовать похожий градиент заново.

Карта выводится штатной системой под приложением и видна через прозрачные и
полупрозрачные участки готовой подложки, в том числе в центре экрана. Её
фактические границы и плавное проявление задаёт альфа-канал файла 04.

`02_yandex_map_transparency_mask.png` устарела: её белая область не
соответствует фактической области отображения карты. Файл сохранён только ради
совместимости с опубликованными ссылками. Не используй его как границу карты,
маску прозрачности, clipPath или проверочную маску.

2. ЗОНЫ СИСТЕМНЫХ ЗНАЧКОВ

Файл `03_system_icons_forbidden_mask.png` содержит актуальный RGBA-шаблон v2.4.
Непрозрачные и полупрозрачные чёрные пиксели отмечают области, занятые
системными иконками. Полностью прозрачные пиксели ограничений не задают.

При создании скина файл нужно открыть временным верхним слоем и не размещать
под ним графику, текст, шкалы, цифры, рамки, свечение и динамические показания.

В верхнем центральном квадрате этой маски штатная система всегда показывает
текущую передачу. В маленьком квадрате справа внизу она показывает режим
движения `driveMode`. Эти два реальных показателя не рисуются скином: обе
области нужно оставить свободными и учитывать в общей композиции.

Это контрольный слой, а не часть итогового оформления. Его нельзя запекать в
готовый фон и нельзя использовать для вырезания отверстий из уже нарисованного
скина. Размер, положение и полупрозрачные края шаблона нельзя менять.

СВОБОДА ВИЗУАЛЬНОГО ОФОРМЛЕНИЯ

Автор скина самостоятельно определяет форму и положение шкал, расположение
показаний, которые рисует приложение, цвета, шрифты, размеры, композицию,
количество слоёв и способ отрисовки. Обязательный состав автомобильных
показаний фиксирован в `FORUM_DESIGN_PROMPTS_RU.md` и сокращать его нельзя.
Реальные и изменяющиеся значения должны оставаться динамическими, а не быть
запечены в статический фон.

СОСТАВ

00_blank_1920x720_rgba.png
    Полностью прозрачный рабочий холст.

01_technical_guide.png
    Справочная визуализация точного слоя 04 и красного наложения зон 03 с
    подписями штатных областей передачи и driveMode. Не использовать как фон
    скина и не определять по ней фактические границы карты.

02_yandex_map_transparency_mask.png
    Устаревшая справочная маска. Не использовать при создании или проверке
    скина.

03_system_icons_forbidden_mask.png
    Актуальный RGBA-шаблон системных иконок v2.4. Непрозрачный или
    полупрозрачный чёрный = зарезервированная область.

04_yandex_map_black_gradient_rgba.png
    Готовая откалиброванная чёрная RGBA-подложка нового скина.

ПРОВЕРКА

1. Все итоговые растровые слои имеют размер 1920×720.
2. Файл 04 используется как нижняя подложка без изменения его положения,
   пикселей и альфа-канала; файл 02 не используется.
3. Ни статические, ни динамические элементы скина не пересекают непрозрачные и
   полупрозрачные области системного шаблона v2.4.
4. Скин не рисует текущую передачу и `driveMode`; их верхняя центральная и
   нижняя правая штатные области остаются свободными.
5. Макет содержит весь обязательный состав показаний из
   `FORUM_DESIGN_PROMPTS_RU.md`.

В пределах этих требований визуальное оформление остаётся свободным.
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
    create_guide(system_overlay, black_gradient).save(
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
    write_readme(output / "README_RU.txt")


if __name__ == "__main__":
    main()
