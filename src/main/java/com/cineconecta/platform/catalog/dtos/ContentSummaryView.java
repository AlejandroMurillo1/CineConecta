package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.ContentStatus;
import com.cineconecta.platform.catalog.ContentType;

import java.time.Instant;

public record ContentSummaryView(
        String id,
        ContentType type,
        ContentStatus status,
        String title,
        int year,
        int ageRating,
        String posterUrl,
        Instant updatedAt
) {}
