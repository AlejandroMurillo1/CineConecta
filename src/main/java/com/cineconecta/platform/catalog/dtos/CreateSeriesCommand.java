package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.utils.DtoLists;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record CreateSeriesCommand(
        @NotNull @Valid ContentMetadata metadata,
        @NotEmpty List<@Valid SeasonDto> seasons
) implements CreateContentCommand {

    public CreateSeriesCommand {
        seasons = DtoLists.immutable(seasons);
    }

    /** Regla: los números de temporada no se repiten dentro de la serie. */
    @AssertTrue(message = "No pueden existir temporadas con el mismo número en una serie")
    public boolean isSeasonNumbersUnique() {
        return DtoLists.hasUniqueKeys(seasons, SeasonDto::number);
    }

    @Override
    public ContentType getType() {
        return ContentType.SERIES;
    }
}

