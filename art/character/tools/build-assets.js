// Character asset generator — rebuilds every geo/texture/animation file and the Outfit enum.
// Run inside the project with run_script:
//   const src = await readFile('tools/build-assets.js');
//   await new (Object.getPrototypeOf(async()=>{}).constructor)('ctx', src)({readImage, createCanvas, saveFile, log});
// Coordinates are Bedrock geometry space (what Blockbench/GeckoLib read): pixels, y-up, feet at y=0,
// the character faces -Z, and her right side is -X.
const { readImage, createCanvas, saveFile, log } = ctx;
const MODID = 'companion', NAME = 'girlfriend', PKG = 'com.example.companion';
const RES = `mod/src/main/resources/assets/${MODID}`;

// ---------- her existing skin: face, eyes, hair, hoodie and legs are sampled from it ----------
const img = await readImage('uploads/existing_model-1792261092376-jmcf.png');
const cv0 = createCanvas(64, 64); const g0 = cv0.getContext('2d'); g0.drawImage(img, 0, 0);
const SRC = g0.getImageData(0, 0, 64, 64).data;
const src = (x, y) => { const o = (y * 64 + x) * 4; return SRC[o + 3] ? [SRC[o], SRC[o + 1], SRC[o + 2]] : null; };
const K = c => (c ? c.join(',') : '');

const P = {
  sk: [255, 255, 250], sk2: [255, 247, 248], sk3: [250, 236, 239],
  h0: [245, 238, 238], h1: [243, 233, 233], h2: [240, 228, 225], h3: [234, 217, 217], h4: [229, 210, 210], h5: [214, 193, 196],
  p0: [255, 240, 247], p1: [255, 229, 241], p2: [255, 218, 235], p3: [244, 199, 220],
  u0: [228, 170, 191], u1: [210, 148, 172], u2: [245, 216, 227], u3: [188, 118, 147],
  g0: [255, 247, 250], g1: [247, 192, 211], g2: [234, 142, 176], gt: [214, 106, 147],
  w0: [252, 250, 252], w1: [236, 231, 238], w2: [214, 206, 216], wp: [240, 158, 190],
};
const PINK = new Set(['255,240,247', '255,229,241', '255,218,235']);
const PINK2HAIR = { '255,240,247': P.h2, '255,229,241': P.h3, '255,218,235': P.h4, '163,147,151': P.h3 };
const HAIR_DARK = new Set(['229,210,210', '234,217,217', '229,212,212', '232,214,214']);
const HAIR_LIGHT = new Set(['240,228,225', '239,225,225', '245,238,238', '243,233,233']);
const toHair = c => (c && PINK2HAIR[K(c)]) || c;
const toFabric = c => (!c ? P.p1 : PINK.has(K(c)) ? c : HAIR_DARK.has(K(c)) ? P.p1 : HAIR_LIGHT.has(K(c)) ? P.p0 : c);

// standard 64x64 skin regions (slim arms)
const REG = {
  head: { up: [8, 0], down: [16, 0], east: [0, 8], north: [8, 8], west: [16, 8], south: [24, 8] },
  hat: { up: [40, 0], down: [48, 0], east: [32, 8], north: [40, 8], west: [48, 8], south: [56, 8] },
  body: { up: [20, 16], down: [28, 16], east: [16, 20], north: [20, 20], west: [28, 20], south: [32, 20] },
  jacket: { up: [20, 32], down: [28, 32], east: [16, 36], north: [20, 36], west: [28, 36], south: [32, 36] },
  rsleeve: { up: [44, 32], down: [47, 32], east: [40, 36], north: [44, 36], west: [47, 36], south: [51, 36] },
  lsleeve: { up: [52, 48], down: [55, 48], east: [48, 52], north: [52, 52], west: [55, 52], south: [59, 52] },
  rarm: { up: [44, 16], down: [47, 16], east: [40, 20], north: [44, 20], west: [47, 20], south: [51, 20] },
  larm: { up: [36, 48], down: [39, 48], east: [32, 52], north: [36, 52], west: [39, 52], south: [43, 52] },
  rleg: { up: [4, 16], down: [8, 16], east: [0, 20], north: [4, 20], west: [8, 20], south: [12, 20] },
  lleg: { up: [20, 48], down: [24, 48], east: [16, 52], north: [20, 52], west: [24, 52], south: [28, 52] },
};
const DIMS = (W, H, D) => ({ up: [W, D], down: [W, D], east: [D, H], west: [D, H], north: [W, H], south: [W, H] });
// sample a standard-skin box region, rescaled to the target face; r0/rn pick a row band of the side faces
function sampler(reg, W, H, D, { r0 = 0, rn = H, up = 'up', down = 'down', map = c => c } = {}) {
  const R = REG[reg], dims = DIMS(W, H, D);
  return (face, x, y, w, h) => {
    const f = face === 'up' ? up : face === 'down' ? down : face;
    const [sw, sh0] = dims[f];
    const side = f !== 'up' && f !== 'down';
    const sh = side ? rn : sh0;
    return map(src(R[f][0] + Math.floor((x * sw) / w), R[f][1] + (side ? r0 : 0) + Math.floor((y * sh) / h)), face, x, y, w, h);
  };
}

// ---------- skeleton (shared by the body and every outfit) ----------
const SKELETON = [
  ['root', null, [0, 0, 0]],
  ['body', 'root', [0, 12, 0]],
  ['waist', 'body', [0, 12, 0]],
  ['chest', 'waist', [0, 18, 0]],
  ['bust', 'chest', [0, 23, -2], [-16, 0, 0]],
  ['head', 'chest', [0, 24, 0]],
  ['hair_front', 'chest', [0, 24, -3.05]],
  ['hair_back', 'chest', [0, 24, 2.75]],
  ['right_arm', 'chest', [-5, 21.5, 0]],
  ['right_forearm', 'right_arm', [-5.5, 17.5, 0]],
  ['left_arm', 'chest', [5, 21.5, 0]],
  ['left_forearm', 'left_arm', [5.5, 17.5, 0]],
  ['skirt_front', 'waist', [0, 11.75, -3.3]],
  ['skirt_back', 'waist', [0, 11.75, 2.3]],
  ['right_leg', 'body', [-1.9, 12, 0]],
  ['right_shin', 'right_leg', [-1.9, 6, 0]],
  ['left_leg', 'body', [1.9, 12, 0]],
  ['left_shin', 'left_leg', [1.9, 6, 0]],
];

// ---------- painters ----------
const hairFill = (x, y) => {
  const col = [P.h2, P.h3, P.h3, P.h4, P.h2, P.h3, P.h4, P.h3][x % 8];
  return (x * 7 + y * 3) % 11 === 0 ? P.h1 : (x * 5 + y * 2) % 13 === 0 ? P.h4 : col;
};
const headP = sampler('head', 8, 8, 8, { map: toHair });
const hatRaw = sampler('hat', 8, 8, 8, { map: toHair });
const hairShellP = (face, x, y, w, h) => {
  const c = hatRaw(face, x, y, w, h);
  if (face === 'north') return y === 0 ? c || P.h2 : c;
  if (face === 'east' || face === 'west') {
    const edge = face === 'east' ? x === w - 1 : x === 0;
    const near = face === 'east' ? x === w - 2 : x === 1;
    if (edge) return c;
    if (near && y >= 4) return null;
    return hairFill(x, y);
  }
  if (face === 'down') return x === 0 || x === w - 1 ? P.h4 : null;
  return hairFill(x, y);
};
const skinP = (face) => (face === 'east' || face === 'west' || face === 'down' ? P.sk2 : P.sk);
const bustP = (face) => (face === 'down' ? P.sk3 : face === 'east' || face === 'west' ? P.sk2 : P.sk);
const legP = (side, shin) => sampler(side === 'R' ? 'rleg' : 'lleg', 4, 12, 4, { r0: shin ? 6 : 0, rn: 6, map: c => c || P.sk });
const strandFront = side => (x, y) => {
  const col = (side === 'R' ? 20 : 26) + x;
  const c = src(col, 36 + y) || src(col, 20 + y);
  return c && PINK.has(K(c)) ? null : c;
};
const strandP = side => {
  const F = strandFront(side);
  return (face, x, y, w, h) => {
    if (face === 'north') return F(x, y);
    if (face === 'south') return F(x, y) ? P.h4 : null;
    if (face === 'east') return F(0, y) ? P.h4 : null;
    if (face === 'west') return F(1, y) ? P.h4 : null;
    if (face === 'down') return F(x, 11) ? P.h4 : null;
    return P.h3;
  };
};
const hairBackP = (face, x, y, w, h) => {
  const tip = (yy, xx) => (yy === h - 1 && (xx === 0 || xx === w - 1 || xx === 3 || xx === 4)) || (yy === h - 2 && (xx === 0 || xx === w - 1));
  if (face === 'south') return tip(y, x) ? null : hairFill(x + 1, y);
  if (face === 'north') return tip(y, x) ? null : P.h5;
  if (face === 'east' || face === 'west') return y >= h - 2 ? null : P.h4;
  if (face === 'down') return x === 0 || x === w - 1 || x === 3 || x === 4 ? null : P.h4;
  return P.h4;
};

// everyday — her original skin's hoodie, kept as two layers like a vanilla skin (base shell + overlay)
const headRaw = sampler('head', 8, 8, 8);
const hoodP = (face, x, y, w, h) => {
  const c = headRaw(face, x, y, w, h);
  if (face === 'north') return y === 0 || (y === 1 && (x === 0 || x === w - 1)) ? c : null;
  if (face === 'down') return c && PINK.has(K(c)) ? c : null;
  return c;
};
const hatP = sampler('hat', 8, 8, 8);
const torsoP = (reg, lower) => {
  const s = sampler(reg, 8, 12, 4, { r0: lower ? 6 : 0, rn: 6 });
  return (face, x, y, w, h) => ((lower ? face === 'up' : face === 'down') ? null : s(face, x, y, w, h));
};
// the bust wedge shows the same rows of her skin as the chest behind it (rows 1-4 of the body front)
const wedgeP = reg => (face, x, y) => {
  const R = REG[reg];
  if (face === 'north') return src(R.north[0] + x, R.north[1] + 1 + y);
  if (face === 'east') return src(R.east[0] + 3, R.east[1] + 1 + y);
  if (face === 'west') return src(R.west[0], R.west[1] + 1 + y);
  if (face === 'down') return reg === 'body' ? P.p0 : src(R.north[0] + x, R.north[1] + 4);
  return null;
};
const sleeveP = (reg, lower) => {
  const s = sampler(reg, 3, 12, 4, { r0: lower ? 6 : 0, rn: 6 });
  return (face, x, y, w, h) => ((lower ? face === 'up' : face === 'down') ? null : s(face, x, y, w, h));
};
// her underwear set — shared by the everyday outfit (under the hoodie) and the sleep outfit
const pantiesP = (face, x, y, w, h) => {
  if (face === 'up') return P.u2;
  if (face === 'down') return P.u0;
  if (face === 'north' || face === 'south') {
    if (y === 0) return face === 'north' && (x === 3 || x === 4) ? P.u3 : P.u2;
    if (y === 1) return P.u0;
    return x === 0 || x === w - 1 ? null : P.u0;
  }
  return y === 0 ? P.u2 : y === 1 ? P.u0 : null;
};
const legBandP = side => (face, x) => {
  if (face === 'up' || face === 'down') return null;
  if (face === (side === 'R' ? 'west' : 'east')) return P.u0;
  if (face === 'north') return (side === 'R' ? x >= 2 : x <= 1) ? P.u0 : null;
  if (face === 'south') return (side === 'R' ? x <= 1 : x >= 2) ? P.u0 : null;
  return null;
};
// beach — pink gingham triangle top + tie-side bottoms
const ging = (x, y) => (x % 2 === 0 && y % 2 === 0 ? P.g2 : x % 2 === 0 || y % 2 === 0 ? P.g1 : P.g0);
const bkBustP = (face, x, y) => {
  if (face === 'north') {
    if (y === 0) return x === 2 || x === 5 ? P.gt : null;
    if (y === 1) return x === 2 || x === 5 ? ging(x, y) : null;
    if (y === 2) return x === 1 || x === 2 || x === 5 || x === 6 ? ging(x, y) : null;
    return ging(x, y);
  }
  if (face === 'down') return ging(x, 3);
  if (face === 'east' || face === 'west') return y === 3 ? P.g1 : null;
  if (face === 'up') return x === 2 || x === 5 ? P.gt : null;
  return null;
};
const bkChestP = (face, x, y) => {
  const tie = P.gt;
  if (face === 'north') return y === 0 ? (x === 2 || x === 5 ? tie : null) : y === 4 ? tie : null;
  if (face === 'up') return y >= 2 ? (x === 2 || x === 5 ? tie : null) : x === 3 || x === 4 ? tie : null;
  if (face === 'south') {
    if (y === 0) return x >= 2 && x <= 5 ? (x === 3 || x === 4 ? P.g2 : tie) : null;
    if (y === 4) return x === 3 || x === 4 ? P.g2 : tie;
    if (y === 3 || y === 5) return x === 2 || x === 5 ? tie : null;
    return null;
  }
  if (face === 'east' || face === 'west') return y === 4 ? tie : null;
  return null;
};
const bkBottomP = (face, x, y, w) => {
  const tie = P.gt;
  if (face === 'north' || face === 'south') {
    if (y === 0) return x === 0 || x === w - 1 ? tie : ging(x, y);
    if (y === 1) return x === 0 || x === w - 1 ? null : ging(x, y);
    return x >= 2 && x <= w - 3 ? ging(x, y) : null;
  }
  if (face === 'east' || face === 'west') {
    if (y === 0) return x === 1 || x === 2 ? P.g2 : tie;
    if (y === 1) return x === 1 || x === 2 ? tie : null;
    return x === 1 ? tie : null;
  }
  if (face === 'down') return ging(x, y);
  return null;
};
// sleep — dusty-rose bra, same panties as under the hoodie
const braBustP = (face, x, y) => {
  if (face === 'north') {
    if (y === 0) return x === 1 || x === 6 ? P.u1 : null;
    if (y === 1) return x === 1 || x === 2 || x === 5 || x === 6 ? P.u2 : null;
    if (y === 2) return x === 3 || x === 4 ? P.u3 : P.u0;
    return P.u0;
  }
  if (face === 'down') return P.u1;
  if (face === 'east' || face === 'west') return y >= 2 ? P.u0 : null;
  if (face === 'up') return x === 1 || x === 6 ? P.u1 : null;
  return null;
};
const braChestP = (face, x, y) => {
  if (face === 'north') return y === 0 ? (x === 1 || x === 6 ? P.u1 : null) : y === 4 ? P.u0 : y === 5 ? P.u1 : null;
  if (face === 'south') {
    if (y <= 3) return x === 1 || x === 6 ? P.u1 : null;
    if (y === 4) return x === 3 || x === 4 ? P.u3 : P.u0;
    return P.u1;
  }
  if (face === 'east' || face === 'west') return y <= 2 ? null : y === 5 ? P.u1 : P.u0;
  if (face === 'up') return x === 1 || x === 6 ? P.u1 : null;
  if (face === 'down') return P.u1;
  return null;
};

// ---------- parts ----------
const limbs = (side) => {
  const R = side === 'R', sx = R ? -1 : 1, arm = R ? 'right' : 'left', a = R ? 'r' : 'l';
  const ax = R ? -7 : 4, lx = R ? -3.9 : -0.1;
  return [
    { bone: `${arm}_upper_arm`, parent: `${arm}_arm`, cubes: [
      { n: `${a}_upper`, o: [ax, 17.5, -2], s: [3, 6, 4], paint: skinP },
      { n: `${a}_elbow_fill`, o: [ax, 15.5, -2], s: [3, 3, 4], inf: -0.1, uvFrom: `${a}_lower` }] },
    { bone: `${arm}_lower_arm`, parent: `${arm}_forearm`, cubes: [{ n: `${a}_lower`, o: [ax, 11.5, -2], s: [3, 6, 4], paint: skinP }] },
    { bone: `${arm}_thigh`, parent: `${arm}_leg`, cubes: [
      { n: `${a}_thigh`, o: [lx, 6, -2], s: [4, 6, 4], paint: legP(side, false) },
      { n: `${a}_hip_fill`, o: [lx, 11, -2], s: [4, 3, 4], inf: -0.1, uvFrom: `${a}_thigh` },
      { n: `${a}_knee_fill`, o: [lx, 4, -2], s: [4, 3, 4], inf: -0.1, uvFrom: `${a}_shin` }] },
    { bone: `${arm}_calf`, parent: `${arm}_shin`, cubes: [{ n: `${a}_shin`, o: [lx, 0, -2], s: [4, 6, 4], paint: legP(side, true) }] },
  ];
};
const BASE = [
  { bone: 'head_skin', parent: 'head', cubes: [{ n: 'head', o: [-4, 24, -4], s: [8, 8, 8], uv: [0, 0], paint: headP }] },
  { bone: 'hair', parent: 'head', cubes: [{ n: 'hair', o: [-4, 24, -4], s: [8, 8, 8], inf: 0.5, uv: [32, 0], paint: hairShellP }] },
  { bone: 'chest_skin', parent: 'chest', cubes: [
    { n: 'chest', o: [-4, 18, -2], s: [8, 6, 4], paint: skinP },
    { n: 'chest_fill', o: [-3.5, 15.5, -2], s: [7, 3, 4], inf: -0.1, uvFrom: 'midriff' }] },
  { bone: 'bust', cubes: [{ n: 'bust', o: [-4, 19, -2], s: [8, 4, 1], inf: -0.05, paint: bustP }] },
  { bone: 'waist_skin', parent: 'waist', cubes: [
    { n: 'midriff', o: [-3.5, 15, -2], s: [7, 3, 4], paint: skinP },
    { n: 'hips', o: [-4, 12, -2], s: [8, 3, 4], paint: skinP }] },
  ...limbs('R'), ...limbs('L'),
  { bone: 'hair_front', cubes: [
    { n: 'strand_r', o: [-3.75, 12, -3.55], s: [2, 12, 1], paint: strandP('R') },
    { n: 'strand_l', o: [1.75, 12, -3.55], s: [2, 12, 1], paint: strandP('L') }] },
  { bone: 'hair_back', cubes: [{ n: 'hair_back', o: [-4, 13, 2.25], s: [8, 11, 1], paint: hairBackP }] },
];

const underwear = [
  { bone: 'waist', cubes: [{ n: 'panties', o: [-4, 12, -2], s: [8, 3, 4], inf: 0.12, paint: pantiesP }] },
  { bone: 'right_leg', cubes: [{ n: 'band_r', o: [-3.9, 11, -2], s: [4, 1, 4], inf: 0.1, paint: legBandP('R') }] },
  { bone: 'left_leg', cubes: [{ n: 'band_l', o: [-0.1, 11, -2], s: [4, 1, 4], inf: 0.1, paint: legBandP('L') }] },
];
const sleeves = side => {
  const R = side === 'R', arm = R ? 'right' : 'left', ax = R ? -7 : 4, a = R ? 'r' : 'l', reg = R ? 'rarm' : 'larm', ov = R ? 'rsleeve' : 'lsleeve';
  return [
    { bone: `${arm}_arm`, cubes: [
      { n: `${a}_sleeve_up`, o: [ax, 17.5, -2], s: [3, 6, 4], inf: 0.25, paint: sleeveP(reg, false) },
      { n: `${a}_sleeve_up_ov`, o: [ax, 17.5, -2], s: [3, 6, 4], inf: 0.5, paint: sleeveP(ov, false) },
      { n: `${a}_sleeve_fill`, o: [ax, 15.5, -2], s: [3, 3, 4], inf: 0.15, uvFrom: `${a}_sleeve_lo` }] },
    { bone: `${arm}_forearm`, cubes: [
      { n: `${a}_sleeve_lo`, o: [ax, 11.5, -2], s: [3, 6, 4], inf: 0.2, paint: sleeveP(reg, true) },
      { n: `${a}_sleeve_lo_ov`, o: [ax, 11.5, -2], s: [3, 6, 4], inf: 0.45, paint: sleeveP(ov, true) }] },
  ];
};
const BUST = { o: [-4, 19, -2], s: [8, 4, 1] };
const OUTFITS = [
  { id: 'everyday', label: 'Everyday', note: 'Her pink hoodie from the original skin, hood up, bare legs; her panties underneath',
    hide: ['hair', 'hair_front', 'hair_back', 'chest_skin', 'bust', 'right_upper_arm', 'right_lower_arm', 'left_upper_arm', 'left_lower_arm'], hairPush: 0,
    parts: [
      { bone: 'head', cubes: [
        { n: 'hood', o: [-4, 24, -4], s: [8, 8, 8], inf: 0.25, paint: hoodP },
        { n: 'hood_hat', o: [-4, 24, -4], s: [8, 8, 8], inf: 0.5, paint: hatP }] },
      { bone: 'chest', cubes: [
        { n: 'hoodie_chest', o: [-4, 18, -2], s: [8, 6, 4], inf: 0.25, paint: torsoP('body', false) },
        { n: 'hoodie_chest_ov', o: [-4, 18, -2], s: [8, 6, 4], inf: 0.5, paint: torsoP('jacket', false) }] },
      { bone: 'bust', cubes: [
        { n: 'hoodie_bust', ...BUST, inf: 0.3, paint: wedgeP('body') },
        { n: 'hoodie_bust_ov', ...BUST, inf: 0.55, paint: wedgeP('jacket') }] },
      { bone: 'waist', cubes: [
        { n: 'hoodie_waist', o: [-4, 12, -2], s: [8, 6, 4], inf: 0.2, paint: torsoP('body', true) },
        { n: 'hoodie_waist_ov', o: [-4, 12, -2], s: [8, 6, 4], inf: 0.45, paint: torsoP('jacket', true) },
        { ...underwear[0].cubes[0] }] },
      ...sleeves('R'), ...sleeves('L'),
    ] },
  { id: 'beach', label: 'Beach', note: 'Gingham triangle top, tie-side bottoms',
    hide: [], hairPush: 0,
    parts: [
      { bone: 'bust', cubes: [{ n: 'top_cups', o: [-4, 19, -2], s: [8, 4, 1], inf: 0.22, paint: bkBustP }] },
      { bone: 'chest', cubes: [{ n: 'top_ties', o: [-4, 18, -2], s: [8, 6, 4], inf: 0.12, paint: bkChestP }] },
      { bone: 'waist', cubes: [{ n: 'bottoms', o: [-4, 12, -2], s: [8, 3, 4], inf: 0.12, paint: bkBottomP }] },
    ] },
  { id: 'sleep', label: 'Sleep', note: 'Her underwear set: bra and panties',
    hide: [], hairPush: 0,
    parts: [
      { bone: 'bust', cubes: [{ n: 'bra_cups', o: [-4, 19, -2], s: [8, 4, 1], inf: 0.22, paint: braBustP }] },
      { bone: 'chest', cubes: [{ n: 'bra_band', o: [-4, 18, -2], s: [8, 6, 4], inf: 0.12, paint: braChestP }] },
      ...underwear.map(g => ({ ...g, cubes: g.cubes.map(c => ({ ...c })) })),
    ] },
];

// ---------- texture packing, painting, geometry ----------
class Tex {
  constructor() { this.d = new Uint8ClampedArray(64 * 64 * 4); }
  set(x, y, c) { if (!c || x < 0 || y < 0 || x >= 64 || y >= 64) return; const o = (y * 64 + x) * 4; this.d[o] = c[0]; this.d[o + 1] = c[1]; this.d[o + 2] = c[2]; this.d[o + 3] = 255; }
  async save(path) { const cv = createCanvas(64, 64); const g = cv.getContext('2d'); const id = g.createImageData(64, 64); id.data.set(this.d); g.putImageData(id, 0, 0); await saveFile(path, cv); }
}
const rects = (u, v, w, h, d) => ({ up: [u + d, v, w, d], down: [u + d + w, v, w, d], east: [u, v + d, d, h], north: [u + d, v + d, w, h], west: [u + d + w, v + d, d, h], south: [u + 2 * d + w, v + d, w, h] });
function pack(cubes, startY) {
  const items = cubes.filter(c => !c.uv && !c.uvFrom).map(c => { const [w, h, d] = c.s.map(Math.floor); return { c, W: 2 * (d + w), H: d + h }; })
    .sort((a, b) => b.H - a.H || b.W - a.W);
  const shelves = []; let y = startY;
  for (const it of items) {
    let s = shelves.find(s => s.x + it.W <= 64 && it.H <= s.h);
    if (!s) { s = { y, h: it.H, x: 0 }; shelves.push(s); y += it.H; }
    it.c.uv = [s.x, s.y]; s.x += it.W;
  }
  if (y > 64) throw new Error('texture overflow ' + y);
  for (const c of cubes) if (c.uvFrom) c.uv = cubes.find(o => o.n === c.uvFrom).uv.slice();
  return y;
}
function paint(tex, cube) {
  if (!cube.paint) return;
  const [w, h, d] = cube.s.map(Math.floor);
  const R = rects(cube.uv[0], cube.uv[1], w, h, d);
  for (const f in R) { const [rx, ry, rw, rh] = R[f]; for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) tex.set(rx + x, ry + y, cube.paint(f, x, y, rw, rh)); }
}
function geo(identifier, groups) {
  const bones = SKELETON.map(([name, parent, pivot, rot]) => ({ name, ...(parent ? { parent } : {}), pivot, ...(rot ? { rotation: rot } : {}) }));
  const by = Object.fromEntries(bones.map(b => [b.name, b]));
  for (const g of groups) {
    let b = by[g.bone];
    if (!b) { b = { name: g.bone, parent: g.parent, pivot: by[g.parent].pivot.slice() }; bones.splice(bones.indexOf(by[g.parent]) + 1, 0, b); by[g.bone] = b; }
    b.cubes = (b.cubes || []).concat(g.cubes.map(c => ({ origin: c.o, size: c.s, uv: c.uv, ...(c.inf ? { inflate: c.inf } : {}) })));
  }
  return { format_version: '1.12.0', 'minecraft:geometry': [{ description: { identifier, texture_width: 64, texture_height: 64, visible_bounds_width: 3, visible_bounds_height: 3.5, visible_bounds_offset: [0, 1.5, 0] }, bones }] };
}
async function build(groups, startY, geoPath, texPath, id) {
  const cubes = groups.flatMap(g => g.cubes);
  const used = pack(cubes, startY);
  const tex = new Tex(); cubes.forEach(c => paint(tex, c));
  await tex.save(texPath);
  await saveFile(geoPath, JSON.stringify(geo(id, groups), null, 1));
  return used;
}
const report = {};
report.base = await build(BASE, 16, `${RES}/geo/entity/${NAME}.geo.json`, `${RES}/textures/entity/${NAME}/skin.png`, `geometry.${NAME}`);
for (const o of OUTFITS) report[o.id] = await build(o.parts, 0, `${RES}/geo/entity/${NAME}_${o.id}.geo.json`, `${RES}/textures/entity/${NAME}/${o.id}.png`, `geometry.${NAME}.${o.id}`);

// ---------- animations (Bedrock/GeckoLib degrees: +X tips the top of a part forward, i.e. a hanging limb swings back) ----------
const TAU = Math.PI * 2, Z3 = [0, 0, 0];
const sm = x => (x <= 0 ? 0 : x >= 1 ? 1 : x * x * (3 - 2 * x));
const ANIMS = {}, LIST = [];
const tkey = t => { const s = (+t.toFixed(4)).toString(); return s.includes('.') ? s : s + '.0'; };
function addAnim(name, length, loop, fps, f, label) {
  const n = Math.max(1, Math.round(length * fps)), ch = {};
  for (let i = 0; i <= n; i++) {
    const t = (length * i) / n, pose = f(t);
    for (const b in pose) for (const k of ['r', 'p']) if (pose[b][k]) (ch[b + '|' + k] = ch[b + '|' + k] || []).push([t, pose[b][k].map(v => +v.toFixed(2) || 0)]);
  }
  const bones = {};
  for (const key in ch) {
    const [b, k] = key.split('|'), fr = ch[key];
    if (fr.every(([, v]) => v.every(x => x === 0))) continue;
    const same = fr.every(([, v]) => K(v) === K(fr[0][1]));
    const kept = same ? [fr[0]] : fr.filter(([, v], i) => i === 0 || i === fr.length - 1 || !(K(v) === K(fr[i - 1][1]) && K(v) === K(fr[i + 1][1])));
    (bones[b] = bones[b] || {})[k === 'r' ? 'rotation' : 'position'] = Object.fromEntries(kept.map(([t, v]) => [tkey(t), v]));
  }
  ANIMS[`animation.${NAME}.${name}`] = { loop, animation_length: +length.toFixed(4), bones };
  LIST.push({ id: name, key: `animation.${NAME}.${name}`, loop, length, label });
}
const norm = poses => {
  const keys = {}; for (const p of poses) for (const b in p) for (const c in p[b]) (keys[b] = keys[b] || new Set()).add(c);
  return poses.map(p => { const o = {}; for (const b in keys) { o[b] = {}; for (const c of keys[b]) o[b][c] = (p[b] && p[b][c]) || Z3; } return o; });
};
const blend = (A, B, k) => { const o = {}; for (const b in A) { o[b] = {}; for (const c in A[b]) o[b][c] = A[b][c].map((v, i) => v + (B[b][c][i] - v) * k); } return o; };
const seq = (list) => { const ps = norm(list.map(l => l[1])); return t => { for (let i = 1; i < list.length; i++) if (t <= list[i][0]) return blend(ps[i - 1], ps[i], sm((t - list[i - 1][0]) / (list[i][0] - list[i - 1][0]))); return ps[ps.length - 1]; }; };
const over = (A, B) => { const [a, b] = norm([A, B]); const o = {}; for (const k in a) { o[k] = {}; for (const c in a[k]) o[k][c] = a[k][c].map((v, i) => v + b[k][c][i]); } return o; };

const STAND = { chest: { r: [0, 0, 0] } };
const SIT = { body: { p: [0, -6, 0] }, chest: { r: [4, 0, 0] }, head: { r: [5, 0, 0] },
  right_leg: { r: [-88, 4, 0] }, left_leg: { r: [-88, -4, 0] }, right_shin: { r: [86, 0, 0] }, left_shin: { r: [86, 0, 0] },
  right_arm: { r: [-26, 0, 9] }, left_arm: { r: [-26, 0, -9] }, right_forearm: { r: [-24, 0, 0] }, left_forearm: { r: [-24, 0, 0] } };
const SLEEP = { body: { r: [0, 0, -90], p: [6, -5.3, 0] }, chest: { r: [6, 0, 0] }, head: { r: [8, 0, -6] },
  right_arm: { r: [-78, -30, 0], p: [1.2, 0, 0] }, right_forearm: { r: [-40, 0, 0] }, left_arm: { r: [-30, 8, -4] }, left_forearm: { r: [-30, 0, 0] },
  right_leg: { r: [-42, 0, 0] }, right_shin: { r: [68, 0, 0] }, left_leg: { r: [-54, 0, 0] }, left_shin: { r: [74, 0, 0] } };
const CUDDLE = { ...SLEEP, head: { r: [16, 0, -6] }, left_arm: { r: [-86, 26, 0] }, left_forearm: { r: [-14, 0, 18] } };
const HUG = { waist: { r: [3, 0, 0] }, chest: { r: [9, 0, 0] }, head: { r: [6, 22, 10] },
  right_arm: { r: [-80, -30, 0] }, left_arm: { r: [-80, 30, 0] }, right_forearm: { r: [-12, 0, -58] }, left_forearm: { r: [-12, 0, 58] } };
const CARRIED = { body: { r: [-40, 0, 0], p: [0, 8, 0] }, chest: { r: [-8, 0, 0] }, head: { r: [-6, -20, 0] },
  right_leg: { r: [-50, 0, 0] }, left_leg: { r: [-56, 0, 0] }, right_shin: { r: [82, 0, 0] }, left_shin: { r: [86, 0, 0] },
  right_arm: { r: [-150, -18, 0] }, right_forearm: { r: [-48, 0, 0] }, left_arm: { r: [-48, 14, 0] }, left_forearm: { r: [-52, 0, 0] } };
const STRETCH = { body: { p: [0, 0.6, 0] }, chest: { r: [-8, 0, 0] }, head: { r: [-16, 0, 0] },
  right_arm: { r: [-172, 0, 12] }, left_arm: { r: [-172, 0, -12] }, right_forearm: { r: [-6, 0, 0] }, left_forearm: { r: [-6, 0, 0] } };
const SIT_STRETCH = over(SIT, { chest: { r: [-10, 0, 0] }, head: { r: [-18, 0, 0] }, right_arm: { r: [-140, 0, 4] }, left_arm: { r: [-140, 0, -4] }, right_forearm: { r: [18, 0, 0] }, left_forearm: { r: [18, 0, 0] } });
const breathe = (t, T, amp = 1) => ({ chest: { r: [amp * Math.sin((TAU * t) / T), 0, 0] } });

addAnim('idle', 4, true, 4, t => {
  const b = Math.sin((TAU * t) / 4), b2 = Math.sin((TAU * t) / 4 + 1.3);
  return { chest: { r: [1 + b, 0, 0] }, head: { r: [-1 - 0.8 * b, 3 * b2, 0] }, right_arm: { r: [0, 0, 4 + b] }, left_arm: { r: [0, 0, -4 - b] },
    right_forearm: { r: [-5, 0, 0] }, left_forearm: { r: [-5, 0, 0] } };
}, 'Idle');
addAnim('walk', 1, true, 16, t => {
  const s = Math.sin(TAU * t), c = Math.cos(TAU * t);
  return { body: { p: [0, 0.4 * Math.abs(c), 0] }, chest: { r: [3, 4 * s, 0] }, head: { r: [-2, -4 * s, 0] },
    right_leg: { r: [-26 * s, 0, 0] }, left_leg: { r: [26 * s, 0, 0] }, right_shin: { r: [6 + 26 * Math.max(0, c), 0, 0] }, left_shin: { r: [6 + 26 * Math.max(0, -c), 0, 0] },
    right_arm: { r: [22 * s, 0, 5] }, left_arm: { r: [-22 * s, 0, -5] }, right_forearm: { r: [-10 - 10 * Math.max(0, -s), 0, 0] }, left_forearm: { r: [-10 - 10 * Math.max(0, s), 0, 0] } };
}, 'Walk');
addAnim('run', 0.6, true, 20, t => {
  const s = Math.sin((TAU * t) / 0.6), c = Math.cos((TAU * t) / 0.6);
  return { body: { p: [0, 1.0 * Math.abs(c), 0] }, chest: { r: [12, 6 * s, 0] }, head: { r: [-10, -5 * s, 0] },
    right_leg: { r: [-48 * s, 0, 0] }, left_leg: { r: [48 * s, 0, 0] }, right_shin: { r: [12 + 72 * Math.max(0, c), 0, 0] }, left_shin: { r: [12 + 72 * Math.max(0, -c), 0, 0] },
    right_arm: { r: [40 * s, 0, 8] }, left_arm: { r: [-40 * s, 0, -8] }, right_forearm: { r: [-62, 0, 0] }, left_forearm: { r: [-62, 0, 0] } };
}, 'Run');
addAnim('swim', 1.6, true, 15, t => {
  const ph = t / 1.6, k = Math.sin(TAU * 4 * ph);
  return { body: { r: [78, 0, 0], p: [0, -1, 0] }, head: { r: [-50, 0, 0] }, chest: { r: [0, 10 * Math.sin(TAU * ph), 0] },
    right_arm: { r: [-180 + 360 * ph, 0, 10] }, left_arm: { r: [360 * ph, 0, -10] }, right_forearm: { r: [-15, 0, 0] }, left_forearm: { r: [-15, 0, 0] },
    right_leg: { r: [12 * k, 0, 0] }, left_leg: { r: [-12 * k, 0, 0] }, right_shin: { r: [10 + 8 * k, 0, 0] }, left_shin: { r: [10 - 8 * k, 0, 0] } };
}, 'Swim');
addAnim('sit', 3, true, 4, t => over(SIT, { ...breathe(t, 3), head: { r: [0, 0, 3 * Math.sin((TAU * t) / 3)] } }), 'Sit');
addAnim('hug', 3, true, 6, t => over(HUG, { ...breathe(t, 3, 1.2), body: { r: [0, 3 * Math.sin((TAU * t) / 3), 0] } }), 'Hug');
addAnim('carried', 3, true, 4, t => over(CARRIED, { ...breathe(t, 3), right_shin: { r: [4 * Math.sin((TAU * t) / 3), 0, 0] }, left_shin: { r: [-4 * Math.sin((TAU * t) / 3), 0, 0] } }), 'Carried');
addAnim('lie_down', 1.6, 'hold_on_last_frame', 10, seq([[0, STAND], [0.7, over(SIT, { chest: { r: [8, 0, 0] } })], [1.6, SLEEP]]), 'Lie down');
addAnim('sleep', 4, true, 4, t => over(SLEEP, breathe(t, 4, 1.5)), 'Sleep');
addAnim('cuddle', 4, true, 4, t => over(CUDDLE, breathe(t, 4, 1.5)), 'Cuddle');
addAnim('stretch', 2.4, false, 10, seq([[0, STAND], [0.7, STRETCH], [1.5, over(STRETCH, { chest: { r: [-3, 0, 0] } })], [2.4, STAND]]), 'Stretch');
addAnim('wake_up', 2.6, false, 10, seq([[0, SLEEP], [1.1, SIT], [1.8, SIT_STRETCH], [2.6, SIT]]), 'Wake up');
// sitting on the player's lap, leaning back into them
const LAP = { body: { p: [0, -4, 0] }, waist: { r: [-6, 0, 0] }, chest: { r: [-4, 0, 0] }, head: { r: [-6, -12, 8] },
  right_leg: { r: [-82, 0, 0] }, left_leg: { r: [-82, 0, 0] }, right_shin: { r: [72, 0, 0] }, left_shin: { r: [72, 0, 0] },
  right_arm: { r: [-34, -14, 0] }, left_arm: { r: [-34, 14, 0] }, right_forearm: { r: [-32, 0, 0] }, left_forearm: { r: [-32, 0, 0] } };
// hands clasped in front, shoulders lifted a little — shared by both petting clips
const CLASP = { right_arm: { r: [-22, -20, 0], p: [0, 0.4, 0] }, left_arm: { r: [-22, 20, 0], p: [0, 0.4, 0] },
  right_forearm: { r: [-46, 0, -18] }, left_forearm: { r: [-46, 0, 18] } };
addAnim('lap', 4, true, 4, t => {
  const s = Math.sin((TAU * t) / 4);
  return over(LAP, { ...breathe(t, 4), head: { r: [0, 2 * s, 1.5 * s] }, right_shin: { r: [6 * s, 0, 0] }, left_shin: { r: [-6 * s, 0, 0] } });
}, 'Lap sit');
addAnim('pet_head', 2.4, true, 10, t => {
  const pat = Math.max(0, Math.sin((TAU * t) / 0.6));
  return over(CLASP, { body: { p: [0, -0.3 * pat, 0] }, chest: { r: [3, 0, 0] }, head: { r: [6 + 6 * pat, 0, 3 * Math.sin((TAU * t) / 2.4)] } });
}, 'Head pat');
addAnim('pet_cheek', 3, true, 6, t => {
  const s = Math.sin((TAU * t) / 3);
  return over(CLASP, { body: { r: [0, 4 * s, 0] }, chest: { r: [2, 4, 3] }, head: { r: [-4, 10 + 2 * s, 14 + 3 * s] } });
}, 'Cheek pet');
await saveFile(`${RES}/animations/entity/${NAME}.animation.json`, JSON.stringify({ format_version: '1.8.0', animations: ANIMS, geckolib_format_version: 2 }, null, 1));

// ---------- viewer manifest + generated Outfit enum ----------
const outfitsMeta = OUTFITS.map(o => ({ id: o.id, label: o.label, note: o.note, geo: `${RES}/geo/entity/${NAME}_${o.id}.geo.json`, texture: `${RES}/textures/entity/${NAME}/${o.id}.png`, hide: o.hide, hairPush: o.hairPush }));
await saveFile('viewer/manifest.json', JSON.stringify({
  base: { geo: `${RES}/geo/entity/${NAME}.geo.json`, texture: `${RES}/textures/entity/${NAME}/skin.png` },
  animations: `${RES}/animations/entity/${NAME}.animation.json`, animationList: LIST, outfits: outfitsMeta }, null, 1));
const javaSet = a => (a.length ? `Set.of(${a.map(s => `"${s}"`).join(', ')})` : 'Set.of()');
await saveFile(`mod/src/main/java/${PKG.replace(/\./g, '/')}/outfit/Outfit.java`, `package ${PKG}.outfit;

import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Her wardrobe. Each outfit is its own GeckoLib model + texture built on the shared skeleton in
 * ${NAME}.geo.json, so switching only changes which layer the renderer draws.
 * GENERATED by tools/build-assets.js — edit the OUTFITS table there and regenerate.
 */
public enum Outfit {
${OUTFITS.map(o => `    ${o.id.toUpperCase()}("${o.id}", ${javaSet(o.hide)}, ${o.hairPush.toFixed(2)}f)`).join(',\n')};

    public static final String MODID = "${MODID}";

    /** Stable id used in NBT, commands and network payloads. */
    public final String id;
    /** Body bones hidden while this outfit is worn (they sit fully under opaque fabric). */
    public final Set<String> hiddenBodyBones;
    /** Z offset (px) applied to the hair_front bone so the front strands lie on top of thick fabric. */
    public final float hairFrontPush;
    public final ResourceLocation model;
    public final ResourceLocation texture;

    Outfit(String id, Set<String> hiddenBodyBones, float hairFrontPush) {
        this.id = id;
        this.hiddenBodyBones = hiddenBodyBones;
        this.hairFrontPush = hairFrontPush;
        this.model = ResourceLocation.fromNamespaceAndPath(MODID, "geo/entity/${NAME}_" + id + ".geo.json");
        this.texture = ResourceLocation.fromNamespaceAndPath(MODID, "textures/entity/${NAME}/" + id + ".png");
    }

    public static Optional<Outfit> byId(String id) {
        return Arrays.stream(values()).filter(o -> o.id.equals(id)).findFirst();
    }
}
`);
log({ textureRowsUsed: report, animations: LIST.map(a => a.id) });
