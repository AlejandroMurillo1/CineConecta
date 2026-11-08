package com.cineconecta.platform.catalog.models;

public sealed interface ContentDetails permits MovieDetails, SeriesDetails, DocumentaryDetails {
}
