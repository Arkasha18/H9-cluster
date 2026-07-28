#!/usr/bin/env python3
"""Build the canonical 1920×720 H9 Cluster design template.

The template intentionally defines only two layout constraints:
the Yandex Navigator aperture and the system-icon collision mask.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


WIDTH = 1920
HEIGHT = 720
SIZE = (WIDTH, HEIGHT)

# Confirmed geometry of the Yandex Navigator window.
MAP_POLYGON = ((745, 105), (1173, 105), (1287, 719), (632, 719))

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


def create_map_transparency_mask() -> Image.Image:
    image = Image.new("L", SIZE, 0)
    ImageDraw.Draw(image).polygon(MAP_POLYGON, fill=255)
    return image


def create_black_map_gradient(source: Path) -> Image.Image:
    """Turn the proven Classic alpha envelope into a pure-black RGBA layer."""
    alpha = checked_image(source, "RGBA").getchannel("A")

    # The binary map mask is the hard rule. Any residual antialiasing inside
    # the aperture is cleared while the original outer fade remains intact.
    ImageDraw.Draw(alpha).polygon(MAP_POLYGON, fill=0)

    gradient = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    gradient.putalpha(alpha)
    return gradient


def create_guide(
    system_mask: Image.Image,
    map_mask: Image.Image,
    black_gradient: Image.Image,
) -> Image.Image:
    image = checkerboard()
    image.alpha_composite(black_gradient)

    map_overlay = Image.new("RGBA", SIZE, (34, 222, 158, 0))
    map_overlay.putalpha(map_mask.point(lambda value: 82 if value else 0))
    image.alpha_composite(map_overlay)

    system_overlay = Image.new("RGBA", SIZE, (255, 62, 21, 0))
    system_overlay.putalpha(system_mask.point(lambda value: 205 if value else 0))
    image.alpha_composite(system_overlay)

    draw = ImageDraw.Draw(image)
    draw.line(
        (*MAP_POLYGON, MAP_POLYGON[0]),
        fill=(64, 255, 196, 255),
        width=4,
        joint="curve",
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
        "1. Карта: прозрачное окно + чёрный градиент",
        font=label,
        fill=(232, 247, 242, 255),
    )
    draw.rectangle((708, 400, 728, 420), fill=(255, 62, 21, 255))
    draw.text(
        (744, 397),
        "2. Красные зоны: не размещать графику",
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


def write_readme(destination: Path) -> None:
    destination.write_text(
        """H9 CLUSTER — АКТУАЛЬНЫЙ ТЕХНИЧЕСКИЙ ШАБЛОН

Рабочая система координат всех файлов: 1920×720.

У нового скина есть только два обязательных ограничения по дизайну.

1. ОКНО ЯНДЕКС КАРТЫ

Карта выводится штатной системой под приложением. Итоговая композиция скина
обязана иметь alpha=0 во всей белой области файла
`02_yandex_map_transparency_mask.png`.

Координаты окна:
    верхняя левая точка   (745, 105)
    верхняя правая точка  (1173, 105)
    нижняя правая точка   (1287, 719)
    нижняя левая точка    (632, 719)

На границе окна используется чёрный градиент. Готовый технический слой
`04_yandex_map_black_gradient_rgba.png` извлечён из проверенного alpha-канала
темы Classic. Его можно положить под оформление нового скина или точно
воспроизвести его альфа-переход. Внутри маски карты слой полностью прозрачен.

2. ЗОНЫ СИСТЕМНЫХ ЗНАЧКОВ

Файл `03_system_icons_forbidden_mask.png` построен только по красной разметке
владельца автомобиля. Исходный шаблон 1280×480 перенесён в 1920×720 точным
масштабом ×1,5. Из изображения исключено красное свечение штатных шкал;
сохранены шесть крупных областей пользовательской разметки.

Белый = запрещено размещать любую графику скина, текст, шкалы, цифры, рамки,
свечение и динамические показания.
Чёрный = эта маска ограничений не задаёт.

Это collision-маска, а не маска прозрачности. Она не требует вырезать отверстия
и не должна использоваться для обрезки уже нарисованных приборов. Размер и
контур белых областей нельзя самовольно увеличивать или уменьшать.

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
    Одноканальная маска: белый = обязательный alpha=0.

03_system_icons_forbidden_mask.png
    Одноканальная collision-маска из красного пользовательского шаблона:
    белый = не размещать графику.

04_yandex_map_black_gradient_rgba.png
    Чистый чёрный RGBA-слой с проверенным градиентом границы карты.

ПРОВЕРКА

1. Все итоговые растровые слои имеют размер 1920×720.
2. Итоговый alpha равен 0 во всей белой области маски карты.
3. Ни статические, ни динамические элементы не пересекают белую системную
   collision-маску.
4. На границе карты присутствует чёрный градиент из файла 04 либо его точная
   реализация.

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
        help="1920×720 RGBA image whose alpha contains the proven map fade",
    )
    parser.add_argument(
        "--system-mask-source",
        type=Path,
        default=DEFAULT_SYSTEM_MASK,
        help="canonical 1920×720 binary system-icon mask",
    )
    args = parser.parse_args()

    output = args.output_dir
    output.mkdir(parents=True, exist_ok=True)

    system_mask = checked_image(args.system_mask_source, "L")
    map_mask = create_map_transparency_mask()
    black_gradient = create_black_map_gradient(args.gradient_source)

    Image.new("RGBA", SIZE, (0, 0, 0, 0)).save(
        output / "00_blank_1920x720_rgba.png", "PNG", optimize=True
    )
    create_guide(system_mask, map_mask, black_gradient).save(
        output / "01_technical_guide.png", "PNG", optimize=True
    )
    map_mask.save(
        output / "02_yandex_map_transparency_mask.png", "PNG", optimize=True
    )
    system_mask.save(
        output / "03_system_icons_forbidden_mask.png", "PNG", optimize=True
    )
    black_gradient.save(
        output / "04_yandex_map_black_gradient_rgba.png", "PNG", optimize=True
    )
    write_readme(output / "README_RU.txt")


if __name__ == "__main__":
    main()
