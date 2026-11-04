package com.cineconecta.platform.catalog.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.Map;

public record CreateMovie(
        CreateContent baseContent,
        @Positive int durationSeconds,
        @NotEmpty String[] directors,
        @NotEmpty Map<String, String> castMembers
) {}