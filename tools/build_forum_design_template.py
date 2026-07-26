#!/usr/bin/env python3
"""Build a neutral 1920x720 design template without Classic artwork."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


WIDTH = 1920
HEIGHT = 720

GEAR_BOX = (882, 0, 1038, 105)
MAP_POLYGON = ((745, 105), (1173, 105), (1287, 719), (632, 719))
LEFT_ICON_COLLISION = (0, 105, 575, 196)
RIGHT_ICON_COLLISION = (1345, 105, 1919, 196)
LEFT_SIDE_ICON_COLLISION = (0, 180, 310, 500)
RIGHT_SIDE_ICON_COLLISION = (1610, 180, 1919, 500)
LEFT_TURN_SIGNAL_COLLISION = (580, 0, 750, 105)
RIGHT_TURN_SIGNAL_COLLISION = (1170, 0, 1340, 105)
DRIVE_MODE_COLLISION = (1180, 630, 1400, 719)
SYSTEM_OVERLAY_COLLISIONS = (
    LEFT_ICON_COLLISION,
    RIGHT_ICON_COLLISION,
    LEFT_SIDE_ICON_COLLISION,
    RIGHT_SIDE_ICON_COLLISION,
    LEFT_TURN_SIGNAL_COLLISION,
    RIGHT_TURN_SIGNAL_COLLISION,
    DRIVE_MODE_COLLISION,
)
REQUIRED_OPAQUE_BACKDROP_BOXES = (
    (0, 0, GEAR_BOX[0] - 1, GEAR_BOX[3]),
    (GEAR_BOX[2] + 1, 0, WIDTH - 1, GEAR_BOX[3]),
    (0, 650, MAP_POLYGON[-1][0] - 1, HEIGHT - 1),
    (MAP_POLYGON[-2][0] + 1, 650, WIDTH - 1, HEIGHT - 1),
)
APP_CARD_BOXES = (
    (522, 12, 682, 76),
    (706, 12, 882, 76),
    (1038, 12, 1214, 76),
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


def checkerboard() -> Image.Image:
    image = Image.new("RGBA", (WIDTH, HEIGHT), (30, 49, 60, 255))
    draw = ImageDraw.Draw(image)
    cell = 48
    colours = ((35, 62, 77, 255), (54, 88, 105, 255))
    for top in range(0, HEIGHT, cell):
        for left in range(0, WIDTH, cell):
            draw.rectangle(
                (left, top, left + cell - 1, top + cell - 1),
                fill=colours[((left // cell) + (top // cell)) % 2],
            )
    return image


def create_guide() -> Image.Image:
    image = checkerboard()
    overlay = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)

    for box in REQUIRED_OPAQUE_BACKDROP_BOXES:
        draw.rectangle(
            box,
            fill=(255, 184, 64, 44),
            outline=(255, 196, 92, 230),
            width=2,
        )
    draw.polygon(
        MAP_POLYGON,
        fill=(0, 210, 155, 72),
        outline=(0, 255, 188, 255),
        width=4,
    )
    draw.rectangle(
        GEAR_BOX,
        fill=(0, 210, 155, 105),
        outline=(0, 255, 188, 255),
        width=4,
    )
    for box in SYSTEM_OVERLAY_COLLISIONS:
        draw.rectangle(
            box,
            fill=(255, 77, 70, 72),
            outline=(255, 104, 96, 255),
            width=3,
        )
    for box in APP_CARD_BOXES:
        draw.rectangle(box, fill=(76, 145, 255, 55), outline=(100, 168, 255, 230), width=2)

    image.alpha_composite(overlay)
    draw = ImageDraw.Draw(image)
    title_font = font(28)
    label_font = font(21)
    small_font = font(17)

    draw.rectangle((0, 0, WIDTH - 1, HEIGHT - 1), outline=(255, 255, 255, 180), width=2)
    draw.text((20, 14), "H9 CLUSTER — ТЕХНИЧЕСКИЙ ШАБЛОН 1920×720",
              font=font(21), fill=(255, 255, 255, 255))
    draw.text((825, 43), "ШТАТНАЯ ПЕРЕДАЧА — ПРОЗРАЧНО",
              font=small_font, fill=(215, 255, 240, 255))
    draw.text((790, 330), "КАРТА ЯНДЕКСА — ПРОЗРАЧНО",
              font=label_font, fill=(215, 255, 240, 255))
    draw.text((1190, 674), "РЕЖИМ: ОВЕРЛЕЙ, БЕЗ РАМКИ",
              font=small_font, fill=(255, 215, 210, 255))
    draw.text((30, 140), "ЗОНА ШТАТНЫХ ЗНАЧКОВ",
              font=small_font, fill=(255, 215, 210, 255))
    draw.text((1530, 140), "ЗОНА ШТАТНЫХ ЗНАЧКОВ",
              font=small_font, fill=(255, 215, 210, 255))
    draw.text((620, 14), "ПОВОРОТНИК ←",
              font=small_font, fill=(255, 215, 210, 255))
    draw.text((1184, 14), "ПОВОРОТНИК →",
              font=small_font, fill=(255, 215, 210, 255))

    legend_y = 665
    draw.rectangle((18, legend_y, 820, 710), fill=(4, 8, 11, 220))
    draw.rectangle((32, 677, 50, 695), fill=(0, 210, 155, 150))
    draw.text((60, 674), "обязательно прозрачно", font=small_font,
              fill=(235, 245, 248, 255))
    draw.rectangle((285, 677, 303, 695), fill=(255, 77, 70, 150))
    draw.text((313, 674), "не размещать важные элементы", font=small_font,
              fill=(235, 245, 248, 255))
    draw.rectangle((615, 677, 633, 695), fill=(255, 184, 64, 150))
    draw.text((643, 674), "alpha=255", font=small_font,
              fill=(235, 245, 248, 255))
    return image


def create_transparency_mask() -> Image.Image:
    image = Image.new("L", (WIDTH, HEIGHT), 0)
    draw = ImageDraw.Draw(image)
    draw.polygon(MAP_POLYGON, fill=255)
    draw.rectangle(GEAR_BOX, fill=255)
    return image


def create_collision_mask() -> Image.Image:
    image = Image.new("L", (WIDTH, HEIGHT), 0)
    draw = ImageDraw.Draw(image)
    for box in SYSTEM_OVERLAY_COLLISIONS:
        draw.rectangle(box, fill=255)
    return image


def create_opaque_backdrop_mask() -> Image.Image:
    image = Image.new("L", (WIDTH, HEIGHT), 0)
    draw = ImageDraw.Draw(image)
    for box in REQUIRED_OPAQUE_BACKDROP_BOXES:
        draw.rectangle(box, fill=255)
    return image


def create_card_guide() -> Image.Image:
    image = Image.new("L", (WIDTH, HEIGHT), 0)
    draw = ImageDraw.Draw(image)
    for box in APP_CARD_BOXES:
        draw.rectangle(box, fill=255)
    return image


def save_neutral_opacity_reference(source: Path, destination: Path) -> None:
    """Save only the alpha channel from the validated layout, never its artwork."""
    alpha = Image.open(source).convert("RGBA").getchannel("A")
    alpha.save(destination, "PNG", optimize=True)


def write_readme(destination: Path) -> None:
    destination.write_text(
        """H9 CLUSTER — НЕЙТРАЛЬНЫЙ ТЕХНИЧЕСКИЙ ШАБЛОН

Формат всех растровых файлов: 1920×720.

В архиве нет графики, текстур, шкал, стрелок, шрифтов или других элементов
текущей темы Classic.

КАК ИСПОЛЬЗОВАТЬ В ЛОКАЛЬНОМ CODEX

Сначала выполните `CODEX_LOCAL_SETUP_RU.md` из комплекта форумных инструкций.
Распакуйте этот архив в рабочую папку и выберите корень рабочей папки как
локальный проект Codex.

Для первого художественного этапа откройте отдельный поток Codex. Укажите
локальный путь к `01_technical_guide.png`. Не просите Codex одновременно
создавать Android-проект, ZIP, отдельные RGBA-слои или APK.

После утверждения одного плоского концепта:

1. Во втором локальном потоке Codex используйте концепт, этот распакованный
   архив и автономный H9 Cluster Dashboard Demo-проект. Сначала создайте и
   проверьте APK только с тестовыми значениями, без подключения к автомобилю.
2. После проверки Demo APK в третьем локальном потоке Codex используйте
   исправленный Demo-проект и исходники основного H9 Cluster. Только тогда
   подключайте реальные значения автомобиля.

СОСТАВ

00_blank_1920x720_rgba.png
    Полностью прозрачный холст для нового дизайна.

01_technical_guide.png
    Нейтральная визуальная схема зон. Это справочная картинка, её нельзя
    использовать как фон готовой темы.

02_required_transparency_mask.png
    Одноканальная маска. Белый = итоговый дизайн обязан иметь alpha=0.
    Чёрный = это требование само по себе не задаёт прозрачность.

03_system_icon_collision_mask.png
    Белый = зона возможного наложения элементов, которые автомобиль рисует
    поверх приложения: поворотники, предупреждающие значки и режим движения.
    В этой зоне нельзя размещать подписи, цифры и критически важные элементы.
    Эта маска описывает конфликт композиции, а не прозрачность.

04_optional_application_cards.png
    Подтверждённые места трёх текущих карточек приложения. Центральный
    промежуток между ними предназначен для штатной передачи. Карточки являются
    возможностью, а не обязательным элементом нового дизайна.

05_reference_opacity_envelope.png
    Только альфа-канал проверенной рабочей компоновки, без единого пикселя её
    оформления. Белый = непрозрачно, серый = градиент, чёрный = прозрачно.
    Файл нужен как техническая ссылка для маскировки штатных нижних/боковых
    показаний и сохранения видимости карты. Новый дизайн не обязан повторять
    силуэт темы, но не должен нарушать обязательную прозрачную маску.

06_required_opaque_backdrop_mask.png
    Белый = итоговая чёрная/тёмная подложка в этой зоне обязана иметь
    alpha=255. Полупрозрачный чёрный запрещён. Чёрный = эта маска сама по себе
    не задаёт непрозрачность.

ОБЯЗАТЕЛЬНЫЕ ПРАВИЛА

1. Не менять размер 1920×720 и систему координат.
2. Не закрывать белые области 02_required_transparency_mask.png.
3. Не рисовать штатную передачу, карту, режим движения и системные значки:
   их формирует автомобиль. Режим движения и системные значки выводятся поверх
   приложения, поэтому им нужна свободная зона, но не прозрачное окно.
   Надпись ECO является только одним из возможных состояний штатного режима.
   Нельзя запекать слово ECO в макет, делать под ним рамку, капсулу, выемку,
   пятно или отдельную подложку: фон должен непрерывно продолжаться под зоной.
4. Не размещать важные шкалы и подписи в белых областях
   03_system_icon_collision_mask.png.
5. Белые области 06_required_opaque_backdrop_mask.png должны иметь alpha=255
   в итоговой композиции. Полупрозрачные эффекты допустимы только поверх уже
   непрозрачного базового слоя.
6. Ненужные штатные показания должны быть закрыты новым непрозрачным боковым
   оформлением. Проверять результат поверх реального штатного лаунчера.
7. Градиент к карте применяется только к чёрной подложке. Шкалы, значки и
   контурные линии остаются отдельными слоями и не затемняются градиентом.
8. Стрелки, динамические числа, часы и показания датчиков нельзя запекать в
   статический фон.

ВАЖНО

Маска системных оверлеев учитывает наблюдавшиеся положения левого и правого
поворотников, боковых предупреждающих значков и нижнего режима движения.
Полный набор значков зависит от состояния автомобиля. Финальный макет
обязательно проверяется на автомобиле с включёнными контрольными лампами,
обоими поворотниками и несколькими режимами движения.
""",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--opacity-source", required=True, type=Path)
    args = parser.parse_args()

    output = args.output_dir
    output.mkdir(parents=True, exist_ok=True)
    Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0)).save(
        output / "00_blank_1920x720_rgba.png", "PNG", optimize=True
    )
    create_guide().save(output / "01_technical_guide.png", "PNG", optimize=True)
    create_transparency_mask().save(
        output / "02_required_transparency_mask.png", "PNG", optimize=True
    )
    create_collision_mask().save(
        output / "03_system_icon_collision_mask.png", "PNG", optimize=True
    )
    create_card_guide().save(
        output / "04_optional_application_cards.png", "PNG", optimize=True
    )
    save_neutral_opacity_reference(
        args.opacity_source, output / "05_reference_opacity_envelope.png"
    )
    create_opaque_backdrop_mask().save(
        output / "06_required_opaque_backdrop_mask.png", "PNG", optimize=True
    )
    write_readme(output / "README_RU.txt")


if __name__ == "__main__":
    main()
