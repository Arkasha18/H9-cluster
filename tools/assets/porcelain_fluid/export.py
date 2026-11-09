"""Prepare a native 1920x720 material layer; technical masks are validation only."""
from pathlib import Path
import hashlib
import json
import shutil

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUT = ROOT / 'app/src/main/assets/dashboard/skins/porcelainfluid'
OUT.mkdir(parents=True, exist_ok=True)
SOURCE = HERE / 'clean-material-source.png'
original = Image.open(SOURCE).convert('RGBA')
source = original.resize((1920, 720), Image.Resampling.LANCZOS)
# The left well is narrower for the map and has a taller black lower cavity.
# These are material-coordinate transforms, not cuts based on a technical mask.
knots = [(0, 0), (220, 220), (360, 344), (440, 440),
         (540, 540), (600, 650), (690, 706), (720, 720)]
slopes = [(b[1] - a[1]) / (b[0] - a[0]) for a, b in zip(knots, knots[1:])]
tangents = [slopes[0]] + [2 * a * b / (a + b) for a, b in zip(slopes, slopes[1:])] + [slopes[-1]]
def map_y(y):
    for i, ((x0, y0), (x1, y1)) in enumerate(zip(knots, knots[1:])):
        if y <= x1:
            h = x1 - x0
            t = (y - x0) / h
            return (2*t**3-3*t*t+1)*y0 + (t**3-2*t*t+t)*h*tangents[i] + (-2*t**3+3*t*t)*y1 + (t**3-t*t)*h*tangents[i+1]
    return y
def inverse_y(y):
    lo, hi = 0., 720.
    for _ in range(22):
        mid = (lo + hi) / 2
        if map_y(mid) < y:
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2
mesh = []
for target_top in range(720):
    source_top, source_bottom = inverse_y(target_top), inverse_y(target_top + 1)
    mesh.append(((0, target_top, 864, target_top + 1),
                 (0, source_top, 0, source_bottom, 960, source_bottom, 960, source_top)))
mesh.append(((960, 0, 1920, 720), (960, 0, 960, 720, 1920, 720, 1920, 0)))
chrome = source.transform((1920, 720), Image.Transform.MESH, mesh, Image.Resampling.BICUBIC)
# Global soft alpha remapping removes 1..6/255 generative/resampling haze.
# Applied equally to the entire sprite, with no map/reservation mask input.
chrome.putalpha(chrome.getchannel('A').point(lambda a: round(max(0, a - 6) * 255 / 249)))
chrome.save(OUT / 'chrome.png')
material = Image.new('RGBA', chrome.size)
pixels = []
for r, g, b, a in chrome.getdata():
    light = (r + g + b) / 3
    chroma = max(r, g, b) - min(r, g, b)
    coverage = max(0, min(1, (light - 105) / 120)) * max(0, 1 - chroma / 90)
    pixels.append((255, 255, 255, round(a * coverage)))
material.putdata(pixels)
material.save(OUT / 'pearl-material.png')
template = ROOT / 'docs/H9_Cluster_Neutral_Design_Template_1920x720'
calibration = template / '04_yandex_map_black_gradient_rgba.png'
shutil.copy2(calibration, OUT / 'map_black_gradient.png')

from fontTools.ttLib import TTFont
fonts = HERE / 'fonts'
for name in ('PFDisplay', 'PFData', 'PFLabel'):
    font = TTFont(fonts / (name + '.woff'))
    font.flavor = None
    font.save(OUT / (name + '.ttf'))
    shutil.copy2(fonts / (name + '-OFL.txt'), OUT / (name + '-OFL.txt'))

review = Image.new('RGBA', chrome.size, (0, 0, 0, 255))
review.alpha_composite(chrome)
review.convert('RGB').save(HERE / 'material-on-black.png')
report = {
    'canvas': [1920, 720], 'left_x_scale': 0.9, 'left_y_knots': knots,
    'source': SOURCE.name, 'source_sha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
    'calibration_sha256': hashlib.sha256(calibration.read_bytes()).hexdigest(),
    'alpha_source': 'generated sprite; globally remapped max(0,a-6)*255/249 to remove subvisible haze',
    'y_interpolation': 'monotone cubic Hermite with harmonic-mean interior tangents',
    'technical_masks_used_for_export': False,
}
(HERE / 'asset-transform.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report))
