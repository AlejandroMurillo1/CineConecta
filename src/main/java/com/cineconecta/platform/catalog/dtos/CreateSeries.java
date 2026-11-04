package com.cineconecta.platform.catalog.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateSeries(
        @NotNull CreateContent baseContent,
        @NotEmpty CreateSeason[] seasons
) {
}
