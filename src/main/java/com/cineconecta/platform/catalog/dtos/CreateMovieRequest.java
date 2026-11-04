package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.models.CastMember;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.models.MovieDetails;
import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsonTypeName("MOVIE")
public record CreateMovieRequest(
        @NotBlank String title,
        @NotBlank String synopsis,
        @Positive int year,
        @Positive int ageRating,
        String posterUrl,
        @NotEmpty List<String> genres,
        @NotEmpty List<String> countries,
        @NotEmpty List<String> audioLanguages,
        List<String> subtitleLanguages,

        //Atributos de la película
        @Positive int durationSeconds,
        @NotEmpty List<String> directors,
        @NotEmpty Map<String,String> cast
) implements CreateContentRequest {}