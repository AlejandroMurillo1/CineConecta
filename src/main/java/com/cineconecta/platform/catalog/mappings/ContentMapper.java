package com.cineconecta.platform.catalog.mappings;

import com.cineconecta.platform.catalog.dtos.ContentMetadata;
import com.cineconecta.platform.catalog.dtos.ContentSummaryView;
import com.cineconecta.platform.catalog.dtos.ContentView;
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
}
