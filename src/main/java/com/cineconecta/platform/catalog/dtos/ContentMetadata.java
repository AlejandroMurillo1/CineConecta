package com.cineconecta.platform.catalog.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record ContentMetadata(
        @NotBlank String title,
        @NotBlank String synopsis,
        @Positive int year,
        @PositiveOrZero int ageRating,
        String posterUrl,
        @NotEmpty List<String> genres,
        @NotEmpty List<String> countries,
        @NotEmpty List<String> audioLanguages,
        List<String> subtitleLanguages
) {
}
