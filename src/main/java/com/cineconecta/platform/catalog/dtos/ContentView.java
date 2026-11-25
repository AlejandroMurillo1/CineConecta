package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.ContentStatus;
import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.models.ContentDetails;

import java.time.Instant;

public record ContentView(
        String id,
        Long version,
        ContentType type,
        ContentStatus status,
        Instant createdAt,
        Instant updatedAt,
        ContentMetadata metadata,
        ContentDetails details
) {}
