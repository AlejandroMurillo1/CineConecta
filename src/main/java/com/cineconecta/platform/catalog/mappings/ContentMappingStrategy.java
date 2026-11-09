package com.cineconecta.platform.catalog.mappings;

import com.cineconecta.platform.catalog.ContentStatus;
import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.ContentMetadata;
import com.cineconecta.platform.catalog.dtos.CreateContentCommand;
import com.cineconecta.platform.catalog.dtos.UpdateContentCommand;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.models.ContentDetails;
import com.cineconecta.platform.catalog.utils.TextNormalizer;

import java.time.Instant;

public interface ContentMappingStrategy {

    ContentType getSupportedType();

    /** Comando de creación -> detalles del tipo. */
    ContentDetails mapDetails(CreateContentCommand command);

    /** Detalles actuales + comando de actualización -> detalles nuevos (puede devolver los actuales). */
    ContentDetails updateDetails(ContentDetails current, UpdateContentCommand command);

    /** Entidad -> comando precargado para el formulario de edición. */
    UpdateContentCommand toUpdateCommand(Content content);

    /** Reglas extra para publicar. Por defecto un contenido siempre es publicable. */
    default void assertPublishable(ContentDetails details) {

    }

    /** Plantilla de creación: datos comunes + detalles del tipo. */
    default Content mapToDomain(CreateContentCommand command) {
        Instant now = Instant.now();
        Content content = new Content();
        content.setType(getSupportedType());
        content.setStatus(ContentStatus.DRAFT);
        ContentMapper.copyMetadata(command.metadata(), content);
        content.setCreatedAt(now);
        content.setUpdatedAt(now);
        content.setContentDetails(mapDetails(command));
        return content;
    }

    /** Plantilla de actualización: datos comunes + detalles del tipo. */
    default void applyUpdate(Content content, UpdateContentCommand command) {
        ContentMapper.copyMetadata(command.metadata(), content);
        content.setContentDetails(updateDetails(content.getContentDetails(), command));
        content.setUpdatedAt(Instant.now());
    }
}
