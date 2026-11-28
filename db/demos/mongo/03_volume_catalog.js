// =============================================================================
// CineConecta · MongoDB · db/demos/mongo/03_volume_catalog.js
// VOLUMEN del catálogo: N contenidos generados (por defecto 150), para que la
// búsqueda, los filtros, los informes y las mediciones de índices trabajen con un
// catálogo realista y no solo con los 5 títulos de 01_demo_catalog.js.
//
// Qué genera (proporciones aproximadas):
//   - 50 % películas, 30 % series, 20 % documentales.
//   - 85 % publicados, 8 % borradores, 7 % retirados.
//   - Series de 1 a 4 temporadas con 4 a 10 episodios (las series en borrador
//     pueden quedar sin temporadas o con temporadas vacías, como permite el validador).
//   - Clasificación 0, 7, 12, 16 o 18; años de 1975 a 2026 (más recientes que antiguos);
//     1 a 3 géneros; varios países e idiomas de audio y subtítulos.
//   - Afiche según el primer género (/images/posters/genre-*.svg).
//
// Determinista e idempotente: el contenido i siempre tiene el mismo _id, título y
// episodios (generador con semilla), y se vuelve a escribir con replaceOne. Por eso
// las sesiones y las listas que apuntan a estos contenidos siguen siendo válidas al
// volver a correrlo. Los documentos llevan synthetic: true. Nunca borra contenidos
// (si se corre con un N menor, los de más quedan; los contenidos no se borran).
//
// Uso (desde la raíz del proyecto; N opcional, por defecto 150):
//   docker compose exec -T -e CONTENIDOS=150 mongo mongosh --quiet -u cineOwner -p cine_owner_dev \
//       --authenticationDatabase cineconecta cineconecta < db/demos/mongo/03_volume_catalog.js
// =============================================================================

const DB_NAME = (typeof process !== 'undefined' && (process.env.CINE_DB || process.env.MONGO_INITDB_DATABASE)) || 'cineconecta';
const N = Number((typeof process !== 'undefined' && process.env.CONTENIDOS) || 150);
const cine = db.getSiblingDB(DB_NAME);

// Leído por stdin, mongosh sigue con la siguiente sentencia aunque una falle: ante un
// error se usa quit(1) para no imprimir un resumen falso.
function abort(message) {
  print('ERROR: ' + message);
  quit(1);
}
if (!Number.isInteger(N) || N < 1 || N > 100000) {
  abort('CONTENIDOS debe ser un entero entre 1 y 100000 (recibido: ' + N + ').');
}

// ---------------------------------------------------------------- generador con semilla
// mulberry32: mismo resultado para la misma semilla, sin depender de Math.random().
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
const pick = (r, list) => list[Math.floor(r() * list.length)];
const intBetween = (r, min, max) => min + Math.floor(r() * (max - min + 1));
function pickSome(r, list, min, max) {
  const pool = list.slice();
  const out = [];
  const count = Math.min(pool.length, intBetween(r, min, max));
  for (let i = 0; i < count; i += 1) {
    out.push(pool.splice(Math.floor(r() * pool.length), 1)[0]);
  }
  return out;
}
function weighted(r, entries) {               // entries: [[valor, peso], ...]
  const total = entries.reduce((sum, e) => sum + e[1], 0);
  let x = r() * total;
  for (const [value, weight] of entries) {
    x -= weight;
    if (x < 0) return value;
  }
  return entries[entries.length - 1][0];
}

// Igual que TextNormalizer de la aplicación: sin tildes, minúsculas, espacios simples.
const normalize = (text) => text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim().replace(/\s+/g, ' ');
const slug = (text) => normalize(text).replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
const hex = (value, width) => value.toString(16).padStart(width, '0');

// ---------------------------------------------------------------- vocabulario
const FICTION_GENRES = ['Drama', 'Comedia', 'Suspenso', 'Acción', 'Romance', 'Ciencia ficción',
  'Terror', 'Animación', 'Familiar', 'Crimen', 'Aventura', 'Fantasía'];
const DOC_GENRES = ['Naturaleza', 'Historia', 'Sociedad', 'Música', 'Deportes', 'Ciencia'];
const COUNTRIES = ['CO', 'CO', 'CO', 'MX', 'AR', 'ES', 'CL', 'PE', 'US', 'FR', 'BR'];
const FIRST = ['Laura', 'Andrés', 'Camila', 'Diego', 'Valentina', 'Santiago', 'Mariana', 'Julián', 'Sofía',
  'Mateo', 'Paula', 'Nicolás', 'Daniela', 'Sebastián', 'Natalia', 'Felipe', 'Carolina', 'Tomás'];
const LAST = ['Méndez', 'Cárdenas', 'Ospina', 'Herrera', 'Vélez', 'Rojas', 'Pardo', 'Quintero', 'Arboleda',
  'Restrepo', 'Salazar', 'Mejía', 'Castaño', 'Zuluaga', 'Moreno', 'Ibarra', 'Londoño', 'Duque'];
const CHARACTERS = ['el capitán', 'la doctora', 'el forastero', 'la maestra', 'el abuelo', 'la detective',
  'el músico', 'la alcaldesa', 'el pescador', 'la periodista'];
const TITLE_A = ['El silencio', 'La noche', 'Los caminos', 'El regreso', 'Las voces', 'El jardín', 'La herencia',
  'El viaje', 'La frontera', 'Los hijos', 'La memoria', 'El faro', 'La ciudad', 'El invierno', 'La promesa',
  'Los secretos', 'La tormenta', 'El eco', 'La casa', 'El tiempo'];
const TITLE_B = ['del río', 'de los Andes', 'de la montaña', 'del puerto', 'sin nombre', 'de papel',
  'del desierto', 'de cristal', 'de Bogotá', 'del Caribe', 'de medianoche', 'del sur', 'de los abuelos',
  'de humo', 'del valle', 'de sal', 'del amanecer', 'de piedra'];
const DOC_A = ['Voces', 'Crónica', 'Huellas', 'Memorias', 'Rutas', 'Territorios', 'Guardianes', 'Sonidos'];
const DOC_B = ['del Pacífico', 'de la Amazonía', 'del café', 'de la salsa', 'del páramo', 'de la Sierra Nevada',
  'del fútbol', 'de la ciencia', 'del Caribe', 'de los volcanes', 'del Magdalena', 'de la ciudad'];
const TOPICS = ['Agua', 'Biodiversidad', 'Memoria histórica', 'Migración', 'Música popular', 'Deporte',
  'Cambio climático', 'Ciudad', 'Comunidades indígenas', 'Ciencia', 'Gastronomía', 'Arte'];
const EPISODE_WORDS = ['El comienzo', 'La deuda', 'Marea alta', 'El testigo', 'Cruce de caminos', 'La carta',
  'Tierra firme', 'El juicio', 'Puertas cerradas', 'La huida', 'Fuego cruzado', 'El acuerdo', 'Luz de luna',
  'La despedida'];

const person = (r) => `${pick(r, FIRST)} ${pick(r, LAST)}`;
const ratingFor = (r, genres) => {
  if (genres.includes('Animación') || genres.includes('Familiar')) return weighted(r, [[0, 4], [7, 4], [12, 1]]);
  if (genres.includes('Terror')) return weighted(r, [[16, 3], [18, 3]]);
  return weighted(r, [[0, 1], [7, 2], [12, 3], [16, 3], [18, 2]]);
};

// ---------------------------------------------------------------- títulos únicos
const usedTitles = new Set(cine.contents.find({ synthetic: { $ne: true } }, { titleNormalized: 1 })
  .toArray().map((c) => c.titleNormalized));
function uniqueTitle(r, partsA, partsB) {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    const candidate = `${pick(r, partsA)} ${pick(r, partsB)}`;
    if (!usedTitles.has(normalize(candidate))) {
      usedTitles.add(normalize(candidate));
      return candidate;
    }
  }
  // Combinaciones agotadas (N grande): se numera.
  for (let n = 2; ; n += 1) {
    const candidate = `${pick(r, partsA)} ${pick(r, partsB)} ${n}`;
    if (!usedTitles.has(normalize(candidate))) {
      usedTitles.add(normalize(candidate));
      return candidate;
    }
  }
}

// ---------------------------------------------------------------- generación
const NOW = Date.now();
const DAY = 24 * 60 * 60 * 1000;
const docs = [];
const stats = { MOVIE: 0, SERIES: 0, DOCUMENTARY: 0, PUBLISHED: 0, DRAFT: 0, RETIRED: 0, episodes: 0 };

for (let i = 1; i <= N; i += 1) {
  const r = rng(i * 2654435761);
  const id = ObjectId('6750a0000000000000' + hex(i, 6));
  const type = weighted(r, [['MOVIE', 50], ['SERIES', 30], ['DOCUMENTARY', 20]]);
  const status = weighted(r, [['PUBLISHED', 85], ['DRAFT', 8], ['RETIRED', 7]]);
  const genres = type === 'DOCUMENTARY' ? pickSome(r, DOC_GENRES, 1, 2) : pickSome(r, FICTION_GENRES, 1, 3);
  const title = type === 'DOCUMENTARY' ? uniqueTitle(r, DOC_A, DOC_B) : uniqueTitle(r, TITLE_A, TITLE_B);
  // Más títulos recientes que antiguos.
  const year = 2026 - Math.floor(Math.pow(r(), 2.2) * 52);
  const ageRating = type === 'DOCUMENTARY' ? weighted(r, [[0, 3], [7, 4], [12, 2]]) : ratingFor(r, genres);
  const createdAt = new Date(NOW - intBetween(r, 1, 730) * DAY);

  let contentDetails;
  if (type === 'MOVIE') {
    const cast = [];
    for (let c = 0, count = intBetween(r, 1, 4); c < count; c += 1) {
      cast.push(r() < 0.7 ? { name: person(r), character: pick(r, CHARACTERS) } : { name: person(r) });
    }
    contentDetails = {
      _class: 'MOVIE',
      durationSeconds: NumberInt(intBetween(r, 80, 150) * 60),
      directors: [...new Set([person(r), ...(r() < 0.15 ? [person(r)] : [])])],
      cast: cast.filter((m, idx, all) => all.findIndex((o) => o.name === m.name) === idx),
    };
  } else if (type === 'DOCUMENTARY') {
    contentDetails = {
      _class: 'DOCUMENTARY',
      durationSeconds: NumberInt(intBetween(r, 45, 110) * 60),
      directors: [person(r)],
      topics: pickSome(r, TOPICS, 1, 3),
      interviewees: [...new Set(Array.from({ length: intBetween(r, 0, 3) }, () => person(r)))],
      sources: r() < 0.5 ? ['Universidad Icesi', 'Archivo histórico regional'].slice(0, intBetween(r, 1, 2)) : [],
    };
  } else {
    // Publicada o retirada: 1 a 4 temporadas con episodios. Borrador: puede estar incompleta.
    const draft = status === 'DRAFT';
    const seasonCount = draft ? intBetween(r, 0, 2) : intBetween(r, 1, 4);
    const seasons = [];
    for (let s = 1; s <= seasonCount; s += 1) {
      const episodeCount = draft ? intBetween(r, 0, 4) : intBetween(r, 4, 10);
      const episodes = [];
      for (let e = 1; e <= episodeCount; e += 1) {
        episodes.push({
          // UUID estable: depende solo del contenido, la temporada y el episodio.
          episodeId: `${hex(i, 8)}-${hex(s, 4)}-4${hex(e, 3)}-8000-5eed${hex(i * 1000 + s * 100 + e, 8)}`,
          number: NumberInt(e),
          title: `${pick(r, EPISODE_WORDS)}`,
          synopsis: `Temporada ${s}, episodio ${e} de «${title}».`,
          durationSeconds: NumberInt(intBetween(r, 22, 58) * 60),
        });
      }
      stats.episodes += episodes.length;
      seasons.push({ number: NumberInt(s), description: `Temporada ${s} de «${title}».`, episodes });
    }
    contentDetails = { _class: 'SERIES', seasons };
  }

  stats[type] += 1;
  stats[status] += 1;
  docs.push({
    _id: id,
    _class: 'Content',
    version: NumberLong(0),
    type,
    status,
    title,
    titleNormalized: normalize(title),
    synopsis: `${genres.join(', ')}. Sinopsis generada de «${title}» para el volumen de pruebas.`,
    year: NumberInt(year),
    ageRating: NumberInt(ageRating),
    posterUrl: `/images/posters/genre-${slug(genres[0])}.svg`,
    genres,
    countries: pickSome(r, COUNTRIES, 1, 2).filter((c, idx, all) => all.indexOf(c) === idx),
    audioLanguages: weighted(r, [[['es'], 60], [['es', 'en'], 25], [['en'], 10], [['pt', 'es'], 5]]),
    subtitleLanguages: weighted(r, [[[], 15], [['es'], 25], [['en'], 20], [['es', 'en'], 30], [['es', 'en', 'pt'], 10]]),
    schemaVersion: NumberInt(1),
    createdAt,
    updatedAt: createdAt,
    contentDetails,
    synthetic: true,
  });
}

// ---------------------------------------------------------------- escritura
try {
  for (let start = 0; start < docs.length; start += 500) {
    cine.contents.bulkWrite(docs.slice(start, start + 500).map((doc) => ({
      replaceOne: { filter: { _id: doc._id }, replacement: doc, upsert: true },
    })), { ordered: true });
  }
} catch (error) {
  print(`ERROR: no se pudo cargar el catálogo de volumen: ${error.message}`);
  if (error.errInfo) printjson(error.errInfo);
  if (error.writeErrors && error.writeErrors.length) printjson(error.writeErrors[0].err || error.writeErrors[0]);
  quit(1);
}

print(`Catálogo de volumen en ${DB_NAME}.contents: ${docs.length} contenidos generados`);
print(`  tipos: ${stats.MOVIE} películas · ${stats.SERIES} series (${stats.episodes} episodios) · ${stats.DOCUMENTARY} documentales`);
print(`  estados: ${stats.PUBLISHED} publicados · ${stats.DRAFT} borradores · ${stats.RETIRED} retirados`);
print(`  total en la colección: ${cine.contents.countDocuments({})} contenidos`);
