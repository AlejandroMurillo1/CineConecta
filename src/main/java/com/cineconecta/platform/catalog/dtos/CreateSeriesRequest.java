package com.cineconecta.platform.catalog.dtos;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

@JsonTypeName("SERIES")
public record CreateSeriesRequest(
        @NotBlank String title,
        @NotBlank String synopsis,
        @Positive int year,
        @Positive int ageRating,
        String posterUrl,
        @NotEmpty List<String> genres,
        @NotEmpty List<String> countries,
        @NotEmpty List<String> audioLanguages,
        List<String> subtitleLanguages,

        @NotEmpty List<CreateSeasonDto> seasons
) implements CreateContentRequest {}

record CreateSeasonDto(
        @Positive int number,
        @NotBlank String description,
        @NotEmpty List<CreateEpisodeDto> episodes
) {}

record CreateEpisodeDto(
        @Positive int number,
        @NotBlank String title,
        @NotBlank String synopsis,
        @Positive int durationSeconds
) {}
