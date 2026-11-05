package com.cineconecta.platform.catalog.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateSeasonDto(
        @Positive int number,
        @NotBlank String description,
        @NotEmpty List<@Valid CreateEpisodeDto> episodes
) {}
