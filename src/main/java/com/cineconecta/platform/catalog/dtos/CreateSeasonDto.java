package com.cineconecta.platform.catalog.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateSeasonDto(
        @Positive int number,
        @NotBlank String description,
        @NotEmpty List<@Valid CreateEpisodeDto> episodes
) {
    public CreateSeasonDto {
        description = DtoLists.strip(description);
        episodes = DtoLists.immutable(episodes);
    }

    /**
     * Regla: los números de episodio no se repiten dentro de la temporada.
     * Con @AssertTrue el fallo llega como error de validación (mensaje en el formulario);
     */
    @AssertTrue(message = "No pueden existir episodios con el mismo número en una temporada")
    public boolean isEpisodeNumbersUnique() {
        return DtoLists.hasUniqueKeys(episodes, CreateEpisodeDto::number);
    }
}
