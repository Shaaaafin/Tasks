const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');

const root = path.resolve(__dirname, '..');
const previews = path.join(root, 'docs', 'previews');
const sourceDir = path.join(root, 'docs', 'store-assets', 'svg');
const images = path.join(root, 'fastlane', 'metadata', 'android', 'en-US', 'images');
const screens = path.join(images, 'phoneScreenshots');
for (const directory of [sourceDir, screens]) fs.mkdirSync(directory, { recursive: true });

const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const asset = name => `data:image/png;base64,${fs.readFileSync(path.join(previews, name)).toString('base64')}`;
const palettes = {
  mint: { bg: '#E5F0E8', ink: '#173A2D', muted: '#52695D', accent: '#28765B', shape: '#B7D7C3' },
  forest: { bg: '#172F27', ink: '#F1F3E9', muted: '#BDD0C2', accent: '#A8D6BD', shape: '#315944' },
  cream: { bg: '#F4EFE5', ink: '#302D24', muted: '#696454', accent: '#366F58', shape: '#D8DFCA' },
  charcoal: { bg: '#202825', ink: '#F2F2E9', muted: '#C1CCC2', accent: '#A6D5BD', shape: '#3B5041' }
};

function text(value, x, y, size, color, weight = 400, extra = '') {
  return `<text x="${x}" y="${y}" font-size="${size}" fill="${color}" font-weight="${weight}" ${extra}>${escape(value)}</text>`;
}

function lines(values, x, y, size, color, weight, lineHeight) {
  return values.map((value, index) => text(value, x, y + index * lineHeight, size, color, weight)).join('');
}

function mark(x, y, size, top, lower) {
  return `<g transform="translate(${x} ${y}) scale(${size / 64})">
    <circle cx="7" cy="8" r="7" fill="${top}"/>
    <rect x="22" y="1" width="41" height="14" rx="7" fill="${top}"/>
    <rect x="12" y="25" width="41" height="14" rx="7" fill="${lower}"/>
    <rect x="0" y="49" width="41" height="14" rx="7" fill="${lower}"/>
  </g>`;
}

function image(name, x, y, width, height, extra = '') {
  return `<image href="${asset(name)}" x="${x}" y="${y}" width="${width}" height="${height}" preserveAspectRatio="xMidYMid meet" ${extra}/>`;
}

function phone(name, x = 250, y = 514, width = 580) {
  const height = width * 2341 / 1081;
  const id = `phone-${name.replaceAll('.', '-')}-${x}`;
  return `<defs><clipPath id="${id}"><rect x="${x}" y="${y}" width="${width}" height="${height}" rx="26"/></clipPath></defs>
    <rect x="${x - 14}" y="${y + 12}" width="${width + 28}" height="${height + 28}" rx="43" fill="#06120D" opacity=".12" filter="url(#shadow)"/>
    <rect x="${x - 10}" y="${y - 10}" width="${width + 20}" height="${height + 20}" rx="39" fill="#303D36"/>
    <g clip-path="url(#${id})">${image(name, x, y, width, height)}</g>
    <rect x="${x - 3}" y="${y - 3}" width="${width + 6}" height="${height + 6}" rx="29" fill="none" stroke="#FFFFFF" stroke-opacity=".15" stroke-width="2"/>`;
}

function crop(name, x, y, width, sourceX, sourceY, sourceWidth, sourceHeight, radius = 30) {
  const scale = width / sourceWidth;
  const height = sourceHeight * scale;
  const id = `crop-${name.replaceAll('.', '-')}-${x}`;
  return `<defs><clipPath id="${id}"><rect x="${x}" y="${y}" width="${width}" height="${height}" rx="${radius}"/></clipPath></defs>
    <rect x="${x}" y="${y + 15}" width="${width}" height="${height}" rx="${radius}" fill="#06120D" opacity=".16" filter="url(#shadow)"/>
    <g clip-path="url(#${id})">${image(name, x - sourceX * scale, y - sourceY * scale, 1081 * scale, 2341 * scale)}</g>`;
}

function chip(value, x, y, width, palette) {
  return `<rect x="${x}" y="${y}" width="${width}" height="54" rx="27" fill="${palette.ink}" fill-opacity=".07"/>${text(value, x + width / 2, y + 35, 24, palette.ink, 600, 'text-anchor="middle"')}`;
}

const slides = [
  { id: 1, name: 'Overview', palette: 'mint', kicker: 'THE BIG PICTURE', title: ['Everything,', 'in one view.'], description: ['All your lists, from undated plans', 'to Today, Tomorrow, and beyond.'], footer: 'Offline first. No account required.', sources: ['home-light.png'], content: () => phone('home-light.png') },
  { id: 2, name: 'Subtasks', palette: 'forest', kicker: 'ONE STEP AT A TIME', title: ['Big plans.', 'Smaller steps.'], description: ['Add subtasks inline. Keep completed', 'steps in sight as you work through a task.'], footer: 'Subtasks · Tags · Priorities', sources: ['subtasks-inline-dark.png'], content: () => phone('subtasks-inline-dark.png') },
  { id: 3, name: 'Repeat schedules', palette: 'cream', kicker: 'MAKE ROOM FOR ROUTINE', title: ['Repeat at', 'your rhythm.'], description: ['Choose days, intervals, and when to stop.', 'Daily, weekly, or a schedule of your own.'], footer: 'Start dates · End dates · Repeat limits', sources: ['repeat-weekly-dark.png'], content: () => crop('repeat-weekly-dark.png', 175, 514, 730, 0, 485, 1081, 1856, 48) },
  { id: 4, name: 'Custom tabs', palette: 'charcoal', kicker: 'MAKE IT PERSONAL', title: ['A little more', 'you.'], description: ['Give every list its own color and symbol.', 'An icon, an emoji, a little personality.'], footer: 'Custom colors · Icons · Emoji', sources: ['tab-customization-dark.png'], content: () => crop('tab-customization-dark.png', 145, 514, 790, 0, 642, 1081, 1699, 52) },
  { id: 5, name: 'My order', palette: 'mint', kicker: 'PUT FIRST THINGS FIRST', title: ['Your tasks.', 'Your order.'], description: ['Choose My order, then hold and drag.', 'Move a task and its subtasks together.'], footer: 'My order · Date · Priority · Star · Title', sources: ['home-custom-dark.png', 'family-lift-held-dark.png'], content: palette => phone('home-custom-dark.png', 100, 535, 525) + crop('family-lift-held-dark.png', 422, 1000, 574, 45, 145, 990, 430, 32) + `<path d="M925 1352v138m-18-20 18 20 18-20M925 1352l-18 20m18-20 18 20" fill="none" stroke="${palette.accent}" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>` + text('Hold. Lift. Move.', 800, 1560, 27, palette.ink, 600, 'text-anchor="middle"') + text('Task + subtasks', 800, 1605, 25, palette.muted, 400, 'text-anchor="middle"') },
  { id: 6, name: 'Three themes', palette: 'cream', kicker: 'A COMFORTABLE VIEW', title: ['Light. Dark.', 'Just right.'], description: ['Choose light, shaded dark, or AMOLED.', 'Let wallpaper colors add your own touch.'], footer: 'Three themes. One familiar layout.', sources: ['home-light.png', 'home-dark.png', 'home-amoled.png'], content: palette => phone('home-light.png', 42, 770, 316) + phone('home-dark.png', 382, 610, 316) + phone('home-amoled.png', 722, 770, 316) + text('Light', 200, 1530, 29, palette.ink, 600, 'text-anchor="middle"') + text('Shaded dark', 540, 1370, 29, palette.ink, 600, 'text-anchor="middle"') + text('AMOLED', 880, 1530, 29, palette.ink, 600, 'text-anchor="middle"') + chip('Wallpaper colors', 374, 1660, 332, palette) },
  { id: 7, name: 'Home-screen widget', palette: 'forest', kicker: 'RIGHT WHERE YOU NEED IT', title: ['A little widget.', 'A lot done.'], description: ['View, add, and complete from home.', 'Switch lists inside the widget.'], footer: 'All · Starred · Your lists · Scroll to see more', sources: ['store-widget-light.png', 'store-widget-picker-dark.png'], content: palette => `<rect x="80" y="548" width="920" height="1110" rx="70" fill="${palette.shape}" opacity=".44"/>` + image('store-widget-light.png', 120, 585, 840, 924) + `<rect x="383" y="1335" width="600" height="288" rx="42" fill="#06120D" opacity=".28" filter="url(#shadow)"/>` + image('store-widget-picker-dark.png', 380, 1320, 600, 288) + text('Your lists, one tap away.', 120, 1730, 31, palette.ink, 600) },
  { id: 8, name: 'CalDAV sync controls', palette: 'charcoal', kicker: 'STAY IN CONTROL', title: ['Sync on', 'your terms.'], description: ['Use CalDAV while you use the app,', 'only when charging, or manually.'], footer: 'Optional CalDAV · Your choice of server', sources: ['settings-footer-dark.png'], content: palette => `<rect x="80" y="568" width="920" height="1030" rx="62" fill="${palette.shape}" opacity=".42"/>` + crop('settings-footer-dark.png', 112, 620, 856, 32, 245, 1017, 1030, 34) + chip('While using', 90, 1660, 280, palette) + chip('Charging', 394, 1660, 264, palette) + chip('Manual', 682, 1660, 264, palette) }
];

function svg(width, height, body) {
  return `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><defs><filter id="shadow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="18"/></filter></defs><g font-family="Segoe UI, DejaVu Sans, sans-serif">${body}</g></svg>`;
}

function slideSvg(slide) {
  const palette = palettes[slide.palette];
  const decoration = `<circle cx="1110" cy="1470" r="615" fill="${palette.shape}" opacity=".43"/><circle cx="-140" cy="770" r="335" fill="none" stroke="${palette.shape}" stroke-width="2" opacity=".7"/>`;
  const header = mark(80, 67, 47, palette.accent, palette.ink) + text('Tasks', 148, 105, 38, palette.ink, 650) + text('NATIVE ANDROID', 1000, 100, 19, palette.muted, 600, 'text-anchor="end" letter-spacing="3"');
  const title = text(slide.kicker, 82, 181, 21, palette.accent, 700, 'letter-spacing="3.1"') + lines(slide.title, 76, 280, 90, palette.ink, 700, 97) + lines(slide.description, 82, 431, 30, palette.muted, 400, 43);
  const footer = `<line x1="80" y1="1812" x2="1000" y2="1812" stroke="${palette.ink}" stroke-opacity=".18"/>` + text(slide.footer, 80, 1855, 24, palette.ink, 600) + text('Native UI render · Sample data', 80, 1895, 18, palette.muted) + text(String(slide.id).padStart(2, '0'), 1000, 1895, 21, palette.muted, 600, 'text-anchor="end"');
  return svg(1080, 1920, `<rect width="1080" height="1920" fill="${palette.bg}"/>${decoration}${header}${title}${slide.content(palette)}${footer}`);
}

async function save(name, source, output) {
  fs.writeFileSync(path.join(sourceDir, `${name}.svg`), source);
  await sharp(Buffer.from(source)).removeAlpha().png({ compressionLevel: 9 }).toFile(output);
}

async function main() {
  for (const slide of slides) {
    await save(String(slide.id).padStart(2, '0'), slideSvg(slide), path.join(screens, `${slide.id}.png`));
  }
  const banner = svg(1024, 500,
    `<rect width="1024" height="500" fill="#E5F0E8"/><circle cx="1000" cy="400" r="350" fill="#B7D7C3" opacity=".7"/>` +
    mark(56, 46, 39, '#28765B', '#173A2D') + text('Tasks', 112, 78, 33, '#173A2D', 650) +
    lines(['Make room for', 'what matters.'], 51, 193, 59, '#173A2D', 700, 66) +
    text('Private tasks. A clearer day.', 55, 337, 26, '#52695D') + text('Offline first  ·  Optional CalDAV', 55, 411, 23, '#173A2D', 600) +
    phone('home-light.png', 648, 78, 258) + text('Native UI render · Sample data', 55, 469, 14, '#52695D'));
  await save('featureGraphic', banner, path.join(images, 'featureGraphic.png'));

  let sheet = '<rect width="1800" height="1800" fill="#E7E9E1"/>' + text('Tasks / Store gallery', 34, 68, 34, '#173A2D', 650);
  for (const slide of slides) {
    const x = 30 + ((slide.id - 1) % 4) * 445;
    const y = 106 + Math.floor((slide.id - 1) / 4) * 838;
    const png = fs.readFileSync(path.join(screens, `${slide.id}.png`)).toString('base64');
    sheet += `<image href="data:image/png;base64,${png}" x="${x}" y="${y}" width="420" height="747"/>` + text(`${String(slide.id).padStart(2, '0')} / ${slide.name}`, x, y + 781, 21, '#173A2D', 600);
  }
  await sharp(Buffer.from(svg(1800, 1800, sheet))).removeAlpha().png({ compressionLevel: 9 }).toFile(path.join(root, 'docs', 'store-assets', 'overview.png'));
  fs.writeFileSync(path.join(root, 'docs', 'store-assets', 'captions.json'), JSON.stringify(slides.map(({ content, ...slide }) => slide), null, 2) + '\n');
  console.log('Rendered eight 1080 × 1920 store images, a 1024 × 500 feature graphic, and a gallery overview.');
}

main().catch(error => { console.error(error); process.exitCode = 1; });
