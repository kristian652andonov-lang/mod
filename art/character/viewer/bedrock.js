// Minimal Bedrock-geometry + animation runtime that mirrors GeckoLib's transforms:
// X is negated for positions/pivots, rotations become (-x, -y, z) applied Z·Y·X, cubes use box UV.
import * as THREE from 'three';

const D2R = Math.PI / 180;
const ZERO = [0, 0, 0];

export async function loadJSON(url) {
  const r = await fetch(url, { cache: 'no-store' });
  if (!r.ok) throw new Error(`${url}: ${r.status}`);
  return r.json();
}

export function loadTexture(url) {
  return new Promise((resolve, reject) =>
    new THREE.TextureLoader().load(url + '?v=' + Date.now(), (t) => {
      t.magFilter = THREE.NearestFilter;
      t.minFilter = THREE.NearestFilter;
      t.generateMipmaps = false;
      t.colorSpace = THREE.SRGBColorSpace;
      resolve(t);
    }, undefined, reject));
}

function cubeGeometry(c, pv, TW, TH) {
  const inf = c.inflate || 0;
  const [ox, oy, oz] = c.origin, [sx, sy, sz] = c.size;
  const x0 = -(ox + sx) - inf - pv[0], x1 = -ox + inf - pv[0];
  const y0 = oy - inf - pv[1], y1 = oy + sy + inf - pv[1];
  const z0 = oz - inf - pv[2], z1 = oz + sz + inf - pv[2];
  const [w, h, d] = c.size.map(Math.floor);
  const [u, v] = c.uv;
  const faces = [
    // [uv rect, TL, TR, BR, BL, normal] — east is the character's right (+X here), north is her front (-Z)
    [[u + d, v + d, w, h], [x1, y1, z0], [x0, y1, z0], [x0, y0, z0], [x1, y0, z0], [0, 0, -1]],
    [[u, v + d, d, h], [x1, y1, z1], [x1, y1, z0], [x1, y0, z0], [x1, y0, z1], [1, 0, 0]],
    [[u + d + w, v + d, d, h], [x0, y1, z0], [x0, y1, z1], [x0, y0, z1], [x0, y0, z0], [-1, 0, 0]],
    [[u + 2 * d + w, v + d, w, h], [x0, y1, z1], [x1, y1, z1], [x1, y0, z1], [x0, y0, z1], [0, 0, 1]],
    [[u + d, v, w, d], [x1, y1, z1], [x0, y1, z1], [x0, y1, z0], [x1, y1, z0], [0, 1, 0]],
    [[u + d + w, v, w, d], [x1, y0, z1], [x0, y0, z1], [x0, y0, z0], [x1, y0, z0], [0, -1, 0]],
  ];
  const pos = [], nor = [], uvs = [], idx = [];
  for (const [[rx, ry, rw, rh], TL, TR, BR, BL, n] of faces) {
    if (!rw || !rh) continue;
    const i = pos.length / 3;
    pos.push(...TL, ...TR, ...BR, ...BL);
    for (let k = 0; k < 4; k++) nor.push(...n);
    uvs.push(rx / TW, 1 - ry / TH, (rx + rw) / TW, 1 - ry / TH, (rx + rw) / TW, 1 - (ry + rh) / TH, rx / TW, 1 - (ry + rh) / TH);
    idx.push(i, i + 3, i + 2, i, i + 2, i + 1);
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('normal', new THREE.Float32BufferAttribute(nor, 3));
  g.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2));
  g.setIndex(idx);
  return g;
}

/** Builds a bone hierarchy (THREE.Group per bone, meshes per cube) at 1px = 1/16 m. */
export function buildGeo(json, texture, name) {
  const g = json['minecraft:geometry'][0];
  const TW = g.description.texture_width, TH = g.description.texture_height;
  const material = new THREE.MeshStandardMaterial({ name, map: texture, alphaTest: 0.1, side: THREE.DoubleSide, roughness: 1, metalness: 0 });
  const root = new THREE.Group();
  root.name = name;
  root.scale.setScalar(1 / 16);
  const bones = new Map();
  for (const b of g.bones) {
    const grp = new THREE.Group();
    grp.name = b.name;
    const r = b.rotation || ZERO;
    grp.userData.pivot = [-b.pivot[0], b.pivot[1], b.pivot[2]];
    grp.userData.rest = [-r[0] * D2R, -r[1] * D2R, r[2] * D2R];
    bones.set(b.name, grp);
  }
  for (const b of g.bones) {
    const grp = bones.get(b.name), parent = b.parent ? bones.get(b.parent) : null;
    const pp = parent ? parent.userData.pivot : ZERO, pv = grp.userData.pivot;
    grp.userData.base = [pv[0] - pp[0], pv[1] - pp[1], pv[2] - pp[2]];
    grp.position.fromArray(grp.userData.base);
    grp.rotation.set(...grp.userData.rest, 'ZYX');
    (parent || root).add(grp);
    (b.cubes || []).forEach((c, i) => {
      const mesh = new THREE.Mesh(cubeGeometry(c, pv, TW, TH), material);
      mesh.name = `${name}.${b.name}.${i}`;
      grp.add(mesh);
    });
  }
  return { root, bones, material };
}

/** Samples GeckoLib animation JSON (linear keyframes) into {bone: {r, p}} in file convention. */
export class AnimSet {
  constructor(json) {
    this.list = {};
    for (const [key, a] of Object.entries(json.animations)) {
      const bones = {};
      for (const [bone, chans] of Object.entries(a.bones)) {
        bones[bone] = {};
        for (const [ch, frames] of Object.entries(chans)) {
          bones[bone][ch === 'rotation' ? 'r' : 'p'] = Object.entries(frames).map(([t, v]) => [parseFloat(t), Array.isArray(v) ? v : v.post || v.vector]).sort((x, y) => x[0] - y[0]);
        }
      }
      this.list[key.split('.').pop()] = { length: a.animation_length, loop: a.loop, bones };
    }
  }
  /** Loops repeat; one-shots and hold-on-last-frame clips replay after a short hold so they can be watched again. */
  time(a, t) {
    if (a.loop === true) return t % a.length;
    const cycle = a.length + (a.loop === 'hold_on_last_frame' ? 1.6 : 1.0);
    return Math.min(t % cycle, a.length);
  }
  sample(a, t) {
    const pose = new Map();
    for (const [bone, chans] of Object.entries(a.bones)) {
      const o = {};
      for (const [ch, fr] of Object.entries(chans)) o[ch] = interp(fr, t);
      pose.set(bone, o);
    }
    return pose;
  }
}
function interp(fr, t) {
  if (t <= fr[0][0]) return fr[0][1];
  for (let i = 1; i < fr.length; i++) {
    if (t <= fr[i][0]) {
      const [t0, a] = fr[i - 1], [t1, b] = fr[i], k = (t - t0) / (t1 - t0 || 1);
      return a.map((v, j) => v + (b[j] - v) * k);
    }
  }
  return fr[fr.length - 1][1];
}

/** Same rules as GirlfriendModel#setCustomAnimations: hem panels follow the thighs, hair stays off the body. */
export function procedural(pose, outfit) {
  const rx = (n) => (pose.get(n)?.r || ZERO)[0];
  const set = (n, r, p) => pose.set(n, { r, p: p || ZERO });
  const waist = rx('waist');
  const relR = rx('right_leg') - waist, relL = rx('left_leg') - waist;
  set('skirt_front', [Math.min(0, relR, relL), 0, 0]);
  set('skirt_back', [Math.max(0, relR, relL), 0, 0]);
  const lean = rx('chest');
  set('hair_front', [-0.35 * Math.max(0, lean), 0, 0], [0, 0, outfit ? outfit.hairPush : 0]);
  set('hair_back', [0.6 * Math.max(0, -lean), 0, 0]);
  return pose;
}

export function applyPose(model, pose) {
  for (const [name, grp] of model.bones) {
    const ch = pose.get(name), r = ch?.r || ZERO, p = ch?.p || ZERO, rest = grp.userData.rest, b = grp.userData.base;
    grp.rotation.set(rest[0] - r[0] * D2R, rest[1] - r[1] * D2R, rest[2] + r[2] * D2R, 'ZYX');
    grp.position.set(b[0] - p[0], b[1] + p[1], b[2] + p[2]);
  }
}

/** What OutfitLayer does in game: copy every same-named bone transform from the body to the outfit. */
export function syncBones(from, to) {
  for (const [name, grp] of to.bones) {
    const src = from.bones.get(name);
    if (!src) continue;
    grp.position.copy(src.position);
    grp.rotation.copy(src.rotation);
    grp.scale.copy(src.scale);
  }
}
