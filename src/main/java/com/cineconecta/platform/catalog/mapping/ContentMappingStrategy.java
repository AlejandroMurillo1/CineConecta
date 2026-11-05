package com.cineconecta.platform.catalog.mapping;

import com.cineconecta.platform.catalog.ContentStatus;
import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.ContentMetadata;
import com.cineconecta.platform.catalog.dtos.CreateContentCommand;
import com.cineconecta.platform.catalog.models.Content;

import java.time.Instant;

public interface ContentMappingStrategy {
    ContentType getSupportedType();
    Content mapToDomain(CreateContentCommand command);
    default Content baseMapToDomain(ContentMetadata meta, ContentType type) {
        Content content = new Content();

        content.setType(type);
        content.setTitle(meta.title());
        content.setTitleNormalized(meta.title().toLowerCase()); // Lógica común opcional
        content.setSynopsis(meta.synopsis());
        content.setYear(meta.year());
        content.setAgeRating(meta.ageRating());
        content.setPosterUrl(meta.posterUrl());
        content.setGenres(meta.genres());
        content.setCountries(meta.countries());
        content.setAudioLanguages(meta.audioLanguages());
        content.setSubtitleLanguages(meta.subtitleLanguages());

        content.setStatus(ContentStatus.DRAFT);
        content.setCreatedAt(Instant.now());
        content.setUpdatedAt(Instant.now());

        return content;
    }
}
