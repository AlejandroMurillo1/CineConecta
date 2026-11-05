package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.ContentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

public record CreateMovieCommand(
        @NotNull @Valid ContentMetadata metadata,
        @Positive int durationSeconds,
        @NotEmpty List<String> directors,
        @NotEmpty Map<String,String> cast
) implements CreateContentCommand {
    @Override
    public ContentType getType() {
        return ContentType.MOVIE;
    }
}
