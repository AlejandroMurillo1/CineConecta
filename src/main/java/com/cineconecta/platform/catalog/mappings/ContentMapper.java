package com.cineconecta.platform.catalog.mappings;

import com.cineconecta.platform.catalog.dtos.*;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.utils.TextNormalizer;

public final class ContentMapper {
    private ContentMapper() {}

    /** Metadatos -> entidad (creación y actualización). */
    public static void copyMetadata(ContentMetadata meta, Content target) {
        target.setTitle(meta.title());
        target.setTitleNormalized(TextNormalizer.normalize(meta.title()));
        target.setSynopsis(meta.synopsis());
        target.setYear(meta.year());
        target.setAgeRating(meta.ageRating());
        target.setPosterUrl(meta.posterUrl());
        target.setGenres(meta.genres());
        target.setCountries(meta.countries());
        target.setAudioLanguages(meta.audioLanguages());
        target.setSubtitleLanguages(meta.subtitleLanguages());
    }

    /** Entidad -> metadatos (precarga de formularios y vistas). */
    public static ContentMetadata toMetadata(Content c) {
        return new ContentMetadata(c.getTitle(), c.getSynopsis(), c.getYear(), c.getAgeRating(),
                c.getPosterUrl(), c.getGenres(), c.getCountries(), c.getAudioLanguages(),
                c.getSubtitleLanguages());
    }

    public static ContentView toView(Content c) {
        return new ContentView(c.getId(), c.getVersion(), c.getType(), c.getStatus(),
                c.getCreatedAt(), c.getUpdatedAt(), toMetadata(c), c.getContentDetails());
    }

    public static ContentSummaryView toSummary(Content c) {
        return new ContentSummaryView(c.getId(), c.getType(), c.getStatus(), c.getTitle(), c.getYear(),
                c.getAgeRating(), c.getPosterUrl(), c.getUpdatedAt());
    }

    /**
     * Tarjeta del buscador. Se construye a partir de una entidad PARCIAL (el repositorio de búsqueda solo
     * trae estos campos), así que aquí no se puede leer nada más: ni estado, ni detalles.
     */
    public static ContentCardView toCard(Content c) {
        return new ContentCardView(c.getId(), c.getTitle(), c.getType(), c.getYear(), c.getAgeRating(),
                c.getPosterUrl(), c.getGenres());
    }

    /** Detalle para el usuario: sin versión, estado ni fechas (son internos de la administración). */
    public static ContentDetailView toDetail(Content c) {
        return new ContentDetailView(c.getId(), c.getType(), toMetadata(c), c.getContentDetails());
    }
}
