package com.cineconecta.platform.catalog.services;

import com.cineconecta.platform.catalog.ContentStatus;
import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.*;
import com.cineconecta.platform.catalog.exceptions.ContentRuleException;
import com.cineconecta.platform.catalog.exceptions.DuplicateContentException;
import com.cineconecta.platform.catalog.mappings.ContentMapper;
import com.cineconecta.platform.catalog.mappings.ContentMappingStrategy;
import com.cineconecta.platform.catalog.models.Content;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.Map;

@Service
@Validated
public class CatalogService {

    private final ContentStore store;
    private final Map<ContentType, ContentMappingStrategy> mappingStrategies;

    @Autowired
    public CatalogService(Map<ContentType, ContentMappingStrategy> mappingStrategies, ContentStore store) {
        this.mappingStrategies = mappingStrategies;
        this.store = store;
    }

    // ------------------------------------------------------------------ crear
    public ContentView createContent(@NotNull @Valid CreateContentCommand command) {
        Content content = strategyFor(command.getType()).mapToDomain(command);
        assertNotDuplicate(content);
        return ContentMapper.toView(store.save(content));
    }

    // ------------------------------------------------------------------ leer
    public ContentView getContent(String id) {
        return ContentMapper.toView(store.get(id));
    }

    /** Comando precargado + versión, para mostrar el formulario de edición. */
    public ContentEditModel getEditModel(String id) {
        Content content = store.get(id);
        return new ContentEditModel(content.getId(), content.getVersion(),
                strategyFor(content.getType()).toUpdateCommand(content));
    }

    /** Listado de administración; status y type son filtros opcionales (null = todos). */
    public Page<ContentSummaryView> listContents(ContentStatus status, ContentType type, Pageable pageable) {
        return store.list(status, type, pageable).map(ContentMapper::toSummary);
    }

    // ------------------------------------------------------------------ actualizar
    public ContentView updateContent(String id, long expectedVersion,
                                     @NotNull @Valid UpdateContentCommand command) {
        Content content = store.getForUpdate(id, expectedVersion);
        if (command.getType() != content.getType()) {
            throw new ContentRuleException("El tipo de un contenido no puede cambiar");
        }
        strategyFor(content.getType()).applyUpdate(content, command);
        assertNotDuplicate(content);
        return ContentMapper.toView(store.save(content));
    }

    // ------------------------------------------------------------------ publicar y retirar
    /** BORRADOR -> PUBLICADO, si cumple las reglas de publicación de su tipo. */
    public ContentView publishContent(String id, long expectedVersion) {
        Content content = store.getForUpdate(id, expectedVersion);
        if (content.getStatus() != ContentStatus.DRAFT) {
            throw new ContentRuleException("Solo se puede publicar un contenido en borrador (estado actual: "
                    + content.getStatus() + ")");
        }
        strategyFor(content.getType()).assertPublishable(content.getContentDetails());
        content.setStatus(ContentStatus.PUBLISHED);
        content.setUpdatedAt(Instant.now());
        return ContentMapper.toView(store.save(content));
    }

    /**
     * BORRADOR o PUBLICADO -> RETIRADO. Es definitivo y no borra nada: el historial conserva sus
     * referencias y el identificador no se reutiliza. Un contenido retirado ya no admite cambios.
     */
    public ContentView retireContent(String id, long expectedVersion) {
        Content content = store.getForUpdate(id, expectedVersion);
        content.setStatus(ContentStatus.RETIRED);
        content.setUpdatedAt(Instant.now());
        return ContentMapper.toView(store.save(content));
    }

    // ------------------------------------------------------------------ internos
    private ContentMappingStrategy strategyFor(ContentType type) {
        ContentMappingStrategy strategy = mappingStrategies.get(type);
        if (strategy == null) {
            throw new IllegalStateException("No existe estrategia de mapeo para el tipo: " + type);
        }
        return strategy;
    }

    /**
     * Regla propia del equipo (el enunciado NO exige títulos únicos): misma obra = mismo título + año.
     * Para no imponerla: borrar este método, sus dos llamadas, existsOtherWithSameTitleAndYear del
     * store, los dos exists del repositorio y la rama DuplicateKeyException de ContentStore.
     */
    private void assertNotDuplicate(Content content) {
        if (store.existsOtherWithSameTitleAndYear(content)) {
            throw new DuplicateContentException(content.getTitle(), content.getYear());
        }
    }
}
