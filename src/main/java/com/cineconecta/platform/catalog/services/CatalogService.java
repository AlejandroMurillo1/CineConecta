package com.cineconecta.platform.catalog.services;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.CreateContentCommand;
import com.cineconecta.platform.catalog.mapping.ContentMappingStrategy;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.repositories.ContentRepository;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Getter
public class CatalogService {
    private final ContentRepository contentRepository;
    private final Map<ContentType, ContentMappingStrategy> mappingStrategies;

    @Autowired
    public CatalogService(Map<ContentType, ContentMappingStrategy> mappingStrategies, ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
        this.mappingStrategies = mappingStrategies;
    }

    @Transactional
    public Content createContent(final CreateContentCommand command) {
        ContentMappingStrategy strategy = mappingStrategies.get(command.getType());

        if (strategy == null) {
            throw new UnsupportedOperationException("No existe estrategia de mapeo para el tipo: " + command.getType().toString());
        }

        Content content = strategy.mapToDomain(command);
        boolean exists = contentRepository.findByTitleNormalized(content.getTitleNormalized()).isPresent();

        if(!exists) {
            return contentRepository.save(content);
        }

        throw new IllegalStateException("Content with title " + content.getTitleNormalized() + " already exists.");
    }
}
