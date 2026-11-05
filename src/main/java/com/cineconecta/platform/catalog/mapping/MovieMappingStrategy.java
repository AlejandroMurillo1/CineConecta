package com.cineconecta.platform.catalog.mapping;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.*;
import com.cineconecta.platform.catalog.models.CastMember;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.models.MovieDetails;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MovieMappingStrategy implements ContentMappingStrategy{
    @Override
    public ContentType getSupportedType() {
        return ContentType.MOVIE;
    }

    @Override
    public Content mapToDomain(CreateContentCommand command) {
        if (!(command instanceof CreateMovieCommand(
                ContentMetadata metadata, int durationSeconds, List<String> directors, Map<String, String> cast
        ))) {
            throw new IllegalArgumentException("Comando no soportado por MovieMappingStrategy");
        }

        Content content = baseMapToDomain(metadata, ContentType.MOVIE);
        List<CastMember> castMembers = cast.entrySet().stream()
                .map(e -> new CastMember(e.getKey(), e.getValue()))
                .toList();
        content.setContentDetails(new MovieDetails(durationSeconds, directors, castMembers));
        return content;
    }
}
