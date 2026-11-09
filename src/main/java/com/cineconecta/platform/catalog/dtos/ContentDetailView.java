package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.models.ContentDetails;

public record ContentDetailView(
        String id,
        ContentType type,
        ContentMetadata metadata,
        ContentDetails details
) {}
