package com.cineconecta.platform.catalog.models;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;

@JsonTypeName("SERIES")
public record SeriesDetails(List<Season> seasons) implements ContentDetails {}
