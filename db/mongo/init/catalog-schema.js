const INT = ["int", "long"];

const str = { bsonType: "string", minLength: 1 };
const strList = (minItems = 0) => ({
    bsonType: "array", minItems, uniqueItems: true, items: str,
});
const codeList = (pattern, minItems) => ({
    bsonType: "array", minItems, uniqueItems: true,
    items: { bsonType: "string", pattern },
});

// Idiomas: códigos en minúscula (es, en, pt-BR). Países: ISO-3166 alfa-2 en mayúscula (CO, US).
const LANG = "^[a-z]{2,3}(-[A-Za-z]{2,4})?$";
const COUNTRY = "^[A-Z]{2}$";
const UUID = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

// --- subdocumentos
const movie = {
    bsonType: "object",
    required: ["durationSeconds", "directors", "cast"],
    properties: {
        durationSeconds: { bsonType: INT, minimum: 1 },
        directors: strList(1),
        cast: {
            bsonType: "array", minItems: 1,
            items: {
                bsonType: "object",
                required: ["name"],
                properties: {
                    name: str,
                    character: { bsonType: ["string", "null"] },   // opcional: "cuando se conozca"
                },
            },
        },
    },
};

const episode = {
    bsonType: "object",
    required: ["episodeId", "number", "title", "synopsis", "durationSeconds"],
    properties: {
        episodeId: { bsonType: "string", pattern: UUID },
        number: { bsonType: INT, minimum: 1 },
        title: str,
        synopsis: str,
        durationSeconds: { bsonType: INT, minimum: 1 },
    },
};

const season = {
    bsonType: "object",
    required: ["number", "description", "episodes"],
    properties: {
        number: { bsonType: INT, minimum: 1 },
        description: str,
        episodes: { bsonType: "array", items: episode },   // puede estar vacío mientras es borrador
    },
};

const series = {
    bsonType: "object",
    required: ["seasons"],
    properties: {
        seasons: { bsonType: "array", items: season },     // puede estar vacío mientras es borrador
    },
};

const documentary = {
    bsonType: "object",
    required: ["durationSeconds", "directors", "topics"],
    properties: {
        durationSeconds: { bsonType: INT, minimum: 1 },
        directors: strList(1),
        topics: strList(1),
        interviewees: strList(0),     // opcional
        sources: strList(0),          // opcional: fuentes o instituciones participantes
    },
};

// Una rama por tipo: exige SU subdocumento y prohíbe los de los otros tipos.
const branch = (typeValue, key, schema, others) => ({
    properties: { type: { enum: [typeValue] }, [key]: schema },
    required: [key],
    not: { anyOf: others.map((k) => ({ required: [k] })) },
});

// --- esquema raíz ------------------------------------------------------------------
const schema = {
    bsonType: "object",
    required: [
        "type", "status", "title", "titleNormalized", "synopsis", "year", "ageRating",
        "posterUrl", "genres", "countries", "audioLanguages", "subtitleLanguages",
        "schemaVersion", "createdAt", "updatedAt",
    ],
    properties: {
        _id: { bsonType: "objectId" },
        _class: { bsonType: "string" },                    // lo escribe Spring Data (@TypeAlias)
        version: { bsonType: INT, minimum: 0 },             // @Version (bloqueo optimista)
        type: { enum: ["MOVIE", "SERIES", "DOCUMENTARY"] },
        status: { enum: ["DRAFT", "PUBLISHED", "RETIRED"] },
        title: str,
        titleNormalized: str,
        synopsis: str,
        year: { bsonType: INT, minimum: 1888, maximum: 2100 },
        ageRating: { bsonType: INT, minimum: 0, maximum: 21 },   // ajustar si el equipo define otra escala
        posterUrl: { bsonType: "string", pattern: "^https?://\\S+$" },
        genres: strList(1),
        countries: codeList(COUNTRY, 1),
        audioLanguages: codeList(LANG, 1),
        subtitleLanguages: codeList(LANG, 0),
        schemaVersion: { bsonType: INT, minimum: 1 },
        createdAt: { bsonType: "date" },
        updatedAt: { bsonType: "date" },
        movie, series, documentary,
    },
    // Exactamente un tipo válido.
    oneOf: [
        branch("MOVIE", "movie", movie, ["series", "documentary"]),
        branch("SERIES", "series", series, ["movie", "documentary"]),
        branch("DOCUMENTARY", "documentary", documentary, ["movie", "series"]),
    ],
};

// --- crear o actualizar la colección (idempotente) ------------------------------
const options = { validator: { $jsonSchema: schema }, validationLevel: "strict", validationAction: "error" };

if (db.getCollectionInfos({ name: "contents" }).length > 0) {
    db.runCommand({ collMod: "contents", ...options });
    print("contents: validador actualizado");
} else {
    db.createCollection("contents", options);
    print("contents: colección creada con validador");
}

// --- índices (hipótesis iniciales; se confirman o descartan con explain) ----------
// Patrón de consulta del usuario:
//   status = PUBLISHED, ageRating <= maxAge del perfil, filtros opcionales,
//   orden estable: year DESC, _id ASC, con skip/limit.
// _id va antes de ageRating para que el orden del índice coincida con el sort;
// ageRating, al final, se evalúa sobre las claves del índice sin leer el documento.
db.contents.createIndex(
    { status: 1, year: -1, _id: 1, ageRating: 1 },
    { name: "idx_browse" });
db.contents.createIndex(
    { status: 1, type: 1, year: -1, _id: 1, ageRating: 1 },
    { name: "idx_browse_type" });

// Un solo campo array por índice compuesto (limitación de los índices multikey).
db.contents.createIndex(
    { status: 1, genres: 1, year: -1, _id: 1 },
    { name: "idx_browse_genre" });
db.contents.createIndex(
    { status: 1, audioLanguages: 1, year: -1, _id: 1 },
    { name: "idx_browse_audio" });

// Búsqueda por título: sirve a prefijos (/^texto/) sobre el título normalizado.
db.contents.createIndex(
    { status: 1, titleNormalized: 1 },
    { name: "idx_title" });

print("contents: índices listos -> " + db.contents.getIndexes().map((i) => i.name).join(", "));