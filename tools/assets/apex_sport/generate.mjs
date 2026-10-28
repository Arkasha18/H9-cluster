import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';

const require = createRequire(import.meta.url);
const sharp = require('sharp');
const { createCanvas, GlobalFonts, convertSVGTextToPath } = require('@napi-rs/canvas');
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const template = path.join(root, 'docs/H9_Cluster_Neutral_Design_Template_1920x720');
const out = path.join(here, 'renders');
const checks = path.join(root, 'artifacts/skin-review/apex-sport');
await fs.mkdir(out, { recursive: true });
await fs.mkdir(checks, { recursive: true });
for (const [file, name] of [['Rajdhani-Medium.ttf', 'Rajdhani'], ['Inter-Regular.ttf', 'Inter']]) {
  assert(GlobalFonts.registerFromPath(path.join(root, 'app/src/main/assets/fonts', file), name));
}
assert(GlobalFonts.registerFromPath(path.join(root, 'tools/assets/fonts/NotoSans.ttf'), 'Noto Sans'));
const ctx = createCanvas(1920, 720).getContext('2d');
const C = { white: '#f1f4f5', muted: '#9ba8b3', faint: '#55616a', red: '#ff493d', blue: '#46b6e6' };
const esc = s => String(s).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('"', '&quot;');
const num = x => +x.toFixed(3);
const hash = b => createHash('sha256').update(b).digest('hex');
const point = (cx, cy, r, a) => [num(cx + r * Math.cos(a * Math.PI / 180)), num(cy + r * Math.sin(a * Math.PI / 180))];
const arc = (cx, cy, r, a, b) => `M${point(cx, cy, r, a)} A${r},${r} 0 ${b - a > 180 ? 1 : 0} 1 ${point(cx, cy, r, b)}`;
const line = (x1, y1, x2, y2, color, width = 1) => `<path d="M${x1} ${y1}L${x2} ${y2}" fill="none" stroke="${color}" stroke-width="${width}"/>`;
const circle = (x, y, r, fill, stroke = 'none', sw = 1) => `<circle cx="${x}" cy="${y}" r="${r}" fill="${fill}" stroke="${stroke}" stroke-width="${sw}"/>`;
const strokeArc = (cx, cy, r, a, b, color, w) => `<path d="${arc(cx, cy, r, a, b)}" fill="none" stroke="${color}" stroke-width="${w}" stroke-linecap="round"/>`;
const defs = `<defs>
  <linearGradient id="bezel" x1=".12" y1="0" x2=".82" y2="1"><stop stop-color="#8b9499"/><stop offset=".13" stop-color="#3d444c"/><stop offset=".28" stop-color="#11191f"/><stop offset=".61" stop-color="#222b32"/><stop offset=".86" stop-color="#10151b"/><stop offset="1" stop-color="#57616a"/></linearGradient>
  <radialGradient id="face" cx=".42" cy=".25" r=".85"><stop stop-color="#202831"/><stop offset=".6" stop-color="#0c1117"/><stop offset="1" stop-color="#070b0f"/></radialGradient>
  <linearGradient id="glass" x2=".4" y2="1"><stop stop-color="#b6d8ee" stop-opacity=".08"/><stop offset=".5" stop-color="#b6d8ee" stop-opacity="0"/></linearGradient>
  <linearGradient id="rail" x2="1" y2="0"><stop stop-color="#3c4851" stop-opacity="0"/><stop offset=".2" stop-color="#69757c"/><stop offset=".8" stop-color="#69757c"/><stop offset="1" stop-color="#3c4851" stop-opacity="0"/></linearGradient>
  <linearGradient id="headerFade" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#0e141b"/><stop offset=".76" stop-color="#0e141b" stop-opacity=".9"/><stop offset="1" stop-color="#0e141b" stop-opacity="0"/></linearGradient>
</defs>`;
const svg = body => `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="1920" height="720" viewBox="0 0 1920 720">${defs}${body}</svg>`;
const textMetrics = (text, size, family = 'Noto Sans') => {
  ctx.font = `${size}px "${family}"`;
  ctx.textAlign = 'left';
  return ctx.measureText(String(text));
};
const text = (value, x, y, size = 19, color = C.white, family = 'Noto Sans') => {
  const m = textMetrics(value, size, family);
  const left = x + (m.actualBoundingBoxLeft - m.actualBoundingBoxRight) / 2;
  return `<text x="${num(left)}" y="${y}" font-family="${family}" font-size="${size}" fill="${color}" data-center-x="${x}">${esc(value)}</text>`;
};

// Pass 1: approved coordinates, with the user-requested Day/Trip row refinement.
// Styling below never changes these anchors.
const readouts = [];
function field(source, value, x, baseline, size, width, label, unit, format, noData, extras = {}) {
  const family = extras.family || 'Rajdhani';
  const box = extras.bounds || [x - width / 2, baseline - size, x + width / 2, baseline + 5];
  const m = textMetrics(value, size, family);
  const result = { source, sample: value, unit, format, bounds: box,
    anchor: [x, baseline], center: [x, (box[1] + box[3]) / 2], baseline,
    visualTextCenter: [x, num(baseline + (m.actualBoundingBoxDescent - m.actualBoundingBoxAscent) / 2)],
    font: family, fontSizePx: size, maxChars: extras.maxChars || String(value).length,
    noData, label, ...extras };
  readouts.push(result);
  return result;
}
field('speedKph', '86', 228, 349, 93, 208, 'СКОРОСТЬ', 'км/ч', 'Nonnegative whole indicated speed; dial0…240 only; numeric design envelope0…999; SpeedDisplayMode once', '—', { maxChars: 3, samples: ['0', '8', '86', '240', '999'], labelAt: [228, 285], unitAt: [228, 377] });
field('rpm', '2640', 1692, 346, 73, 214, 'ОБОРОТЫ', 'об/мин', '0…8000; shared RpmReadoutStabilizer', '—', { maxChars: 4, samples: ['0', '10', '2640', '8000'], labelAt: [1692, 285], unitAt: [1692, 377] });
field('currentGear', '6', 1000, 63, 44, 36, '', '', '1…8 only if gearSelector==D', '', { maxChars: 1, bounds: [982, 28, 1018, 66], samples: ['1', '8'] });
field('fuelLiters', '54', 466, 250, 43, 100, 'ТОПЛИВО', 'л', 'finite >=0; whole', '—', { maxChars: 3, labelAt: [466, 143], unitAt: [466, 274], samples: ['0', '54', '80'] });
field('rangeKm', '540', 466, 343, 35, 152, 'ЗАПАС ХОДА', 'км', 'integer >=0', '—', { maxChars: 4, labelAt: [466, 316], unitAt: [466, 365], samples: ['0', '9999'] });
field('coolantC', '88', 1454, 250, 43, 100, 'ДВИГАТЕЛЬ', '°C', '40…130 dial; integer numeric', '—', { maxChars: 4, labelAt: [1454, 143], unitAt: [1454, 274], samples: ['-40', '130'] });
field('transmissionTemperatureC', '76', 1454, 343, 35, 135, 'ATF / °C', '°C', 'finite; age <=15000 ms, positive timestamp <=now', '—', { maxChars: 4, labelAt: [1454, 316], samples: ['-40', '140'] });
field('outsideTemperatureC', '+12.5', 1454, 412, 31, 156, 'СНАРУЖИ / °C', '°C', 'signed one decimal; finite', '—', { maxChars: 5, labelAt: [1454, 381], samples: ['-50.0', '+60.0'] });
field('engineFlywheelTorque', '248', 1454, 475, 33, 154, 'МОМЕНТ / Н·м', 'Н·м', 'signed rounded; finite', '—', { maxChars: 5, labelAt: [1454, 444], samples: ['-2000', '2000'] });
field('instantFuelConsumption', '8.4', 145, 555, 39, 142, 'МГНОВ. РАСХОД', 'л/100 км', 'one decimal; measured speed<=1 switches to л/ч', '—', { maxChars: 5, labelAt: [145, 517], unitAt: [145, 576], samples: ['0.0', '99.9', '199.9'] });
field('consumptionLitersPer100Km', '9.7', 145, 625, 39, 142, 'СРЕДНИЙ', 'л/100 км', 'one decimal; finite >=0; not journey average', '—', { maxChars: 5, labelAt: [145, 593], unitAt: [145, 646], samples: ['0.0', '199.9'] });
field('voltage', '14.2', 145, 696, 31, 126, 'БОРТСЕТЬ / В', 'В', 'one decimal; finite >=0', '—', { maxChars: 4, labelAt: [145, 667], samples: ['0.0', '99.9'] });
field('odometerKm', '124 680', 321, 694, 28, 153, 'ODO / км', 'км', 'whole, thousands separated; finite >=0', '—', { maxChars: 9, labelAt: [321, 666], samples: ['0', '9 999 999'] });
field('dayKm', '42.8', 490, 675, 26, 88, 'DAY', 'км', 'one decimal; finite >=0', '—', { maxChars: 6, labelAt: [416, 674], unitAt: [548, 674], captionSizePx: 12, unitSizePx: 12, samples: ['0.0', '9999.9'] });
field('tripKm', '186.4', 490, 707, 26, 88, 'TRIP', 'км', 'one decimal; finite >=0', '—', { maxChars: 6, labelAt: [416, 706], unitAt: [548, 706], captionSizePx: 12, unitSizePx: 12, samples: ['0.0', '9999.9'] });
field('steeringAngleDeg', '+12°', 1640, 506, 33, 150, 'РУЛЬ', '°', 'signed rounded; finite; positive clockwise in mockup; confirm signal convention in implementation', '—', { maxChars: 6, labelAt: [1755, 504], samples: ['-1080°', '+1080°'] });
const wheels = ['FrontLeft', 'FrontRight', 'RearLeft', 'RearRight'];
const wheelTags = ['FL', 'FR', 'RL', 'RR'];
for (let i = 0; i < 4; i++) {
  field(`tyre${wheels[i]}Bar`, ['2.35', '2.36', '2.32', '2.34'][i], 1620, 571 + i * 31, 29, 110, wheelTags[i], 'bar', 'two decimals; finite >=0', '—', { maxChars: 5, samples: ['0.00', '9.99', '10.00'] });
  field(`wheel${wheels[i]}Kph`, ['86.0', '86.2', '85.9', '86.1'][i], 1764, 571 + i * 31, 29, 112, wheelTags[i], 'км/ч', 'one decimal; finite >=0', '—', { maxChars: 5, samples: ['0.0', '350.0'] });
}
field('DashboardClock.time', '14:32', 1148, 56, 35, 132, '', '', 'HH:mm; system clock', '—:—', { maxChars: 5, samples: ['00:00', '23:59'] });
field('DashboardClock.date', '28.10.2026', 788, 54, 25, 165, '', '', 'dd.MM.yyyy; system clock', '—', { maxChars: 10 });
field('WifiIndicator', 'connected', 881, 42, 0, 30, '', '', 'shared WifiIndicator: connection and signal', 'disconnected glyph', { bounds: [864, 27, 898, 57], maxChars: 0, stateSamples: ['connected', 'disconnected', 'unknown'] });
assert.equal(readouts.length, 27);
for (const f of readouts) {
  f.typography = {};
  for (const [role, value, at, size] of [['caption', f.label, f.labelAt, f.captionSizePx || 14], ['unit', f.unit, f.unitAt, f.unitSizePx || 16]]) {
    if (!at) continue;
    const m = textMetrics(value, size);
    const w = m.actualBoundingBoxLeft + m.actualBoundingBoxRight;
    f.typography[role] = { text: value, font: 'Noto Sans', fontSizePx: size, anchor: at, baseline: at[1],
      color: role === 'unit' && ['dayKm', 'tripKm'].includes(f.source) ? C.white : C.muted,
      bounds: [num(at[0] - w / 2), num(at[1] - m.actualBoundingBoxAscent), num(at[0] + w / 2), num(at[1] + m.actualBoundingBoxDescent)],
      visualCenter: [at[0], num(at[1] + (m.actualBoundingBoxDescent - m.actualBoundingBoxAscent) / 2)] };
  }
}

const scaleSpecs = [
  { source: 'speedKph', center: [228, 330], radius: 120, min: 0, max: 240, startDeg: 135, sweepDeg: 270, step: 40, minorStep: 10 },
  { source: 'rpm', center: [1692, 330], radius: 120, min: 0, max: 8000, startDeg: 135, sweepDeg: 270, step: 1000, minorStep: 250 },
  { source: 'fuelLiters', center: [466, 238], radius: 53, min: 0, max: 80, startDeg: 140, sweepDeg: 260, step: 20 },
  { source: 'coolantC', center: [1454, 238], radius: 53, min: 40, max: 130, startDeg: 140, sweepDeg: 260, step: 30 }
].map(s => ({ ...s, interpolation: 'linear angle; clockwise; clamp pointer only, do not clamp numeric reading',
  calibration: Array.from({ length: Math.floor((s.max - s.min) / s.step) + 1 }, (_, i) => {
    const value = s.min + i * s.step;
    return { value, xy: point(...s.center, s.radius, s.startDeg + (value - s.min) / (s.max - s.min) * s.sweepDeg) };
  }) }));
const layout = { name: 'APEX SPORT', status: 'User-approved layout, implemented by independent ApexSportClusterView; review data remain fictional',
  upstreamCommit: '242a66b0b51a7cac7e54a4d528f899a154238f76', canvas: [1920, 720],
  map: { noArtworkCorridor: [560, 80, 1360, 720], transparentCore: [672, 80, 1096, 720],
    base: '04_yandex_map_black_gradient_rgba.png', originalSha256: 'c21978f23e6bcbcdbb255c8ee3b0599ee4494310741eeb84deb1298ff0957260',
    workingCopyTransform: ['No alpha or RGB transform: the complete calibrated04 PNG is embedded byte-identically.',
      'Revision9.7.1 removed the y40..79 header fade; revision9.7.2 also removes the y647+ bottom fade after ghost digits were visible behind Day/Trip.',
      'The original bottom shelf and its sharp edge are intentional calibration. No mask clipping, new cover rectangle, or background regeneration is used.'] },
  typography: { centering: 'Actual glyph ink bounds, fixed baseline; font size fixed; no text clipping', fonts: ['Rajdhani-Medium.ttf', 'NotoSans.ttf'],
    revision: '2026-10-28 vehicle readability refinement: +1 logical px for every text size except the contract-fixed currentGear44 and unchanged shared WifiIndicator.',
    dayTrip: { layout: 'Two horizontal rows: caption, numeric value, unit', captionToValueInkGapMinPx: 8, valueToUnitInkGapMinPx: 4, numericCenterX: 490, numericMaxWidthPx: 88 } },
  systemFields: { overlay: '05_system_fields_overlay.png', strict: '06_lower_left_reserved_mask.png', clearancePx: 1,
    allAlphaAboveZero: true, use: 'validation only, never clipping', preserveBaseAlphaUnder06: true },
  semantics: { rpm: 'Smooth pointer; stabilized digits: >=160 ms, 10 rpm steps, hysteresis 8 rpm; display only',
    speed: 'Shared SpeedDisplayMode; numeric and arc use same indicated rounded speed; preserve 0 at stop',
    fuel: '0..80 L calibration from current GwmClusterDataSource.TANK_CAPACITY_LITERS and Classic/Sport',
    coolant: '40..130 C existing Classic/Sport scale; design marks, not newly defined vehicle warning thresholds',
    missing: 'Nonfinite/negative values on unsigned fields show dash and no pointer/fill. Signed torque/temperature/steering allow negative. Invalid gear is empty.',
    rendererScope: 'Design source for the independent registered apexsport renderer. Native Canvas and APK evidence is recorded separately in IMPLEMENTATION_RU.md and artifacts/skin-review/apex-sport/native/.',
    sampleMeaning: 'Every displayed value, clock, Wi-Fi, needle and map is fictional review data, marked МАКЕТ.' },
  refinement: { fixed: 'Numeric anchors/baselines remain at pass1 coordinates except user-requested Day/Trip horizontal reflow: numeric centerX453→490; baselines675/707 unchanged.',
    typographyOnly: ['Original speed refinement100→92, then vehicle readability revision92→93; fixed anchor(228,349)', 'Fuel-range andATF captions baseline309→316 to clear bezel; values unchanged', 'Steering caption moved alongside fixed value to clear bezel', 'Day/Trip use separate caption, value and km columns, with gaps validated for extreme samples', 'Cyrillic captions use bundled NotoSans instead of Latin-onlyInter', 'All text sizes increased by1 logical px after the stationary car review; currentGear44 and WifiIndicator unchanged'] },
  readouts, scales: scaleSpecs };
await fs.writeFile(path.join(here, 'layout.json'), JSON.stringify(layout, null, 2) + '\n');

// Pass 2: sculpture, materials and light around the anchors above.
let chrome = `<g id="decorative-chrome">`;
let critical = '<g id="scale-and-caption-layer">';
let dynamic = '<g id="mockup-readouts">';
for (const [cx, cy] of [[228, 330], [1692, 330]]) {
  chrome += circle(cx, cy, 160, '#030609') + circle(cx, cy, 157, 'url(#bezel)') + circle(cx, cy, 149, '#05090e', '#090e14', 2);
  chrome += circle(cx, cy, 145, 'url(#face)', '#48515a', 1) + circle(cx, cy, 139, 'none', '#27343e', 1);
  chrome += strokeArc(cx, cy, 154, 203, 302, '#aab9c2', 1.1) + strokeArc(cx, cy, 151, 30, 146, '#1e252c', 3);
  chrome += circle(cx, cy, 137, 'url(#glass)');
  for (const a of [45, 135, 225, 315]) {
    const [sx, sy] = point(cx, cy, 152, a);
    chrome += circle(sx, sy, 2.3, '#10161b', '#73808a', .7) + line(sx - 1.3, sy, sx + 1.3, sy, '#85909a', .6);
  }
}
for (const s of scaleSpecs.slice(0, 2)) {
  const [cx, cy] = s.center;
  const isRpm = s.source === 'rpm';
  critical += strokeArc(cx, cy, 121, 135, 405, '#57616b', 1);
  if (isRpm) critical += strokeArc(cx, cy, 120, 337.5, 405, '#d93d36', 3);
  for (let value = 0; value <= s.max; value += s.minorStep) {
    const a = 135 + value / s.max * 270;
    const major = value % s.step === 0;
    const [x1, y1] = point(cx, cy, major ? 109 : 115, a);
    const [x2, y2] = point(cx, cy, 121, a);
    critical += line(x1, y1, x2, y2, isRpm && value >= 6000 ? C.red : major ? '#e1e8eb' : '#7b8894', major ? 2 : 1);
    if (major) {
      const [tx, ty] = point(cx, cy, 97, a);
      critical += text(isRpm ? value / 1000 : value, tx, ty + 7, 22, isRpm && value >= 6000 ? C.red : C.white, 'Rajdhani');
    }
  }
  const shown = isRpm ? 2640 : 86;
  const angle = 135 + shown / s.max * 270;
  dynamic += strokeArc(cx, cy, 119, angle - 3.5, angle + 3.5, C.red, 4);
  const p1 = point(cx, cy, 115, angle), p2 = point(cx, cy, 89, angle), p3 = point(cx, cy, 114, angle + 2), p4 = point(cx, cy, 114, angle - 2);
  dynamic += `<path d="M${p1}L${p3}L${p2}L${p4}Z" fill="${C.red}"/>`;
  critical += text(isRpm ? '×1000' : 'APEX', cx, 427, 13, C.muted);
}
for (const s of scaleSpecs.slice(2)) {
  const [cx, cy] = s.center;
  const fuel = s.source === 'fuelLiters';
  chrome += circle(cx, cy, 66, '#080d12', '#3b4752', 1) + circle(cx, cy, 62, 'url(#face)');
  critical += strokeArc(cx, cy, 53, 140, 400, '#3f4a54', 4);
  critical += strokeArc(cx, cy, 53, fuel ? 140 : 358, fuel ? 173 : 400, C.red, 4);
  for (let i = 0; i <= 8; i++) {
    const a = 140 + i / 8 * 260, p = point(cx, cy, 46, a), q = point(cx, cy, 50, a);
    critical += line(...p, ...q, '#ccd5db', 1);
  }
  const a = 140 + ((fuel ? 54 : 88) - s.min) / (s.max - s.min) * 260;
  dynamic += strokeArc(cx, cy, 53, 140, a, fuel ? '#edf2f5' : C.blue, 4);
  const p = point(cx, cy, 47, a), q = point(cx, cy, 61, a);
  dynamic += line(...p, ...q, C.red, 2.2);
  critical += text(fuel ? 'E' : 'C', cx - 50, cy + 47, 13, C.muted) + text(fuel ? 'F' : 'H', cx + 50, cy + 47, 13, C.muted);
}
// Sparse rails replace rectangular cards. All terminate before reserved areas.
chrome += line(83, 491, 210, 491, 'url(#rail)') + line(83, 583, 210, 583, 'url(#rail)') + line(83, 654, 210, 654, 'url(#rail)');
chrome += line(240, 647, 537, 647, 'url(#rail)') + line(386, 662, 386, 704, '#253039');
chrome += line(1378, 523, 1810, 523, 'url(#rail)') + line(1552, 541, 1552, 669, '#33404a') + line(1692, 541, 1692, 669, '#33404a');
for (let i = 0; i < 4; i++) {
  critical += text(wheelTags[i], 1454, 571 + i * 31, 20, C.muted);
  if (i < 3) chrome += line(1407, 580 + i * 31, 1810, 580 + i * 31, '#1e2933');
}
critical += text('КОЛЕСО', 1454, 544, 13, C.muted) + text('bar', 1620, 544, 15, C.muted) + text('км/ч', 1764, 544, 15, C.muted);
for (const f of readouts) {
  if (f.source === 'WifiIndicator') continue;
  dynamic += `<g id="${esc(f.source)}" data-source="${esc(f.source)}">${text(f.sample, ...f.anchor, f.fontSizePx, C.white, f.font)}</g>`;
  if (f.labelAt) critical += text(f.label, ...f.labelAt, f.typography.caption.fontSizePx, C.muted);
  if (f.unitAt) critical += text(f.unit, ...f.unitAt, f.typography.unit.fontSizePx, f.typography.unit.color);
}
dynamic += `<g id="WifiIndicator">${strokeArc(881, 50, 15, 225, 315, C.white, 2)}${strokeArc(881, 50, 9, 225, 315, C.white, 2)}${circle(881, 50, 2, C.white)}</g>`;
dynamic += `<g id="steering-direction-clockwise">${strokeArc(1798, 497, 9, 145, 395, C.white, 1.5)}<path d="M1808 497l-3 7-5-6" fill="none" stroke="${C.white}" stroke-width="1.5"/></g>`;
critical += text('APEX', 174, 56, 32, '#d9e1e6') + text('S P O R T', 174, 77, 12, C.red);
critical += text('МАКЕТ • ВСЕ ЗНАЧЕНИЯ ДЕМОНСТРАЦИОННЫЕ', 1677, 42, 13, C.muted);
critical += text('H9  /  1920 × 720', 1692, 702, 12, C.faint);
chrome += '</g>'; critical += '</g>'; dynamic += '</g>';
const foreground = chrome + critical + dynamic;

// Keep the complete calibrated04 byte-identical, including its opaque bottom shelf.
const baseFile = await fs.readFile(path.join(template, layout.map.base));
assert.equal(hash(baseFile), layout.map.originalSha256);
const { data: baseRaw } = await sharp(baseFile).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
const base = baseFile;
const baseTag = `<image width="1920" height="720" xlink:href="data:image/png;base64,${base.toString('base64')}"/>`;
const render = async body => sharp(convertSVGTextToPath(svg(body))).png().toBuffer();
const layers = {};
for (const [name, body] of Object.entries({ chrome, critical: critical + dynamic, dynamic, foreground })) {
  layers[name] = await render(body);
  await fs.writeFile(path.join(checks, `${name}.png`), layers[name]);
}
const fontStyles = [];
for (const [file, family] of [[path.join(root, 'app/src/main/assets/fonts/Rajdhani-Medium.ttf'), 'Rajdhani'], [path.join(root, 'tools/assets/fonts/NotoSans.ttf'), 'Noto Sans']]) {
  const bytes = await fs.readFile(file);
  fontStyles.push(`@font-face{font-family:'${family}';src:url(data:font/ttf;base64,${bytes.toString('base64')}) format('truetype');}`);
}
await fs.writeFile(path.join(here, 'design.svg'), svg(baseTag + foreground).replace('<defs>', `<defs><style>${fontStyles.join('')}</style>`));
await fs.writeFile(path.join(here, 'design-outlined.svg'), convertSVGTextToPath(svg(baseTag + foreground)));
await fs.writeFile(path.join(here, 'chrome.svg'), svg(chrome));
await fs.writeFile(path.join(here, 'readouts.svg'), svg(critical + dynamic));
const wire = readouts.map(f => `<rect x="${f.bounds[0]}" y="${f.bounds[1]}" width="${f.bounds[2] - f.bounds[0]}" height="${f.bounds[3] - f.bounds[1]}" fill="none" stroke="#336779" stroke-width=".6"/>${line(f.bounds[0], f.baseline, f.bounds[2], f.baseline, '#44767e', .6)}`).join('');
await fs.writeFile(path.join(here, 'layout-wireframe.svg'), svg(baseTag + critical + dynamic + wire));
const composite = await sharp(base).composite([{ input: layers.foreground }]).png().toBuffer();
await fs.writeFile(path.join(checks, 'composition-rgba.png'), composite);
const black = await sharp({ create: { width: 1920, height: 720, channels: 4, background: '#000' } }).composite([{ input: composite }]).png().toBuffer();
await fs.writeFile(path.join(out, '01-black.png'), black);
for (const theme of ['dark', 'light']) {
  const map = (await fs.readFile(path.join(here, `map-${theme}.svg`), 'utf8')).replaceAll('Inter', 'Noto Sans');
  const mapPng = await sharp(convertSVGTextToPath(map)).png().toBuffer();
  await sharp(mapPng).composite([{ input: composite }]).png().toFile(path.join(out, theme === 'dark' ? '02-map-dark.png' : '03-map-light.png'));
}
let legend = `<rect x="598" y="201" width="722" height="281" rx="12" fill="#0b131d" fill-opacity=".96" stroke="#637482"/>`;
legend += text('ТЕХНИЧЕСКИЕ ПОЛЯ · СЛОЙ 05 · 1:1', 959, 239, 22);
legend += text('Наложен исходный PNG, включая слабую alpha.', 959, 270, 17, C.muted);
legend += `<rect x="626" y="296" width="14" height="14" fill="#ff5a5a"/>` + text('Красный: никакой добавленной графики + 1 px.', 980, 309, 19);
legend += `<rect x="626" y="335" width="14" height="14" fill="#4ed8ff"/>` + text('Голубой: декор под штатным слоем допустим.', 980, 348, 19);
legend += text('Важные цифры, подписи и указатели — вне полей.', 959, 383, 17, C.muted);
legend += text('Исключение: только номер 1…8 в штатной зоне.', 959, 412, 17, C.muted);
legend += text('Поля и легенда служат только для проверки макета.', 959, 455, 15, C.muted);
await sharp(black).composite([{ input: path.join(template, '05_system_fields_overlay.png') }, { input: await render(legend) }]).png().toFile(path.join(out, '04-technical-fields.png'));
await sharp(black).composite([{ input: await render(wire) }]).png().toFile(path.join(checks, 'layout-wireframe.png'));
let checker = '';
for (let y = 0; y < 720; y += 32) for (let x = 0; x < 1920; x += 32) checker += `<rect x="${x}" y="${y}" width="32" height="32" fill="${(x + y) / 32 % 2 ? '#b0b0b0' : '#e0e0e0'}"/>`;
await sharp(await render(checker)).composite([{ input: composite }]).png().toFile(path.join(checks, 'checkerboard.png'));

// Pixel validation is independent of SVG generation; masks never modify artwork.
const rgba = b => sharp(b).ensureAlpha().raw().toBuffer();
const fg = await rgba(layers.foreground), important = await rgba(layers.critical), comp = await rgba(composite);
const mask03 = await rgba(path.join(template, '03_system_icons_forbidden_mask.png'));
const mask06 = await sharp(path.join(template, '06_lower_left_reserved_mask.png')).extractChannel(0).raw().toBuffer();
assert.equal(mask06.filter(value => value > 0).length, 40651);
let lowerLeftOverlap = 0, clearanceOverlap = 0, criticalOverlap = 0, corridorOverlap = 0, coreAlpha = 0, preservedBaseErrors = 0;
const collisions = [];
for (let y = 0; y < 720; y++) for (let x = 0; x < 1920; x++) {
  const i = y * 1920 + x, a = fg[i * 4 + 3];
  if (mask06[i]) {
    if (a) lowerLeftOverlap++;
    if (comp[i * 4 + 3] !== baseRaw[i * 4 + 3]) preservedBaseErrors++;
  }
  if (a) {
    let near = false;
    for (let dy = -1; dy <= 1 && !near; dy++) for (let dx = -1; dx <= 1; dx++) if (x + dx >= 0 && x + dx < 1920 && y + dy >= 0 && y + dy < 720 && mask06[(y + dy) * 1920 + x + dx]) near = true;
    if (near) clearanceOverlap++;
  }
  if (important[i * 4 + 3] && mask03[i * 4 + 3] && !(x >= 982 && x < 1018 && y >= 28 && y < 66)) {
    criticalOverlap++;
    if (collisions.length < 15) collisions.push([x, y]);
  }
  if (y >= 80 && x >= 560 && x < 1360 && a) corridorOverlap++;
  if (y >= 80 && x >= 672 && x < 1096 && comp[i * 4 + 3]) coreAlpha++;
}
const fitErrors = [];
const dayTripSpacing = [];
for (const f of readouts) {
  if (!f.fontSizePx) continue;
  for (const sample of [...(f.samples || [f.sample]), f.noData].filter(Boolean)) {
    const m = textMetrics(sample, f.fontSizePx, f.font);
    const w = m.actualBoundingBoxLeft + m.actualBoundingBoxRight;
    if (w > f.bounds[2] - f.bounds[0] || f.baseline - m.actualBoundingBoxAscent < f.bounds[1] || f.baseline + m.actualBoundingBoxDescent >= f.bounds[3]) fitErrors.push({ field: f.source, sample, width: num(w) });
    if (f.source === 'dayKm' || f.source === 'tripKm') {
      const captionGap = f.anchor[0] - w / 2 - f.typography.caption.bounds[2];
      const unitGap = f.typography.unit.bounds[0] - f.anchor[0] - w / 2;
      dayTripSpacing.push({ field: f.source, sample, captionToValueInkGapPx: num(captionGap), valueToUnitInkGapPx: num(unitGap) });
      assert(captionGap >= layout.typography.dayTrip.captionToValueInkGapMinPx, `${f.source}: caption touches value`);
      assert(unitGap >= layout.typography.dayTrip.valueToUnitInkGapMinPx, `${f.source}: value touches unit`);
    }
  }
}
assert(base.equals(baseFile), 'Embedded calibrated background must remain byte-identical');
assert((await rgba(base)).equals(baseRaw), 'Every calibrated background RGBA pixel must remain unchanged');
const report = { canvas: [1920, 720], requiredReadouts: readouts.length, lowerLeftOverlap, clearanceOverlap,
  criticalOverlap, criticalCollisionExamples: collisions, corridorOverlap, coreAlpha, preservedBaseErrors, fitErrors,
  calibratedHeaderUnchanged: true, calibratedBaseUnchanged: true, dayTripSpacing,
  originalBaseSha256: hash(baseFile), embeddedBaseSha256: hash(base), foregroundSha256: hash(layers.foreground),
  coordinatesSha256: hash(Buffer.from(JSON.stringify(readouts.map(f => [f.source, f.anchor, f.bounds])))),
  limitations: ['This report validates SVG design only; native Android and APK checks are recorded separately', 'Vehicle display compositing and real data require on-device verification'] };
await fs.writeFile(path.join(here, 'validation.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report));
assert.equal(lowerLeftOverlap, 0, 'Foreground overlaps lower-left field06');
assert.equal(clearanceOverlap, 0, 'Foreground lacks one pixel margin from field06');
assert.equal(criticalOverlap, 0, 'Critical readouts overlap factory fields');
assert.equal(corridorOverlap, 0, 'Map corridor must contain no foreground');
assert.equal(coreAlpha, 0, 'Calibrated core must remain transparent');
assert.equal(preservedBaseErrors, 0, 'Original base alpha under field06 must stay unchanged');
assert.equal(fitErrors.length, 0, 'Readout text exceeds passport bounds');
