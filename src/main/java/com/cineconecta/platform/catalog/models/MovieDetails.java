package com.cineconecta.platform.catalog.models;

import java.util.List;

public record MovieDetails(
        int durationSeconds,
        List<String> directors,
        List<CastMember> cast
) {}
