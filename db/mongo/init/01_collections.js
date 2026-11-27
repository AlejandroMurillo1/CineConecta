// =============================================================================
// CineConecta · MongoDB · 01_collections.js
// Colecciones con validador $jsonSchema.
//
// Ejecutar con mongosh (local o Atlas):
//   mongosh "<cadena de conexión>" --file db/mongo/01_collections.js
// En Docker lo ejecuta el contenedor solo (docker-entrypoint-initdb.d).
//
// Colecciones: contents, my_lists, playback_sessions, admin_users.
// El validador de contents sigue el modelo que persiste el módulo catalog
// (Spring Data MongoDB): enums en inglés, metadatos en la raíz y los datos
// propios de cada tipo en el subdocumento polimórfico contentDetails.
// No hay playback_progress: "Continuar viendo" se calcula desde
// playback_sessions con el índice { profileId: 1, startedAt: -1 }.
// =============================================================================

const DB_NAME = (typeof process !== 'undefined' && (process.env.CINE_DB || process.env.MONGO_INITDB_DATABASE)) || 'cineconecta';
const cine = db.getSiblingDB(DB_NAME);

// ---------------------------------------------------------------- utilidades
const text = { bsonType: 'string', minLength: 1 };
const textList = (minItems) => ({ bsonType: 'array', minItems, uniqueItems: true, items: text });
const patternList = (minItems, pattern) => ({ bsonType: 'array', minItems, items: { bsonType: 'string', pattern } });
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
// contents — catálogo polimórfico (MOVIE, SERIES, DOCUMENTARY)
//
// Forma del documento (la escribe Spring Data desde la clase Content):
//   - metadatos comunes en la raíz (title, synopsis, year, ageRating, posterUrl,
//     genres, countries, audioLanguages, subtitleLanguages...), obligatorios desde
//     la creación: la aplicación no guarda borradores incompletos;
//   - contentDetails: subdocumento con los datos propios del tipo. Spring Data
//     agrega _class con el alias del tipo ("MOVIE", "SERIES", "DOCUMENTARY");
//   - version: control de concurrencia optimista (@Version);
//   - titleNormalized: título sin tildes ni mayúsculas, para buscar y para
//     impedir duplicados (título + año).
//
// Regla por estado: una serie en borrador puede quedar sin temporadas o con
// temporadas vacías; publicada o retirada debe tener al menos una temporada y
// cada temporada al menos un episodio.
// No se usa additionalProperties: false, para que el catálogo pueda evolucionar
// (campos nuevos + schemaVersion) y para admitir el campo _class de Spring Data.
// =============================================================================
const COUNTRY = '^[A-Z]{2}$';
const LANGUAGE = '^[a-z]{2,3}(-[A-Za-z]{2,4})?$';
const UUID = '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$';

const episode = {
  bsonType: 'object',
  required: ['episodeId', 'number', 'title', 'synopsis', 'durationSeconds'],
  properties: {
    episodeId: { bsonType: 'string', pattern: UUID, description: 'UUID generado por la aplicación; no cambia' },
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

const movieDetails = {
  bsonType: 'object',
  required: ['durationSeconds', 'directors', 'cast'],
  properties: {
    durationSeconds: positiveInt,
    directors: textList(1),
    cast: {
      bsonType: 'array',
      minItems: 1,
      items: {
        bsonType: 'object',
        required: ['name'],
        properties: { name: text, character: text },   // personaje: opcional ("cuando se conozca")
      },
    },
  },
};

const documentaryDetails = {
  bsonType: 'object',
  required: ['durationSeconds', 'directors', 'topics'],
  properties: {
    durationSeconds: positiveInt,       // una sola pieza
    directors: textList(1),
    topics: textList(1),
    interviewees: textList(0),          // opcional ("cuando corresponda")
    sources: textList(0),               // opcional: fuentes o instituciones participantes
  },
};

const seriesDetails = (minSeasons, minEpisodes) => ({
  bsonType: 'object',
  required: ['seasons'],
  properties: {
    seasons: { bsonType: 'array', minItems: minSeasons, items: seasonWith(minEpisodes) },
  },
});

const contentsSchema = {
  bsonType: 'object',
  title: 'Contenido del catálogo',
  required: ['type', 'status', 'title', 'titleNormalized', 'synopsis', 'year', 'ageRating', 'posterUrl',
    'genres', 'countries', 'audioLanguages', 'subtitleLanguages', 'schemaVersion', 'createdAt', 'updatedAt',
    'contentDetails'],
  properties: {
    _id: { bsonType: 'objectId' },
    version: { bsonType: ['int', 'long'], minimum: 0 },
    type: { enum: ['MOVIE', 'SERIES', 'DOCUMENTARY'] },
    status: { enum: ['DRAFT', 'PUBLISHED', 'RETIRED'] },
    title: { bsonType: 'string', minLength: 1, maxLength: 200 },
    titleNormalized: text,
    synopsis: { bsonType: 'string', minLength: 1, maxLength: 2000 },
    year: { bsonType: 'int', minimum: 1888, maximum: 2100 },
    ageRating: { bsonType: 'int', minimum: 0, maximum: 18 },
    posterUrl: { bsonType: 'string', pattern: '^https?://\\S+$' },
    genres: textList(1),
    countries: patternList(1, COUNTRY),
    audioLanguages: patternList(1, LANGUAGE),
    subtitleLanguages: patternList(0, LANGUAGE),
    schemaVersion: positiveInt,
    createdAt: { bsonType: 'date' },
    updatedAt: { bsonType: 'date' },
    contentDetails: { bsonType: 'object' },
  },
  // Los datos de contentDetails dependen del tipo (y, en series, del estado).
  anyOf: [
    { properties: { type: { enum: ['MOVIE'] }, contentDetails: movieDetails } },
    { properties: { type: { enum: ['DOCUMENTARY'] }, contentDetails: documentaryDetails } },
    { properties: { type: { enum: ['SERIES'] }, status: { enum: ['DRAFT'] }, contentDetails: seriesDetails(0, 0) } },
    { properties: { type: { enum: ['SERIES'] }, status: { enum: ['PUBLISHED', 'RETIRED'] }, contentDetails: seriesDetails(1, 1) } },
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
    episodeId: { bsonType: 'string', pattern: UUID, description: 'episodeId de contents (UUID)' },
    contentType: { enum: ['MOVIE', 'SERIES', 'DOCUMENTARY'] },
    durationSeconds: positiveInt,
    planIdAtStart: { bsonType: 'int', minimum: 1, description: 'planId de la suscripción vigente al iniciar' },
    startedAt: { bsonType: 'date' },
    endedAt: { bsonType: ['date', 'null'] },
    playingSince: { bsonType: ['date', 'null'], description: 'interno: inicio del tramo en reproducción; null en pausa' },
    watchedSeconds: { bsonType: 'int', minimum: 0, description: 'tiempo real visto, sin saltos' },
    lastPosition: { bsonType: 'int', minimum: 0 },
    finished: { bsonType: 'bool', description: 'el usuario la finalizó con el control "finalizar"' },
  },
  // Las series se reproducen por episodio: episodeId obligatorio solo en SERIES.
  anyOf: [
    { properties: { contentType: { enum: ['SERIES'] } }, required: ['episodeId'] },
    { properties: { contentType: { enum: ['MOVIE', 'DOCUMENTARY'] } } },
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
