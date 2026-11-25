package com.cineconecta.platform.catalog;

public record ContentSummary(String id, String title, ContentType type,
                             int ageRating, ContentStatus status, String posterUrl) {
}
