package com.cineconecta.platform.catalog.mapping;

import com.cineconecta.platform.catalog.ContentStatus;
import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.ContentMetadata;
import com.cineconecta.platform.catalog.dtos.CreateContentCommand;
import com.cineconecta.platform.catalog.dtos.CreateSeasonDto;
import com.cineconecta.platform.catalog.dtos.CreateSeriesCommand;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.models.Episode;
import com.cineconecta.platform.catalog.models.Season;
import com.cineconecta.platform.catalog.models.SeriesDetails;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class SeriesMappingStrategy implements ContentMappingStrategy{
    @Override
    public ContentType getSupportedType() {
        return ContentType.SERIES;
    }

    @Override
    public Content mapToDomain(CreateContentCommand command) {
        if (!(command instanceof
                CreateSeriesCommand(ContentMetadata metadata, List<CreateSeasonDto> seasons1)
        )) {
            throw new IllegalArgumentException("Comando no soportado por SeriesMapperStrategy");
        }

        Content content = baseMapToDomain(metadata, ContentType.SERIES);
        //Mapeo de los atributos concretos de la serie
        List<Season> seasons = seasons1.stream()
                .map(seasonDto -> new Season(
                        seasonDto.number(),
                        seasonDto.description(),
                        seasonDto.episodes().stream()
                                .map(epDto -> new Episode(
                                        UUID.randomUUID().toString(),
                                        epDto.number(),
                                        epDto.title(),
                                        epDto.synopsis(),
                                        epDto.durationSeconds()
                                )).toList()
                )).toList();

        //Asignarle los details al content
        content.setContentDetails(new SeriesDetails(seasons));

        return content;
    }
}
