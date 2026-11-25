package com.cineconecta.platform.catalog.models;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.springframework.data.annotation.TypeAlias;

import java.util.List;

@TypeAlias("SERIES")
public record SeriesDetails(List<Season> seasons) implements ContentDetails {}
