package com.cineconecta.platform.catalog.models;

public record Episode(
        String episodeId, int number, String title,
        String synopsis, int durationSeconds
) {
}
