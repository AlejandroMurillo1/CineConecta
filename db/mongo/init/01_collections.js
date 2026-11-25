// =============================================================================
// CineConecta · MongoDB · 01_collections.js
// Colecciones con validador $jsonSchema.
//
// Ejecutar con mongosh (local o Atlas):
//   mongosh "<cadena de conexión>" --file db/mongo/01_collections.js
// En Docker lo ejecuta el contenedor solo (docker-entrypoint-initdb.d).
//
// Colecciones: contents, my_lists, playback_sessions, admin_users.
// No hay playback_progress: "Continuar viendo" se calcula desde
// playback_sessions con el índice { profileId: 1, startedAt: -1 }.
// =============================================================================

const DB_NAME = (typeof process !== 'undefined' && (process.env.CINE_DB || process.env.MONGO_INITDB_DATABASE)) || 'cineconecta';
const cine = db.getSiblingDB(DB_NAME);

// ---------------------------------------------------------------- utilidades
const text = { bsonType: 'string', minLength: 1 };
const textList = (minItems) => ({ bsonType: 'array', minItems, uniqueItems: true, items: text });
const positiveInt = { bsonType: 'int', minimum: 1 };

function createWithValidator(name, schema) {
  if (cine.getCollectionNames().includes(name)) {
    cine.runCommand({ collMod: name, validator: { $jsonSchema: schema }, validationLevel: 'strict', validationAction: 'error' });
    print(`  ${name}: validador actualizado`);
  } else {
    cine.createCollection(name, { validator: { $jsonSchema: schema }, validationLevel: 'strict', validationAction: 'error' });
    print(`  ${name}: creada`);
  }
}

// =============================================================================
// contents — catálogo polimórfico (película, serie, documental)
//
// Regla: un BORRADOR solo exige lo mínimo para guardarse. Para estar PUBLICADO
// o RETIRADO, el documento debe cumplir los obligatorios de su tipo
// (anyOf de abajo). Así, "publicar" equivale a pasar esta validación.
// No se usa additionalProperties: false, para que el catálogo pueda evolucionar
// (campos nuevos + schemaVersion).
// =============================================================================
const episode = {
  bsonType: 'object',
  required: ['episodeId', 'number', 'title', 'synopsis', 'durationSeconds'],
  properties: {
    episodeId: { bsonType: 'objectId' },
    number: positiveInt,
    title: text,
    synopsis: text,
    durationSeconds: positiveInt,
  },
};

const seasonWith = (minEpisodes) => ({
  bsonType: 'object',
  required: ['number', 'description', 'episodes'],
  properties: {
    number: positiveInt,
    description: text,
    episodes: { bsonType: 'array', minItems: minEpisodes, items: episode },
  },
});

const contentProperties = {
  _id: { bsonType: 'objectId' },
  type: { enum: ['PELICULA', 'SERIE', 'DOCUMENTAL'] },
  title: text,
  synopsis: text,
  releaseYear: { bsonType: 'int', minimum: 1888, maximum: 2100 },
  genres: textList(0),
  countries: textList(0),
  audioLanguages: textList(0),
  subtitleLanguages: textList(0),
  ageRating: { bsonType: 'int', minimum: 0, maximum: 18 },
  coverUrl: { bsonType: 'string', pattern: '^https?://' },
  status: { enum: ['BORRADOR', 'PUBLICADO', 'RETIRADO'] },
  schemaVersion: positiveInt,
  createdAt: { bsonType: 'date' },
  updatedAt: { bsonType: 'date' },
  publishedAt: { bsonType: ['date', 'null'] },
  retiredAt: { bsonType: ['date', 'null'] },
  // película y documental
  durationSeconds: positiveInt,
  directors: textList(0),
  // película
  cast: {
    bsonType: 'array',
    items: {
      bsonType: 'object',
      required: ['person'],
      properties: { person: text, character: text },   // personaje: opcional
    },
  },
  // documental (todos opcionales: "cuando corresponda")
  topics: textList(0),
  interviewees: textList(0),
  sources: {
    bsonType: 'array',
    items: {
      bsonType: 'object',
      required: ['name'],
      properties: { name: text, description: text },
    },
  },
  // serie
  seasons: { bsonType: 'array', items: seasonWith(0) },
};

const COMMON_REQUIRED = ['synopsis', 'releaseYear', 'genres', 'countries', 'audioLanguages',
  'subtitleLanguages', 'ageRating', 'coverUrl'];
const COMPLETE_COMMON = {
  genres: textList(1),
  countries: textList(1),
  audioLanguages: textList(1),
  // subtitleLanguages puede quedar vacío (decisión abierta del equipo).
};

const contentsSchema = {
  bsonType: 'object',
  title: 'Contenido del catálogo',
  required: ['type', 'title', 'status', 'schemaVersion', 'createdAt', 'updatedAt'],
  properties: contentProperties,
  anyOf: [
    // 1) Borrador: basta con lo de arriba.
    { properties: { status: { enum: ['BORRADOR'] } } },
    // 2) Película completa.
    {
      properties: { ...COMPLETE_COMMON, type: { enum: ['PELICULA'] }, directors: textList(1), cast: { bsonType: 'array', minItems: 1 } },
      required: [...COMMON_REQUIRED, 'durationSeconds', 'directors', 'cast'],
    },
    // 3) Documental completo (una sola pieza).
    {
      properties: { ...COMPLETE_COMMON, type: { enum: ['DOCUMENTAL'] }, directors: textList(1) },
      required: [...COMMON_REQUIRED, 'durationSeconds', 'directors'],
    },
    // 4) Serie completa: al menos una temporada, cada una con al menos un episodio.
    {
      properties: { ...COMPLETE_COMMON, type: { enum: ['SERIE'] }, seasons: { bsonType: 'array', minItems: 1, items: seasonWith(1) } },
      required: [...COMMON_REQUIRED, 'seasons'],
    },
  ],
};

// =============================================================================
// my_lists — un documento por perfil; referencias simples a contents.
// _id = profileId (entero que nace en PostgreSQL).
// =============================================================================
const myListsSchema = {
  bsonType: 'object',
  title: 'Mi lista de un perfil',
  required: ['_id', 'contentIds', 'updatedAt'],
  properties: {
    _id: { bsonType: 'int', minimum: 1, description: 'profileId de PostgreSQL' },
    contentIds: { bsonType: 'array', uniqueItems: true, items: { bsonType: 'objectId' } },
    updatedAt: { bsonType: 'date' },
  },
};

// =============================================================================
// playback_sessions — historial inmutable una vez cerrada la sesión.
// contentType, durationSeconds y planIdAtStart son fotos del momento de inicio.
// =============================================================================
const playbackSessionsSchema = {
  bsonType: 'object',
  title: 'Sesión de reproducción',
  required: ['accountId', 'profileId', 'contentId', 'contentType', 'durationSeconds', 'planIdAtStart',
    'startedAt', 'endedAt', 'watchedSeconds', 'lastPosition', 'finished'],
  properties: {
    _id: { bsonType: 'objectId' },
    accountId: { bsonType: 'int', minimum: 1, description: 'accountId de PostgreSQL' },
    profileId: { bsonType: 'int', minimum: 1, description: 'profileId de PostgreSQL' },
    contentId: { bsonType: 'objectId' },
    episodeId: { bsonType: 'objectId' },
    contentType: { enum: ['PELICULA', 'SERIE', 'DOCUMENTAL'] },
    durationSeconds: positiveInt,
    planIdAtStart: { bsonType: 'int', minimum: 1, description: 'planId de la suscripción vigente al iniciar' },
    startedAt: { bsonType: 'date' },
    endedAt: { bsonType: ['date', 'null'] },
    playingSince: { bsonType: ['date', 'null'], description: 'interno: inicio del tramo en reproducción; null en pausa' },
    watchedSeconds: { bsonType: 'int', minimum: 0, description: 'tiempo real visto, sin saltos' },
    lastPosition: { bsonType: 'int', minimum: 0 },
    finished: { bsonType: 'bool', description: 'el usuario la finalizó con el control "finalizar"' },
  },
  // Las series se reproducen por episodio: episodeId obligatorio solo en SERIE.
  anyOf: [
    { properties: { contentType: { enum: ['SERIE'] } }, required: ['episodeId'] },
    { properties: { contentType: { enum: ['PELICULA', 'DOCUMENTAL'] } } },
  ],
};

// =============================================================================
// admin_users — administradores, independientes de las cuentas de clientes.
// =============================================================================
const adminUsersSchema = {
  bsonType: 'object',
  title: 'Usuario administrador',
  required: ['name', 'email', 'passwordHash', 'createdAt'],
  properties: {
    _id: { bsonType: 'objectId' },
    name: text,
    email: { bsonType: 'string', pattern: '^[^A-Z\\s@]+@[^A-Z\\s@]+$', description: 'en minúsculas' },
    passwordHash: { bsonType: 'string', pattern: '^\\$2[aby]\\$\\d{2}\\$.{53}$', description: 'hash BCrypt' },
    createdAt: { bsonType: 'date' },
  },
};

print(`Base de datos: ${DB_NAME}`);
createWithValidator('contents', contentsSchema);
createWithValidator('my_lists', myListsSchema);
createWithValidator('playback_sessions', playbackSessionsSchema);
createWithValidator('admin_users', adminUsersSchema);
