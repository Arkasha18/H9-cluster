import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { createHash } from 'node:crypto';

const sharp = createRequire(import.meta.url)('sharp');
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const template = path.join(root, 'docs/H9_Cluster_Neutral_Design_Template_1920x720');
const output = path.join(root, 'app/src/main/assets/dashboard/skins/apexsport');
const hash = b => createHash('sha256').update(b).digest('hex');
const chromeSvg = await fs.readFile(path.join(here, 'chrome.svg'), 'utf8');
assert(!/<(?:text|image|mask|clipPath)\b/.test(chromeSvg), 'Static artwork must contain shapes only, without baked readouts or mask clipping');
const chrome = await sharp(Buffer.from(chromeSvg)).png().toBuffer();
const { data, info } = await sharp(chrome).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
assert.equal(info.width, 1920);
assert.equal(info.height, 720);
const strict = await sharp(path.join(template, '06_lower_left_reserved_mask.png')).extractChannel(0).raw().toBuffer();
let protectedPixels = 0, overlaps = 0, corridor = 0;
for (let y = 0; y < 720; y++) for (let x = 0; x < 1920; x++) {
  const i = y * 1920 + x;
  if (strict[i]) protectedPixels++;
  if (!data[i * 4 + 3]) continue;
  if (y >= 80 && x >= 560 && x < 1360) corridor++;
  for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
    if (x + dx < 0 || x + dx >= 1920 || y + dy < 0 || y + dy >= 720) continue;
    if (strict[(y + dy) * 1920 + x + dx]) overlaps++;
  }
}
assert.equal(protectedPixels, 40651);
assert.equal(overlaps, 0);
assert.equal(corridor, 0);
const base = await fs.readFile(path.join(template, '04_yandex_map_black_gradient_rgba.png'));
assert.equal(hash(base), 'c21978f23e6bcbcdbb255c8ee3b0599ee4494310741eeb84deb1298ff0957260');
const font = await fs.readFile(path.join(root, 'tools/assets/fonts/NotoSans.ttf'));
await fs.mkdir(output, { recursive: true });
await fs.writeFile(path.join(output, 'chrome.png'), chrome);
await fs.writeFile(path.join(output, 'map_black_gradient.png'), base);
await fs.writeFile(path.join(output, 'NotoSans.ttf'), font);
const report = { canvas: [1920, 720], protectedPixels, overlapsIncludingOnePixelMargin: overlaps,
  mapCorridorOverlap: corridor, assets: { 'chrome.png': hash(chrome), 'map_black_gradient.png': hash(base), 'NotoSans.ttf': hash(font) },
  content: 'Only static housings/rails, immutable original calibration and font. All captions, scales, values, clock, Wifi and pointers are drawn by Android Canvas.' };
await fs.writeFile(path.join(here, 'export-validation.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report));
