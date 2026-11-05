package com.cineconecta.platform.catalog.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateEpisodeDto(
        @Positive int number,
        @NotBlank String title,
        @NotBlank String synopsis,
        @Positive int durationSeconds
) {}
