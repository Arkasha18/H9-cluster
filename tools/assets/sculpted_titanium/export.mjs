import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

const sharp = createRequire(import.meta.url)('sharp');
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const template = path.join(root, 'docs/H9_Cluster_Neutral_Design_Template_1920x720');
const output = path.join(root, 'app/src/main/assets/dashboard/skins/sculptedtitanium');
const source = await fs.readFile(path.join(here, 'approved_design.svg'), 'utf8');
const start = source.indexOf('<g class="ui">');
assert.ok(start > 0);
// The revision keeps the sculpted perimeter, but moves ALL typography to
// Canvas: captions and measurements now share the same compartment centres.
const removed = [...source.slice(start).matchAll(/<text\b/g)].length;
let art = source.slice(0, start)
 .replace(/<path d="M1454 462H1533M1454 530H1505M1454 598H1566"[^>]*\/>/, '')
 .replace(/<path d="M1454 462H1620M1454 530H1642M1454 598H1628"[^>]*\/>/, '');

// These are design contours, never cutouts made from a technical field mask.
function replaceGroup(svg, id, replacement) {
 const begin = svg.indexOf(`<g id="${id}">`);
 assert.ok(begin >= 0, id);
 const tags = /<\/?g\b[^>]*>/g;
 tags.lastIndex = begin;
 let depth = 0, match;
 while ((match = tags.exec(svg))) {
  depth += match[0].startsWith('</') ? -1 : 1;
  if (depth === 0) {
   const value = typeof replacement === 'function'
    ? replacement(svg.slice(begin, tags.lastIndex)) : replacement;
   return svg.slice(0, begin) + value + svg.slice(tags.lastIndex);
  }
 }
 throw new Error(`Unclosed artwork group: ${id}`);
}

const finish = `<linearGradient id="refined-metal" x1="0" y1="0" x2=".15" y2="1">
 <stop offset="0" stop-color="#d2d2c8"/><stop offset=".1" stop-color="#727c7e"/>
 <stop offset=".19" stop-color="#353d40"/><stop offset=".7" stop-color="#12181b"/>
 <stop offset=".92" stop-color="#40494b"/><stop offset="1" stop-color="#bcaa83"/>
 </linearGradient>
 <linearGradient id="refined-glass" x1="0" y1="0" x2="0" y2="1">
 <stop offset="0" stop-color="#272e32"/><stop offset=".45" stop-color="#11191e"/>
 <stop offset="1" stop-color="#070d11"/></linearGradient>
 <linearGradient id="edge-fade" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="1920" y2="0">
 <stop offset="0" stop-color="white"/><stop offset=".272916667" stop-color="white"/>
 <stop offset=".291666667" stop-color="black"/><stop offset=".708333333" stop-color="black"/>
 <stop offset=".727083333" stop-color="white"/><stop offset="1" stop-color="white"/>
 </linearGradient>
 <linearGradient id="header-fade" gradientUnits="userSpaceOnUse" x1="0" y1="68" x2="0" y2="80">
 <stop offset="0" stop-color="white"/><stop offset="1" stop-color="white" stop-opacity="0"/></linearGradient>
 <linearGradient id="bridge-feather" gradientUnits="userSpaceOnUse" x1="710" y1="0" x2="1210" y2="0">
 <stop offset="0" stop-color="white" stop-opacity="0"/>
 <stop offset=".1" stop-color="white"/><stop offset=".9" stop-color="white"/>
 <stop offset="1" stop-color="white" stop-opacity="0"/></linearGradient>
 <mask id="bridge-cleanup" maskUnits="userSpaceOnUse" x="710" y="0" width="500" height="80">
 <rect x="710" width="500" height="80" fill="url(#bridge-feather)"/></mask>
 <mask id="design-edge-feather" maskUnits="userSpaceOnUse" x="0" y="0" width="1920" height="720">
 <rect width="1920" height="720" fill="url(#edge-fade)"/>
 <rect width="1920" height="80" fill="url(#header-fade)"/></mask>`;
art = art.replace('</defs>', finish + '</defs>');
art = replaceGroup(art, 'top-bridge', original => original + `<g id="clean-top-bridge" mask="url(#bridge-cleanup)">
 <path d="M710 35Q734 46 764 48H1158Q1190 49 1210 34V78H710Z" fill="#090e11"/>
 <path d="M714 44Q735 58 766 58H1158Q1186 58 1205 44" fill="none" stroke="url(#refined-metal)" stroke-width="3"/>
 <path d="M714 46Q735 61 766 61H1158Q1186 61 1205 46" fill="none" stroke="#8a5228" stroke-width="5" opacity=".3"/>
 <path d="M714 46Q735 61 766 61H1158Q1186 61 1205 46" fill="none" stroke="url(#amber)" stroke-width="1.5"/>
 </g>`);
art = replaceGroup(art, 'central-gear-saddle', `<g id="central-gear-saddle">
 <path d="M935 11H1034Q1045 11 1050 22L1067 64Q1071 75 1057 76H912Q899 76 903 64L918 23Q922 11 935 11Z" fill="url(#refined-metal)"/>
 <path d="M937 15H1032Q1042 15 1046 25L1061 64Q1064 71 1055 71H914Q906 71 909 64L923 25Q927 15 937 15Z" fill="url(#refined-glass)"/>
 <path d="M935 14H1033Q1043 14 1047 24" fill="none" stroke="#d8d9cc" stroke-width="1" opacity=".65"/>
 <path d="M914 73H1055" stroke="url(#amber)" stroke-width="1.2"/>
 </g>`);
art = art.replace('<g id="designer-artwork">', '<g id="designer-artwork" mask="url(#design-edge-feather)">');
const staticSvg = art + '</svg>';
assert.ok(!/<text\b/.test(staticSvg), 'Baked captions or sample readings must not ship');
await fs.mkdir(output, {recursive:true});
const chrome = await sharp(Buffer.from(staticSvg), {density:72}).png().toBuffer();
const {data,info} = await sharp(chrome).ensureAlpha().raw().toBuffer({resolveWithObject:true});
assert.equal(info.width,1920); assert.equal(info.height,720);
const mask = await sharp(path.join(template,'03_system_icons_forbidden_mask.png')).ensureAlpha().raw().toBuffer();
let corridor = 0, lowerLeft = 0;
for (let y=0;y<720;y++) for(let x=0;x<1920;x++) {
 if(!data[(y*1920+x)*4+3]) continue;
 if(x>=560&&x<1360&&y>=80) corridor++;
 const reserved = (x>=387&&x<=497&&y>=373&&y<=441)
  || (x>=230&&x<=547&&y>=494&&y<=641);
 if(reserved&&mask[(y*1920+x)*4+3]) lowerLeft++;
}
assert.equal(corridor,0); assert.equal(lowerLeft,0);
const base = await fs.readFile(path.join(template,'04_yandex_map_black_gradient_rgba.png'));
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
assert.equal(hash(base),'c21978f23e6bcbcdbb255c8ee3b0599ee4494310741eeb84deb1298ff0957260');
await fs.writeFile(path.join(output,'chrome.png'),chrome);
await fs.writeFile(path.join(output,'map_black_gradient.png'),base);
await fs.writeFile(path.join(here,'refined_artwork.svg'),staticSvg);
const report = {size:[1920,720],removedTextElements:removed,lowerLeftOverlap:lowerLeft,
 centralCorridorOverlap:corridor,baseSha256:hash(base),chromeSha256:hash(chrome)};
await fs.writeFile(path.join(here,'export_validation.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report));
