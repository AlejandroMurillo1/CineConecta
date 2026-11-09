package com.cineconecta.platform.catalog.mappings;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.*;
import com.cineconecta.platform.catalog.models.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SeriesMappingStrategy implements ContentMappingStrategy {

    @Override
    public ContentType getSupportedType() {
        return ContentType.SERIES;
    }

    @Override
    public ContentDetails mapDetails(CreateContentCommand command) {
        if (!(command instanceof CreateSeriesCommand series)) {
            throw new IllegalArgumentException("Comando no soportado por SeriesMappingStrategy: "
                    + command.getClass().getSimpleName());
        }
        return new SeriesDetails(series.seasons().stream()
                .map(this::toSeason)
                .sorted(Comparator.comparingInt(Season::number))   // orden por número, no por orden del formulario
                .toList());
    }

    private Season toSeason(CreateSeasonDto dto) {
        return new Season(dto.number(), dto.description(), dto.episodes().stream()
                .map(this::toEpisode)
                .sorted(Comparator.comparingInt(Episode::number))
                .toList());
    }

    private Episode toEpisode(CreateEpisodeDto dto) {
        // El episodeId lo genera el servidor: es estable y lo referenciará el historial de reproducción.
        return new Episode(UUID.randomUUID().toString(), dto.number(), dto.title(),
                dto.synopsis(), dto.durationSeconds());
    }
}
