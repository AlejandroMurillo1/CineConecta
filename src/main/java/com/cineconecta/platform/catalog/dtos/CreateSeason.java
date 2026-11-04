package com.cineconecta.platform.catalog.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

public record CreateSeason(
        @Positive int seasonNumber,
        @NotBlank String description,
        @NotEmpty CreateEpisode[] episodes
) {
}
