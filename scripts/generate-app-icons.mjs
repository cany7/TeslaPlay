// Raster exports of the user-supplied Tesla T; no traced or redrawn artwork.
// Requires sharp (export-time only; not an Android build dependency).
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const sharp = require('sharp');
const root = new URL('../', import.meta.url);
const resource = new URL('shared/src/main/res/', root);
const original = await readFile(new URL('drawable-nodpi/tesla_t_cool_grey.png', resource));
// Pad the short edge, never stretch. One square master drives all exports.
const image = await sharp(original).resize(512, 512, { fit: 'contain', background: '#ffffff' }).flatten({ background: '#ffffff' }).png().toBuffer();
await writeFile(new URL('drawable-nodpi/tesla_launcher_square.png', resource), image);
const colors = await readFile(new URL('values/ic_launcher_colors.xml', resource), 'utf8');
const width = 60;
const height = 60;
const background = colors.match(/<color name="ic_launcher_background">(#[0-9A-Fa-f]{6})<\/color>/)?.[1];
if (!background) throw new Error('Missing icon background color');

// Match the central 72dp shown by an adaptive launcher at rest, with the
// original transparent PNG centered inside the 108dp adaptive canvas.
const svg = (size, round = false) => `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="18 18 72 72">${round ? '<defs><clipPath id="mask"><circle cx="54" cy="54" r="36"/></clipPath></defs><g clip-path="url(#mask)">' : ''}<path fill="${background}" d="M0 0h108v108H0z"/><image x="${(108 - width) / 2}" y="${(108 - height) / 2}" width="${width}" height="${height}" href="data:image/png;base64,${image.toString('base64')}"/>${round ? '</g>' : ''}</svg>\n`;

for (const [density, size] of Object.entries({ mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 })) {
  const directory = new URL(`mipmap-${density}/`, resource);
  await mkdir(directory, { recursive: true });
  for (const round of [false, true]) {
    await sharp(Buffer.from(svg(size, round))).webp({ lossless: true })
      .toFile(fileURLToPath(new URL(`ic_launcher${round ? '_round' : ''}.webp`, directory)));
  }
}
await sharp(Buffer.from(svg(512))).png()
  .toFile(fileURLToPath(new URL('shared/src/main/ic_launcher-playstore.png', root)));
// CarPlay requires a square PNG. Preserve the artwork's aspect and alpha;
// only pad the short edge instead of stretching or cropping the supplied image.
await sharp(Buffer.from(svg(512))).png()
  .toFile(fileURLToPath(new URL('common/src/main/res/raw/ic_car_home.png', root)));
// Bake the 108dp canvas into foreground pixels. Some launchers normalize a
// layer-list's intrinsic 60dp size back to the full foreground, losing its inset.
await sharp(Buffer.from(svg(864).replace('viewBox="18 18 72 72"', 'viewBox="0 0 108 108"'))).png()
  .toFile(fileURLToPath(new URL('drawable-nodpi/tesla_launcher_foreground.png', resource)));
const mono = await sharp(original).resize(480, 480, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } }).png().toBuffer();
await sharp({ create: { width: 864, height: 864, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
  .composite([{ input: mono, gravity: 'center' }]).png()
  .toFile(fileURLToPath(new URL('drawable-nodpi/tesla_launcher_monochrome.png', resource)));
await mkdir(new URL('docs/assets/', root), { recursive: true });
await writeFile(new URL('docs/assets/app-icon.svg', root), svg(512));
console.log('Generated launcher bitmaps, Play Store icon, and SVG export from the original Tesla T PNG.');
