package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.utils.DtoLists;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateEpisodeDto(
        @Positive int number,
        @NotBlank String title,
        @NotBlank String synopsis,
        @Positive int durationSeconds
) {
    public CreateEpisodeDto {
        title = DtoLists.strip(title);
        synopsis = DtoLists.strip(synopsis);
    }
}
