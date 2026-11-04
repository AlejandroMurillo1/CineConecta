package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.models.Content;
import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.Collections;
import java.util.List;

@JsonTypeName("DOCUMENTARY")
public record CreateDocumentaryRequest(
        @NotBlank String title,
        @NotBlank String synopsis,
        @Positive int year,
        @Positive int ageRating,
        String posterUrl,
        @NotEmpty List<String> genres,
        @NotEmpty List<String> countries,
        @NotEmpty List<String> audioLanguages,
        List<String> subtitleLanguages,

        @Positive int durationSeconds,
        @NotEmpty List<String> directors,
        @NotEmpty List<String> topics,
        List<String> interviewees,
        List<String> sources
) implements CreateContentRequest {}
