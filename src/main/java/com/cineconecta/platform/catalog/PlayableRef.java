package com.cineconecta.platform.catalog;

public record PlayableRef(
        String contentId,
        String episodeId,
        ContentType type,
        String title,
        String episodeTitle,
        Integer seasonNumber,
        Integer episodeNumber,
        int durationSeconds,
        int ageRating,
        String posterUrl
) {}
