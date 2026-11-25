package com.cineconecta.platform.catalog.models;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.springframework.data.annotation.TypeAlias;

import java.util.List;

@TypeAlias("MOVIE")
public record MovieDetails(
        int durationSeconds,
        List<String> directors,
        List<CastMember> cast
) implements ContentDetails {}
