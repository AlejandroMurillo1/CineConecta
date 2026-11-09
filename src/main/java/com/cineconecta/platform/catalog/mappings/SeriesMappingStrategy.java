package com.cineconecta.platform.catalog.mappings;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.*;
import com.cineconecta.platform.catalog.models.*;
import org.springframework.stereotype.Component;

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
        return SeriesUtils.create(series.seasons());
    }

    @Override
    public ContentDetails updateDetails(ContentDetails current, UpdateContentCommand command) {
        if (!(command instanceof UpdateSeriesCommand)) {
            throw new IllegalArgumentException("Comando no soportado por SeriesMappingStrategy: "
                    + command.getClass().getSimpleName());
        }
        // Actualizar una serie NO toca su estructura: temporadas y episodios se gestionan con SeriesService.
        return current;
    }

    @Override
    public UpdateContentCommand toUpdateCommand(Content content) {
        return new UpdateSeriesCommand(ContentMapper.toMetadata(content));
    }

    @Override
    public void assertPublishable(ContentDetails details) {
        if (!(details instanceof SeriesDetails series)) {
            throw new IllegalStateException("Los detalles no corresponden a una serie");
        }
        SeriesUtils.assertPublishable(series);
    }
}
