package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.utils.DtoLists;
import jakarta.validation.constraints.NotBlank;

public record CastMemberDto(@NotBlank String name, String character) {

    public CastMemberDto {
        name = DtoLists.strip(name);
        character = (character == null || character.isBlank()) ? null : character.strip();
    }
}
