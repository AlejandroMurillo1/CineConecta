package com.cineconecta.platform.catalog.dtos;

import jakarta.validation.constraints.*;

import java.util.List;

public record ContentMetadata(
        @NotBlank String title,
        @NotBlank String synopsis,
        @Positive int year,
        @Min(0) @Max(18) int ageRating,
        String posterUrl,
        @NotEmpty List<String> genres,
        @NotEmpty List<String> countries,
        @NotEmpty List<String> audioLanguages,
        List<String> subtitleLanguages
) {
}
