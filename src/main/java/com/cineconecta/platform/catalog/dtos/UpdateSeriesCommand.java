package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.ContentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdateSeriesCommand(
        @NotNull @Valid ContentMetadata metadata
) implements UpdateContentCommand {

    @Override
    public ContentType getType() {
        return ContentType.SERIES;
    }
}
