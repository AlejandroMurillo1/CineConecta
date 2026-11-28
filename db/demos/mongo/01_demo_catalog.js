// =============================================================================
// CineConecta · MongoDB · db/demos/mongo/01_demo_catalog.js
// Catálogo de demostración: 5 contenidos con IDs fijos (para escribirlos en la
// pantalla de "Mi lista" durante la presentación).
//
// Requiere el validador del módulo catalog (rama fix/mongo-validator-catalog).
// Se puede ejecutar varias veces: reemplaza los mismos 5 documentos.
//
//   docker compose exec -T mongo mongosh -u cineOwner -p cine_owner_dev \
//     --authenticationDatabase cineconecta cineconecta < db/demos/mongo/01_demo_catalog.js
//
// | ID (termina en) | Tipo        | Título                  | Clasif. | Estado    |
// |-----------------|-------------|-------------------------|---------|-----------|
// | ...0001         | SERIES      | Río Abajo               | 13      | PUBLISHED |
// | ...0002         | MOVIE       | Noche en San Antonio    | 16      | PUBLISHED |
// | ...0003         | DOCUMENTARY | Páramos: agua que nace  | 7       | PUBLISHED |
// | ...0004         | MOVIE       | La última función       | 7       | PUBLISHED |
// | ...0005         | MOVIE       | Proyecto sin terminar   | 7       | DRAFT     |
// =============================================================================

const DB_NAME = (typeof process !== 'undefined' && (process.env.CINE_DB || process.env.MONGO_INITDB_DATABASE)) || 'cineconecta';
const cine = db.getSiblingDB(DB_NAME);
const now = new Date();

function content(id, type, status, title, titleNormalized, year, ageRating, genres, contentDetails) {
  return {
    _id: ObjectId(id),
    _class: 'Content',
    version: NumberLong(0),
    type,
    status,
    title,
    titleNormalized,
    synopsis: `Sinopsis de demostración de «${title}».`,
    year: NumberInt(year),
    ageRating: NumberInt(ageRating),
    // Afiches servidos por la aplicación (src/main/resources/static/images/posters/).
    posterUrl: `/images/posters/${id}.svg`,
    genres,
    countries: ['CO'],
    audioLanguages: ['es'],
    subtitleLanguages: ['en'],
    schemaVersion: NumberInt(1),
    createdAt: now,
    updatedAt: now,
    contentDetails,
  };
}

const episode = (episodeId, number, title, minutes) => ({
  episodeId, number: NumberInt(number), title, synopsis: `Episodio ${number}.`, durationSeconds: NumberInt(minutes * 60),
});

const movie = (minutes, director, cast) => ({
  _class: 'MOVIE', durationSeconds: NumberInt(minutes * 60), directors: [director], cast,
});

const docs = [
  content('6740a1f0c3b2a1d4e5f60001', 'SERIES', 'PUBLISHED', 'Río Abajo', 'rio abajo', 2024, 13, ['Drama'], {
    _class: 'SERIES',
    seasons: [
      { number: NumberInt(1), description: 'La llegada de la draga', episodes: [
        episode('3f6c2a1e-8b4d-4c1a-9e2f-7a5b6c8d9e01', 1, 'Aguas bajas', 45),
        episode('9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c02', 2, 'La subasta', 47),
      ] },
      { number: NumberInt(2), description: 'El puerto vacío', episodes: [
        episode('5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b03', 1, 'Corriente arriba', 44),
      ] },
    ],
  }),
  content('6740a1f0c3b2a1d4e5f60002', 'MOVIE', 'PUBLISHED', 'Noche en San Antonio', 'noche en san antonio', 2023, 16, ['Suspenso'],
    movie(104, 'Laura Méndez', [{ name: 'Andrés Cárdenas', character: 'Tomás' }, { name: 'María Ospina' }])),
  content('6740a1f0c3b2a1d4e5f60003', 'DOCUMENTARY', 'PUBLISHED', 'Páramos: agua que nace', 'paramos agua que nace', 2022, 7, ['Naturaleza'], {
    _class: 'DOCUMENTARY', durationSeconds: NumberInt(55 * 60), directors: ['Camilo Rojas'],
    topics: ['Agua', 'Páramos'], interviewees: ['Lucía Pardo'], sources: ['Instituto de investigación ambiental'],
  }),
  content('6740a1f0c3b2a1d4e5f60004', 'MOVIE', 'PUBLISHED', 'La última función', 'la ultima funcion', 2021, 7, ['Comedia'],
    movie(92, 'Sofía Herrera', [{ name: 'Daniel Vélez', character: 'El proyeccionista' }])),
  content('6740a1f0c3b2a1d4e5f60005', 'MOVIE', 'DRAFT', 'Proyecto sin terminar', 'proyecto sin terminar', 2026, 7, ['Drama'],
    movie(80, 'Sin asignar', [{ name: 'Por definir' }])),
];

// Leído por stdin, mongosh sigue con la siguiente sentencia aunque una falle: sin el
// try/quit(1) imprimiría "cargado" aunque el validador hubiera rechazado los documentos.
try {
  for (const doc of docs) {
    cine.contents.replaceOne({ _id: doc._id }, doc, { upsert: true });
    print(`  ${doc._id.toString()}  ${doc.type.padEnd(11)} ${doc.status.padEnd(9)} ${doc.title}`);
  }
} catch (error) {
  print(`ERROR: no se pudo cargar el catálogo de demostración: ${error.message}`);
  if (error.errInfo) {
    printjson(error.errInfo);
  }
  quit(1);
}
print(`Catálogo de demostración cargado en ${DB_NAME}: ${docs.length} contenidos.`);
