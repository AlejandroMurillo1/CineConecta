package com.cineconecta.platform.catalog.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

public record CreateContent(
        @NotBlank String type,
        String status,
        @NotBlank String title,
        @NotBlank String synopsis,
        @Positive int year,
        @Positive int ageRating,
        String posterUrl,
        @NotEmpty String[] genres,
        @NotEmpty String[] countries,
        @NotEmpty String[] audioLanguages,
        String[] subtitleLanguages
) {}
