// =============================================================================
// CineConecta · MongoDB · db/demos/mongo/02_synthetic_sessions.js
// Sesiones de reproducción históricas bajo distintos planes, para que el
// informe de consumo (RF-34), "Continuar viendo" y las marcas de visto tengan
// datos en la demostración.
//
// De dónde sale cada dato:
//   - Perfiles, cuentas y suscripciones: PostgreSQL, vía 07_export_viewers.sql
//     (llega como `const VIEWERS = [...]` antes de este script).
//   - Contenidos, episodios y duraciones: la colección contents (solo PUBLISHED).
//   - planIdAtStart: el plan de la suscripción vigente en startedAt. Cada sesión
//     cae dentro de [startsAt, endsAt) de una suscripción de su cuenta, así que el
//     informe reparte el consumo por el plan que de verdad tenía el cliente.
//
// Reglas que respeta (las mismas de la aplicación y del validador):
//   - Solo contenidos con ageRating <= maxAgeRating del perfil.
//   - Las series se reproducen por episodio (episodeId obligatorio).
//   - Todas las sesiones están cerradas (endedAt), terminan antes de ahora y no
//     se superponen dentro de un mismo perfil.
//   - watchedSeconds <= durationSeconds y lastPosition <= durationSeconds.
//   - Mezcla de comportamientos: ~50 % vistas completas (finished), ~30 % a medias
//     (aparecen en "Continuar viendo") y ~20 % abandonadas al poco de empezar.
//
// Idempotente: borra solo los documentos marcados con synthetic: true y los
// vuelve a generar. Las sesiones creadas desde la aplicación no se tocan. Con los
// mismos datos de PostgreSQL produce las mismas sesiones (generador con semilla).
//
// Requisitos: 01_synthetic_billing.sql y/o 05_synthetic_accounts.sql en PostgreSQL
// y 01_demo_catalog.js en MongoDB.
//
// Uso (bash / Git Bash, desde la raíz del proyecto):
//
//   { docker compose exec -T postgres psql -U cine -d cineconecta -qAt -v ON_ERROR_STOP=1 \
//       < db/demos/postgres/07_export_viewers.sql
//     cat db/demos/mongo/02_synthetic_sessions.js
//   } | docker compose exec -T mongo mongosh --quiet -u cineOwner -p cine_owner_dev \
//       --authenticationDatabase cineconecta cineconecta
//
// Para borrar solo las sesiones sintéticas:
//   db.playback_sessions.deleteMany({ synthetic: true })
// =============================================================================

if (typeof VIEWERS === 'undefined') {
  throw new Error('Falta VIEWERS: ejecute este script precedido por la salida de '
    + 'db/demos/postgres/07_export_viewers.sql (ver el comando al inicio del archivo).');
}

const DB_NAME = (typeof process !== 'undefined' && (process.env.CINE_DB || process.env.MONGO_INITDB_DATABASE)) || 'cineconecta';
const cine = db.getSiblingDB(DB_NAME);

const MINUTE = 60 * 1000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
// Las sesiones terminan al menos una hora antes de ahora: ninguna queda "en curso".
const NOW = Date.now() - HOUR;
// Sesiones por día de suscripción y perfil (entre 0.25 y 0.55, según el perfil).
const MIN_RATE = 0.25;
const MAX_RATE = 0.55;

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
const between = (r, min, max) => min + r() * (max - min);
const pick = (r, list) => list[Math.floor(r() * list.length)];

// ---------------------------------------------------------------- catálogo reproducible
// Cada elemento es algo que se puede reproducir: una película, un documental o un episodio.
const playables = [];
cine.contents.find({ status: 'PUBLISHED' }).forEach((content) => {
  const details = content.contentDetails || {};
  if (content.type === 'SERIES') {
    (details.seasons || []).forEach((season) => (season.episodes || []).forEach((ep) => {
      if (ep.episodeId && ep.durationSeconds > 0) {
        playables.push({ contentId: content._id, type: 'SERIES', ageRating: content.ageRating,
          episodeId: ep.episodeId, durationSeconds: Number(ep.durationSeconds) });
      }
    }));
  } else if (details.durationSeconds > 0) {
    playables.push({ contentId: content._id, type: content.type, ageRating: content.ageRating,
      durationSeconds: Number(details.durationSeconds) });
  }
});
if (playables.length === 0) {
  throw new Error('No hay contenidos publicados con duración. Cargue antes db/demos/mongo/01_demo_catalog.js.');
}

// ---------------------------------------------------------------- generación
function sessionFor(r, viewer, planId, playable, startedAt, maxEnd) {
  const duration = playable.durationSeconds;
  const behaviour = r();
  let watched;
  let finished = false;
  if (behaviour < 0.5) {
    watched = duration;                                  // vista completa
    finished = true;
  } else if (behaviour < 0.8) {
    watched = Math.round(duration * between(r, 0.2, 0.85)); // a medias
  } else {
    watched = Math.round(duration * between(r, 0.02, 0.15)); // abandonada
  }
  const pauses = Math.round(between(r, 0, 10) * 60);
  let endedAt = startedAt + (watched + pauses) * 1000;
  if (endedAt > maxEnd) {
    // No cabe antes de la siguiente sesión: se recorta y deja de estar terminada.
    watched = Math.max(1, Math.floor((maxEnd - startedAt) / 1000) - pauses);
    watched = Math.min(watched, duration);
    finished = watched >= duration;
    endedAt = Math.min(maxEnd, startedAt + (watched + pauses) * 1000);
  }
  const doc = {
    accountId: NumberInt(viewer.accountId),
    profileId: NumberInt(viewer.profileId),
    contentId: playable.contentId,
    contentType: playable.type,
    durationSeconds: NumberInt(duration),
    planIdAtStart: NumberInt(planId),
    startedAt: new Date(startedAt),
    playingSince: null,
    endedAt: new Date(endedAt),
    lastActivityAt: new Date(endedAt),
    watchedSeconds: NumberInt(watched),
    lastPosition: NumberInt(watched),
    finished,
    revision: NumberInt(1),
    synthetic: true,
  };
  if (playable.episodeId) {
    doc.episodeId = playable.episodeId;
  }
  return doc;
}

const sessions = [];
let skippedProfiles = 0;
for (const viewer of VIEWERS) {
  const allowed = playables.filter((p) => p.ageRating <= viewer.maxAgeRating);
  if (allowed.length === 0) {
    skippedProfiles += 1;                                // p. ej. un perfil infantil de 0 años
    continue;
  }
  const r = rng(viewer.profileId * 7919);
  const rate = between(r, MIN_RATE, MAX_RATE);
  for (const sub of viewer.subscriptions || []) {
    const from = sub.startsAt;
    const to = Math.min(sub.endsAt, NOW);
    if (to - from < 3 * HOUR) {
      continue;
    }
    const count = Math.max(1, Math.round(((to - from) / DAY) * rate));
    // Inicios ordenados dentro de la vigencia, de noche (18:00 a 23:00 UTC-5 ≈ 23:00 a 04:00 UTC).
    const starts = [];
    for (let i = 0; i < count; i += 1) {
      const day = from + Math.floor(between(r, 0, (to - from) / DAY)) * DAY;
      const start = Math.max(from, day - (day % DAY) + 23 * HOUR + Math.floor(between(r, 0, 5 * 60)) * MINUTE);
      if (start < to - HOUR) {
        starts.push(start);
      }
    }
    starts.sort((a, b) => a - b);
    for (let i = 0; i < starts.length; i += 1) {
      const startedAt = starts[i];
      // La sesión termina antes de que empiece la siguiente del mismo perfil.
      const maxEnd = Math.min(i + 1 < starts.length ? starts[i + 1] - MINUTE : to, to);
      if (maxEnd - startedAt < 2 * MINUTE) {
        continue;
      }
      sessions.push(sessionFor(r, viewer, sub.planId, pick(r, allowed), startedAt, maxEnd));
    }
  }
}

const removed = cine.playback_sessions.deleteMany({ synthetic: true }).deletedCount;
if (sessions.length > 0) {
  cine.playback_sessions.insertMany(sessions, { ordered: true });
}

// ---------------------------------------------------------------- resumen
print(`Sesiones sintéticas en ${DB_NAME}.playback_sessions: ${removed} borradas, ${sessions.length} creadas`);
print(`  perfiles: ${VIEWERS.length - skippedProfiles} con sesiones, ${skippedProfiles} sin contenido apto para su clasificación`);
print('  por plan y tipo (planId · tipo · sesiones · minutos vistos):');
cine.playback_sessions.aggregate([
  { $match: { synthetic: true } },
  { $group: { _id: { plan: '$planIdAtStart', type: '$contentType' },
    sessions: { $sum: 1 }, seconds: { $sum: '$watchedSeconds' } } },
  { $sort: { '_id.plan': 1, '_id.type': 1 } },
]).forEach((row) => print(`    ${row._id.plan} · ${row._id.type.padEnd(11)} · ${String(row.sessions).padStart(4)} · ${Math.round(row.seconds / 60)}`));
const first = cine.playback_sessions.find({ synthetic: true }).sort({ startedAt: 1 }).limit(1).toArray()[0];
const last = cine.playback_sessions.find({ synthetic: true }).sort({ startedAt: -1 }).limit(1).toArray()[0];
if (first && last) {
  print(`  período cubierto: ${first.startedAt.toISOString().slice(0, 10)} a ${last.startedAt.toISOString().slice(0, 10)}`);
}
