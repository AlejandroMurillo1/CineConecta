// =============================================================================
// CineConecta · MongoDB · 02_indexes.js
// Índices para las consultas principales. createIndex no falla si ya existe
// un índice idéntico, así que el script se puede volver a ejecutar.
// =============================================================================

const DB_NAME = (typeof process !== 'undefined' && (process.env.CINE_DB || process.env.MONGO_INITDB_DATABASE)) || 'cineconecta';
const cine = db.getSiblingDB(DB_NAME);

// ---------------------------------------------------------------- contents
// Búsqueda por título (palabras, con reglas del español).
cine.contents.createIndex({ title: 'text' }, { name: 'tx_contents_title', default_language: 'spanish' });

// Búsqueda del cliente: solo publicados, filtrando por tipo y año.
// También sirve al listado del administrador (estado + tipo).
cine.contents.createIndex({ status: 1, type: 1, releaseYear: -1 }, { name: 'ix_contents_status_type_year' });

// Filtros por género e idioma. Son arreglos (índices multikey); MongoDB no
// permite dos arreglos en un mismo índice compuesto, por eso van separados.
cine.contents.createIndex({ status: 1, genres: 1 }, { name: 'ix_contents_status_genres' });
cine.contents.createIndex({ status: 1, audioLanguages: 1 }, { name: 'ix_contents_status_audio' });
cine.contents.createIndex({ status: 1, subtitleLanguages: 1 }, { name: 'ix_contents_status_subtitles' });

// Resolver un episodio por su identificador y garantizar que no se repita
// entre series. Parcial: solo documentos que ya tienen episodios.
cine.contents.createIndex(
  { 'seasons.episodes.episodeId': 1 },
  { name: 'ux_contents_episode_id', unique: true, partialFilterExpression: { 'seasons.episodes.episodeId': { $exists: true } } },
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
