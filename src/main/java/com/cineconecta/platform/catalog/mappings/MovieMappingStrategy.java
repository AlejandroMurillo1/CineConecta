package com.cineconecta.platform.catalog.mappings;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.*;
import com.cineconecta.platform.catalog.models.CastMember;
import com.cineconecta.platform.catalog.models.ContentDetails;
import com.cineconecta.platform.catalog.models.MovieDetails;
import org.springframework.stereotype.Component;

@Component
public class MovieMappingStrategy implements ContentMappingStrategy {

    @Override
    public ContentType getSupportedType() {
        return ContentType.MOVIE;
    }

    @Override
    public ContentDetails mapDetails(CreateContentCommand command) {
        // instanceof simple (no deconstrucción del record): añadir un campo al comando no rompe esto.
        if (!(command instanceof CreateMovieCommand movie)) {
            throw new IllegalArgumentException("Comando no soportado por MovieMappingStrategy: "
                    + command.getClass().getSimpleName());
        }
        return new MovieDetails(
                movie.durationSeconds(),
                movie.directors(),
                movie.cast().stream()
                        .map(member -> new CastMember(member.name(), member.character()))
                        .toList());
    }
}
