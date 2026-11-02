package com.cineconecta.platform.catalog.model;

import java.util.List;

public record MovieDetails(
        int durationSeconds,
        List<String> directors,
        List<CastMember> cast
) {}
