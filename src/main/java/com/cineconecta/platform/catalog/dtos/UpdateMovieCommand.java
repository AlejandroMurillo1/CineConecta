package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.utils.DtoLists;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record UpdateMovieCommand(
        @NotNull @Valid ContentMetadata metadata,
        @Positive int durationSeconds,
        @NotEmpty List<String> directors,
        @NotEmpty List<@Valid CastMemberDto> cast
) implements UpdateContentCommand {

    public UpdateMovieCommand {
        directors = DtoLists.cleanTexts(directors);
        cast = DtoLists.immutable(cast);
    }

    @Override
    public ContentType getType() {
        return ContentType.MOVIE;
    }
}
