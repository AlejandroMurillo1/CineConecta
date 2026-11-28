// =============================================================================
// CineConecta · MongoDB · db/demos/mongo/04_synthetic_my_lists.js
// "Mi lista" de los perfiles exportados de PostgreSQL (los mismos que reciben
// sesiones en 02_synthetic_sessions.js), para que la colección my_lists tenga datos.
//
// De dónde sale cada dato:
//   - Perfiles y su clasificación: PostgreSQL, vía 07_export_viewers.sql
//     (llega como `const VIEWERS = [...]` antes de este script).
//   - Contenidos: la colección contents (solo PUBLISHED).
//
// Reglas que respeta (las mismas de la aplicación, RI-08):
//   - Solo contenidos publicados con clasificación <= la del perfil.
//   - Sin repetidos (uniqueItems en el validador).
//   - Un documento por perfil, con _id = profileId.
//
// Qué genera: ≈ 60 % de los perfiles con lista, de 1 a 12 títulos, con más
// probabilidad para los títulos populares (mismo criterio que las sesiones).
//
// Idempotente: reemplaza solo las listas marcadas con synthetic: true. Las listas que
// un perfil armó desde la aplicación no se tocan. Determinista (generador con semilla).
//
// Uso (bash / Git Bash, desde la raíz del proyecto):
//
//   { docker compose exec -T postgres psql -U cine -d cineconecta -qAt -v ON_ERROR_STOP=1 \
//       < db/demos/postgres/07_export_viewers.sql
//     cat db/demos/mongo/04_synthetic_my_lists.js
//   } | docker compose exec -T mongo mongosh --quiet -u cineOwner -p cine_owner_dev \
//       --authenticationDatabase cineconecta cineconecta
//
// Requisitos: 01_demo_catalog.js y/o 03_volume_catalog.js en MongoDB.
// =============================================================================

function abort(message) {
  print('ERROR: ' + message);
  quit(1);
}
if (typeof VIEWERS === 'undefined') {
  abort('Falta VIEWERS: ejecute este script precedido por la salida de '
    + 'db/demos/postgres/07_export_viewers.sql (ver el comando al inicio del archivo).');
}

const DB_NAME = (typeof process !== 'undefined' && (process.env.CINE_DB || process.env.MONGO_INITDB_DATABASE)) || 'cineconecta';
const cine = db.getSiblingDB(DB_NAME);

function rng(seed) {
  let a = seed >>> 0;
  return function next() {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// Mismo criterio de popularidad que 02_synthetic_sessions.js.
function popularity(contentId) {
  let h = 0;
  for (const ch of contentId.toHexString()) h = (Math.imul(h, 31) + ch.charCodeAt(0)) >>> 0;
  return 1 / Math.pow(1 + (h % 100), 0.9);
}

const published = cine.contents.find({ status: 'PUBLISHED' }, { _id: 1, ageRating: 1 }).toArray()
  .map((c) => ({ id: c._id, ageRating: Number(c.ageRating), weight: popularity(c._id) }));
if (published.length === 0) {
  abort('No hay contenidos publicados. Cargue antes 01_demo_catalog.js o 03_volume_catalog.js.');
}

const byRating = {};
function allowedFor(maxAgeRating) {
  if (!byRating[maxAgeRating]) {
    byRating[maxAgeRating] = published.filter((c) => c.ageRating <= maxAgeRating);
  }
  return byRating[maxAgeRating];
}

// Elige k títulos distintos, con probabilidad proporcional a la popularidad.
function pickDistinct(r, list, k) {
  const pool = list.slice();
  const chosen = [];
  while (chosen.length < k && pool.length > 0) {
    const total = pool.reduce((sum, c) => sum + c.weight, 0);
    let x = r() * total;
    let index = 0;
    while (index < pool.length - 1 && x >= pool[index].weight) {
      x -= pool[index].weight;
      index += 1;
    }
    chosen.push(pool.splice(index, 1)[0].id);
  }
  return chosen;
}

// Listas hechas desde la aplicación: no se tocan.
const userLists = new Set(cine.my_lists.find({ synthetic: { $ne: true } }, { _id: 1 }).toArray().map((d) => Number(d._id)));

const now = new Date();
const operations = [];
let withList = 0;
let titles = 0;
let skippedUser = 0;
for (const viewer of VIEWERS) {
  const r = rng(viewer.profileId * 104729);
  if (r() >= 0.6) continue;                                   // ≈ 40 % sin lista
  if (userLists.has(viewer.profileId)) {
    skippedUser += 1;
    continue;
  }
  const allowed = allowedFor(viewer.maxAgeRating);
  if (allowed.length === 0) continue;
  const contentIds = pickDistinct(r, allowed, 1 + Math.floor(r() * 12));
  withList += 1;
  titles += contentIds.length;
  operations.push({
    replaceOne: {
      filter: { _id: NumberInt(viewer.profileId) },
      replacement: {
        _id: NumberInt(viewer.profileId),
        contentIds,
        updatedAt: new Date(now.getTime() - Math.floor(r() * 90) * 24 * 60 * 60 * 1000),
        synthetic: true,
      },
      upsert: true,
    },
  });
}

let removed = 0;
try {
  // Las sintéticas que ya no corresponden (p. ej., con una muestra de perfiles más pequeña).
  const keep = operations.map((op) => op.replaceOne.filter._id);
  removed = cine.my_lists.deleteMany({ synthetic: true, _id: { $nin: keep } }).deletedCount;
  for (let start = 0; start < operations.length; start += 1000) {
    cine.my_lists.bulkWrite(operations.slice(start, start + 1000), { ordered: true });
  }
} catch (error) {
  abort('No se pudieron guardar las listas: ' + error.message);
}

print(`Mi lista en ${DB_NAME}.my_lists: ${withList} listas sintéticas (${titles} títulos guardados), ${removed} borradas`);
print(`  perfiles exportados: ${VIEWERS.length}; con lista propia (no se tocaron): ${skippedUser}`);
print(`  total en la colección: ${cine.my_lists.countDocuments({})} listas`);
