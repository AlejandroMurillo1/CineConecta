package com.cineconecta.platform.catalog.mappings;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.*;
import com.cineconecta.platform.catalog.models.CastMember;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.models.ContentDetails;
import com.cineconecta.platform.catalog.models.MovieDetails;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MovieMappingStrategy implements ContentMappingStrategy {

    @Override
    public ContentType getSupportedType() {
        return ContentType.MOVIE;
    }

    @Override
    public ContentDetails mapDetails(CreateContentCommand command) {
        if (!(command instanceof CreateMovieCommand movie)) {
            throw new IllegalArgumentException("Comando no soportado por MovieMappingStrategy: "
                    + command.getClass().getSimpleName());
        }
        return new MovieDetails(movie.durationSeconds(), movie.directors(), toCast(movie.cast()));
    }

    @Override
    public ContentDetails updateDetails(ContentDetails current, UpdateContentCommand command) {
        if (!(command instanceof UpdateMovieCommand movie)) {
            throw new IllegalArgumentException("Comando no soportado por MovieMappingStrategy: "
                    + command.getClass().getSimpleName());
        }
        // Una película se reemplaza completa: no hay identificadores internos que conservar.
        return new MovieDetails(movie.durationSeconds(), movie.directors(), toCast(movie.cast()));
    }

    @Override
    public UpdateContentCommand toUpdateCommand(Content content) {
        if (!(content.getContentDetails() instanceof MovieDetails details)) {
            throw new IllegalStateException("El contenido " + content.getId() + " no tiene detalles de película");
        }
        List<CastMemberDto> cast = details.cast().stream()
                .map(member -> new CastMemberDto(member.name(), member.character()))
                .toList();
        return new UpdateMovieCommand(ContentMapper.toMetadata(content),
                details.durationSeconds(), details.directors(), cast);
    }

    private static List<CastMember> toCast(List<CastMemberDto> cast) {
        return cast.stream().map(member -> new CastMember(member.name(), member.character())).toList();
    }
}
