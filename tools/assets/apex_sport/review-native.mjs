import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';

const require = createRequire(import.meta.url);
const sharp = require('sharp');
const { GlobalFonts, convertSVGTextToPath } = require('@napi-rs/canvas');
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const nativeDir = process.argv[2];
assert(nativeDir, 'Pass the real Robolectric build/reports/apexsport directory');
const output = process.argv[3] ? path.resolve(process.argv[3])
  : path.join(root, 'artifacts/skin-review/apex-sport/native');
await fs.mkdir(output, { recursive: true });
GlobalFonts.registerFromPath(path.join(root, 'tools/assets/fonts/NotoSans.ttf'), 'Noto Sans');
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const svg = body => `<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="720">${body}</svg>`;
const label = (s, x, y, size = 18, fill = '#dbe5eb') => `<text x="${x}" y="${y}" text-anchor="middle" font-family="Noto Sans" font-size="${size}" fill="${fill}">${s}</text>`;
const stamp = await sharp(convertSVGTextToPath(svg(label('МАКЕТ • РЕНДЕР ANDROID CANVAS / ROBOLECTRIC', 1677, 42, 13, '#9ba8b3')))).png().toBuffer();
const frame = await fs.readFile(path.join(nativeDir, 'native-motion.png'));
const meta = await sharp(frame).metadata();
assert.equal(meta.width, 1920); assert.equal(meta.height, 720);
assert(meta.hasAlpha);
for (const name of await fs.readdir(nativeDir)) if (name.endsWith('.png')) await fs.copyFile(path.join(nativeDir, name), path.join(output, name));
const black = await sharp({ create: { width: 1920, height: 720, channels: 4, background: '#000' } }).composite([{ input: frame }, { input: stamp }]).png().toBuffer();
await fs.writeFile(path.join(output, '01-black.png'), black);
const demoLayer = path.join(nativeDir, 'demo-system-layer.png');
try {
  await fs.access(demoLayer);
  await sharp({ create: { width: 1920, height: 720, channels: 4, background: '#000' } })
    .composite([{ input: frame }, { input: demoLayer }]).png().toFile(path.join(output, '06-demo-upper-layer.png'));
} catch (error) {
  if (error.code !== 'ENOENT') throw error;
}
for (const theme of ['dark', 'light']) {
  const map = (await fs.readFile(path.join(here, `map-${theme}.svg`), 'utf8')).replaceAll('Inter', 'Noto Sans');
  const png = await sharp(convertSVGTextToPath(map)).png().toBuffer();
  await sharp(png).composite([{ input: frame }, { input: stamp }]).png().toFile(path.join(output, theme === 'dark' ? '02-map-dark.png' : '03-map-light.png'));
}
let legend = '<rect x="598" y="201" width="722" height="281" rx="12" fill="#0b131d" fill-opacity=".96" stroke="#637482"/>';
legend += label('ANDROID CANVAS · ТЕХНИЧЕСКИЕ ПОЛЯ 05 · 1:1', 959, 239, 20);
legend += label('Исходный PNG поверх нативного View, без вырезания.', 959, 270, 16, '#9ba8b3');
legend += '<rect x="626" y="296" width="14" height="14" fill="#ff5a5a"/>' + label('Красный: никакой добавленной графики + 1 px.', 980, 309);
legend += '<rect x="626" y="335" width="14" height="14" fill="#4ed8ff"/>' + label('Голубой: декор под штатным слоем допустим.', 980, 348);
legend += label('Важные показания — вне полей; исключение: номер 1…8.', 959, 387, 16, '#9ba8b3');
legend += label('Слой 05, карты и эта легенда не входят в ресурсы скина.', 959, 420, 15, '#9ba8b3');
legend += label('Демонстрационные данные. Без запуска эмулятора.', 959, 455, 14, '#9ba8b3');
await sharp(black).composite([
  { input: path.join(root, 'docs/H9_Cluster_Neutral_Design_Template_1920x720/05_system_fields_overlay.png') },
  { input: await sharp(convertSVGTextToPath(svg(legend))).png().toBuffer() }
]).png().toFile(path.join(output, '04-technical-fields.png'));
let checker = '';
for (let y = 0; y < 720; y += 32) for (let x = 0; x < 1920; x += 32) checker += `<rect x="${x}" y="${y}" width="32" height="32" fill="${(x + y) / 32 % 2 ? '#b0b0b0' : '#e0e0e0'}"/>`;
await sharp(Buffer.from(svg(checker))).composite([{ input: frame }, { input: stamp }]).png().toFile(path.join(output, '05-checkerboard.png'));
const approved = await sharp(path.join(here, 'renders/01-black.png')).ensureAlpha().raw().toBuffer();
const actual = await sharp(black).ensureAlpha().raw().toBuffer();
let difference = 0, count = 0, changed = 0;
const diff = Buffer.alloc(1920 * 720 * 4);
for (let y = 0; y < 720; y++) for (let x = 0; x < 1920; x++) {
  // Shared native WifiIndicator and the explicitly review-only stamp intentionally differ.
  const exclude = (x >= 850 && x < 912 && y < 80) || (x >= 1400 && y < 80);
  const i = (y * 1920 + x) * 4;
  let maximum = 0;
  for (let c = 0; c < 3; c++) {
    const delta = Math.abs(approved[i + c] - actual[i + c]);
    if (!exclude) { difference += delta; count++; maximum = Math.max(maximum, delta); }
  }
  if (!exclude && maximum > 20) changed++;
  diff[i] = Math.min(255, maximum * 4); diff[i + 3] = 255;
}
await sharp(diff, { raw: { width: 1920, height: 720, channels: 4 } }).png().toFile(path.join(output, 'comparison-diff-x4.png'));
const panels = [await fs.readFile(path.join(here, 'renders/01-black.png')), black];
await sharp({ create: { width: 1920, height: 1440, channels: 4, background: '#000' } }).composite(panels.map((input, i) => ({ input, top: i * 720, left: 0 }))).png().toFile(path.join(output, 'comparison-design-above-native-below.png'));
const report = { input: 'Unmodified PNG emitted by ApexSportClusterView.draw(Canvas) in native Robolectric SDK28',
  nativeMotionSha256: hash(frame), canvas: [1920, 720], meanAbsoluteRgbDifference: difference / count,
  pixelsWithDifferenceAbove20: changed, exclusions: ['Shared native WifiIndicator uses its real geometry', 'Review-only mockup caption'],
  interpretation: 'Android and SVG rasterization differ; numerical metrics supplement visual and independent geometry/alpha tests, not pixel identity.',
  previews: ['01-black.png', '02-map-dark.png', '03-map-light.png', '04-technical-fields.png'],
  mapsAndLegendOnlyInReview: true, emulatorRun: false };
await fs.writeFile(path.join(output, 'native-comparison.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report));
