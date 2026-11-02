package com.cineconecta.platform.catalog.model;

public record Episode(
        String episodeId, int number, String title,
        String synopsis, int durationSeconds
) {
}
