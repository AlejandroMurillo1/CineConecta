const samples = [
    {
        _id: ObjectId("65f1a2b3c4d5e6f708192a01"), version: NumberLong(0),
        type: "MOVIE", status: "PUBLISHED", title: "La última función del Rex",
        titleNormalized: "la ultima funcion del rex", synopsis: "El último proyeccionista de un cine de barrio intenta salvar la sala la noche antes de su demolición.",
        year: 2021, ageRating: 12, posterUrl: "https://example.com/posters/ultima-funcion-rex.jpg",
        genres: ["drama", "comedia"], countries: ["CO", "AR"], audioLanguages: ["es"], subtitleLanguages: ["en", "pt-BR"],
        schemaVersion: 1, createdAt: ISODate("2026-11-02T15:00:00Z"), updatedAt: ISODate("2026-11-02T15:00:00Z"),
        contentDetails: { _class: "MOVIE", durationSeconds: 6420, directors: ["Marcela Quintero"], cast: [
            { name: "Julián Restrepo", character: "Don Aurelio" },
            { name: "Camila Arboleda", character: "Lucía" },
            { name: "Tomás Ibarra" }
        ] }
    },
    {
        _id: ObjectId("65f1a2b3c4d5e6f708192a02"), version: NumberLong(2),
        type: "SERIES", status: "PUBLISHED", title: "Las casas del río",
        titleNormalized: "las casas del rio", synopsis: "Tres familias que comparten una ribera descubren que su historia común es menos inocente de lo que recuerdan.",
        year: 2023, ageRating: 16, posterUrl: "https://example.com/posters/casas-del-rio.jpg",
        genres: ["drama", "misterio"], countries: ["CO"], audioLanguages: ["es"], subtitleLanguages: ["en"],
        schemaVersion: 1, createdAt: ISODate("2026-11-02T15:10:00Z"), updatedAt: ISODate("2026-11-03T09:30:00Z"),
        contentDetails: { _class: "SERIES", seasons: [
            { number: 1, description: "La llegada de una carta antigua reabre viejas deudas.", episodes: [
                { episodeId: "0b7f3c52-6a1e-4d0b-9f43-2f1c8a5e7d01", number: 1, title: "La carta", synopsis: "Una carta sin remitente aparece en la casa más antigua de la ribera.", durationSeconds: 2760 },
                { episodeId: "0b7f3c52-6a1e-4d0b-9f43-2f1c8a5e7d02", number: 2, title: "Crecida", synopsis: "El río sube y obliga a las familias a convivir bajo un mismo techo.", durationSeconds: 2880 }
            ] },
            { number: 2, description: "Diez años después, el río devuelve lo que se llevó.", episodes: [
                { episodeId: "0b7f3c52-6a1e-4d0b-9f43-2f1c8a5e7d03", number: 1, title: "Retorno", synopsis: "Una de las hijas regresa sin explicar dónde estuvo.", durationSeconds: 3000 }
            ] }
        ] }
    },
    {
        _id: ObjectId("65f1a2b3c4d5e6f708192a03"), version: NumberLong(0),
        type: "DOCUMENTARY", status: "DRAFT", title: "Voces del Pacífico",
        titleNormalized: "voces del pacifico", synopsis: "Un recorrido por las comunidades costeras que preservan la música y la memoria oral del litoral.",
        year: 2024, ageRating: 0, posterUrl: "https://example.com/posters/voces-del-pacifico.jpg",
        genres: ["documental", "musica"], countries: ["CO", "EC"], audioLanguages: ["es"], subtitleLanguages: ["en", "fr"],
        schemaVersion: 1, createdAt: ISODate("2026-11-04T11:00:00Z"), updatedAt: ISODate("2026-11-04T11:00:00Z"),
        contentDetails: { _class: "DOCUMENTARY", durationSeconds: 4980, directors: ["Andrés Valencia", "Rosa Cuero"],
            topics: ["música tradicional", "memoria oral", "identidad afrocolombiana"],
            interviewees: ["Doña Etelvina Mosquera", "Maestro Lisandro Caicedo"],
            sources: ["Universidad del Pacífico", "Archivo Sonoro del Litoral"] }
    }
];

if (db.contents.countDocuments() === 0) {
    db.contents.insertMany(samples);
    print("catalog samples inserted");
} else {
    print("catalog samples skipped: contents is not empty");
}
