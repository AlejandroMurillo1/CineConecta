package com.cineconecta.platform.catalog.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

public record CreateDocumentary(
        CreateContent baseContent,
        @Positive int durationSeconds,
        @NotEmpty String[] directors,
        @NotEmpty String[] topics,
        String[] interviewees,
        String[] sources
) {
}
