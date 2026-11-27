package com.cineconecta.platform.catalog.dtos;

public record ContentEditModel(String id, Long version, UpdateContentCommand command, EpisodeDto episode) {
    public ContentEditModel(String id, Long version, UpdateContentCommand command) {
        this(id, version, command, null);
    }
}
