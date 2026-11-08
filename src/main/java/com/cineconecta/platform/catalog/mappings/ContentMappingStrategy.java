package com.cineconecta.platform.catalog.mappings;

import com.cineconecta.platform.catalog.ContentStatus;
import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.ContentMetadata;
import com.cineconecta.platform.catalog.dtos.CreateContentCommand;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.models.ContentDetails;

import java.time.Instant;

public interface ContentMappingStrategy {

    ContentType getSupportedType();

    /** Convierte únicamente lo propio del tipo (los detalles). */
    ContentDetails mapDetails(CreateContentCommand command);

    /** Plantilla: datos comunes + detalles del tipo. */
    default Content mapToDomain(CreateContentCommand command) {
        ContentMetadata meta = command.metadata();
        Instant now = Instant.now();

        Content content = new Content();
        content.setType(getSupportedType());
        content.setStatus(ContentStatus.DRAFT);
        content.setTitle(meta.title());
        content.setTitleNormalized(TextNormalizer.normalize(meta.title()));
        content.setSynopsis(meta.synopsis());
        content.setYear(meta.year());
        content.setAgeRating(meta.ageRating());
        content.setPosterUrl(meta.posterUrl());
        content.setGenres(meta.genres());
        content.setCountries(meta.countries());
        content.setAudioLanguages(meta.audioLanguages());
        content.setSubtitleLanguages(meta.subtitleLanguages());
        content.setCreatedAt(now);
        content.setUpdatedAt(now);
        content.setContentDetails(mapDetails(command));
        return content;
    }
}
