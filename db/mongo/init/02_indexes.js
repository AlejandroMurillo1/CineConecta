// =============================================================================
// CineConecta · MongoDB · 02_indexes.js
// Índices para las consultas principales. createIndex no falla si ya existe
// un índice idéntico, así que el script se puede volver a ejecutar.
// =============================================================================

const DB_NAME = (typeof process !== 'undefined' && (process.env.CINE_DB || process.env.MONGO_INITDB_DATABASE)) || 'cineconecta';
const cine = db.getSiblingDB(DB_NAME);

// ---------------------------------------------------------------- contents
// Búsqueda del cliente (módulo catalog): status = PUBLISHED y ageRating <= clasificación del
// perfil, con filtros opcionales de tipo, género, idioma de audio, año y título.
// Regla ESR (Equality, Sort, Range): primero las igualdades y al final el rango (ageRating).
cine.contents.createIndex({ status: 1, type: 1, ageRating: 1 }, { name: 'ix_contents_status_type_age' });

// Filtros por género e idioma de audio. Son arreglos (índices multikey); MongoDB no
// permite dos arreglos en un mismo índice compuesto, por eso van separados.
cine.contents.createIndex({ status: 1, genres: 1, ageRating: 1 }, { name: 'ix_contents_status_genres_age' });
cine.contents.createIndex({ status: 1, audioLanguages: 1, ageRating: 1 }, { name: 'ix_contents_status_audio_age' });

// Sin duplicados en el catálogo: un mismo título normalizado (sin tildes ni mayúsculas)
// no puede repetirse en el mismo año. La aplicación traduce la violación a un mensaje.
// También sirve a la búsqueda por título, que es un "contiene" sobre titleNormalized.
cine.contents.createIndex({ titleNormalized: 1, year: 1 }, { name: 'ux_contents_title_year', unique: true });

// Resolver un episodio por su identificador (reproducción, "Continuar viendo").
// Parcial: solo documentos que ya tienen episodios.
//
// NO es único a propósito. En un índice multikey, una temporada con `episodes: []` aporta
// una clave null; si dos series en borrador tienen una temporada vacía y otra con episodios,
// las dos generan esa clave null y el índice único rechaza la segunda (E11000), aunque no
// haya ningún episodeId repetido. La aplicación lo mostraba como "título repetido".
// La unicidad del episodeId la garantiza su origen: un UUID generado por la aplicación
// (UUID.randomUUID) que nunca se reutiliza (RI-07).
if (cine.contents.getIndexes().some((index) => index.name === 'ux_contents_episode_id')) {
  cine.contents.dropIndex('ux_contents_episode_id');   // versión anterior (única) de este índice
}
cine.contents.createIndex(
  { 'contentDetails.seasons.episodes.episodeId': 1 },
  {
    name: 'ix_contents_episode_id',
    partialFilterExpression: { 'contentDetails.seasons.episodes.episodeId': { $exists: true } },
  },
);

// ---------------------------------------------------------------- my_lists
// _id = profileId: la consulta "Mi lista del perfil" ya usa el índice de _id.

// ---------------------------------------------------------------- playback_sessions
// "Continuar viendo": igualdad por perfil + orden por inicio (regla ESR).
cine.playback_sessions.createIndex({ profileId: 1, startedAt: -1 }, { name: 'ix_sessions_profile_started' });

// Informes y analítica por período (sesiones iniciadas entre dos fechas).
cine.playback_sessions.createIndex({ startedAt: 1 }, { name: 'ix_sessions_started' });

// Analítica por contenido (finalización, bajo desempeño).
cine.playback_sessions.createIndex({ contentId: 1, startedAt: 1 }, { name: 'ix_sessions_content_started' });

// Cierre de sesiones abandonadas: abiertas (endedAt = null) e iniciadas hace tiempo.
cine.playback_sessions.createIndex({ endedAt: 1, startedAt: 1 }, { name: 'ix_sessions_open' });

// ---------------------------------------------------------------- admin_users
cine.admin_users.createIndex({ email: 1 }, { name: 'ux_admin_users_email', unique: true });

print('Índices creados en ' + DB_NAME);
