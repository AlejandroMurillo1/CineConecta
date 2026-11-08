package com.cineconecta.platform.catalog.services;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.CreateContentCommand;
import com.cineconecta.platform.catalog.exceptions.DuplicateContentException;
import com.cineconecta.platform.catalog.mappings.ContentMappingStrategy;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.repositories.ContentRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Service
@Validated
public class CatalogService {

    private final ContentRepository contentRepository;
    private final Map<ContentType, ContentMappingStrategy> mappingStrategies;

    @Autowired
    public CatalogService(Map<ContentType, ContentMappingStrategy> mappingStrategies,
                          ContentRepository contentRepository) {
        this.mappingStrategies = mappingStrategies;
        this.contentRepository = contentRepository;
    }

    public Content createContent(@NotNull @Valid CreateContentCommand command) {
        Content content = strategyFor(command.getType()).mapToDomain(command);

        if (contentRepository.existsByTitleNormalizedAndYear(content.getTitleNormalized(), content.getYear())) {
            throw new DuplicateContentException(content.getTitle(), content.getYear());
        }

        try {
            return contentRepository.save(content);
        } catch (DuplicateKeyException e) {
            // Solo ocurre si se activa el índice único ux_title_year (peticiones simultáneas).
            throw new DuplicateContentException(content.getTitle(), content.getYear());
        }
    }

    private ContentMappingStrategy strategyFor(ContentType type) {
        ContentMappingStrategy strategy = mappingStrategies.get(type);
        if (strategy == null) {
            throw new IllegalStateException("No existe estrategia de mapeo para el tipo: " + type);
        }
        return strategy;
    }
}
