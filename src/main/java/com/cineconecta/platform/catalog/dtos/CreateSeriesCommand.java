package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateSeriesCommand(
        @NotNull ContentMetadata metadata,
        @NotEmpty List<CreateSeasonDto> seasons
) implements CreateContentCommand {

    @Override
    public ContentType getType() {
        return ContentType.SERIES;
    }
}

