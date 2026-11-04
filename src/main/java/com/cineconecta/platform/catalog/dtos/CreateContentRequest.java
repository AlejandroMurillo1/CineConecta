package com.cineconecta.platform.catalog.dtos;
import com.cineconecta.platform.catalog.models.Content;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type" // Lee el campo "type" del JSON raíz para saber qué DTO instanciar
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CreateMovieRequest.class, name = "MOVIE"),
        @JsonSubTypes.Type(value = CreateSeriesRequest.class, name = "SERIES"),
        @JsonSubTypes.Type(value = CreateDocumentaryRequest.class, name = "DOCUMENTARY")
})
public interface CreateContentRequest {}
