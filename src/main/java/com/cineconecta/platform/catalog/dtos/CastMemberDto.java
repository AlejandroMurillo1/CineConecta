package com.cineconecta.platform.catalog.dtos;

import jakarta.validation.constraints.NotBlank;

public record CastMemberDto(@NotBlank String name, String character) {

    public CastMemberDto {
        name = DtoLists.strip(name);
        character = (character == null || character.isBlank()) ? null : character.strip();
    }
}
