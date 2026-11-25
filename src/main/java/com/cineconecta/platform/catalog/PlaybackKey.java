package com.cineconecta.platform.catalog;

public record PlaybackKey(
        String contentId,
        String episodeId
) {
    public static PlaybackKey of(String contentId) {
        return new PlaybackKey(contentId, null);
    }
}
