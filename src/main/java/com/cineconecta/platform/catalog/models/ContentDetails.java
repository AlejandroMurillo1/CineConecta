package com.cineconecta.platform.catalog.models;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = MovieDetails.class, name = "MOVIE"),
        @JsonSubTypes.Type(value = SeriesDetails.class, name = "SERIES"),
        @JsonSubTypes.Type(value = DocumentaryDetails.class, name = "DOCUMENTARY")
})
public sealed interface ContentDetails permits MovieDetails, SeriesDetails, DocumentaryDetails {
}
