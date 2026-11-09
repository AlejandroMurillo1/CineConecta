package com.cineconecta.platform.catalog;

public record PlayableRef(String contentId, String episodeId, ContentType type,
                          int durationSeconds, int ageRating) {
}
