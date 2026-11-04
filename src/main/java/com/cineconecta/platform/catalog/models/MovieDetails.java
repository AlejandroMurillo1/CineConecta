package com.cineconecta.platform.catalog.models;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;

@JsonTypeName("MOVIE")
public record MovieDetails(
        int durationSeconds,
        List<String> directors,
        List<CastMember> cast
) implements ContentDetails {}
