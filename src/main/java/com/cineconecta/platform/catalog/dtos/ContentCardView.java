package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.ContentType;

import java.util.List;

public record ContentCardView(
        String id,
        String title,
        ContentType type,
        int year,
        int ageRating,
        String posterUrl,
        List<String> genres
) {}
