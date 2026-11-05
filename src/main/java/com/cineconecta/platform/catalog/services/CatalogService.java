package com.cineconecta.platform.catalog.services;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.CreateContentCommand;
import com.cineconecta.platform.catalog.mapping.ContentMappingStrategy;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.repositories.ContentRepository;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Getter
public class CatalogService {
    private final ContentRepository contentRepository;
    private final Map<ContentType, ContentMappingStrategy> mappingStrategies;

    @Autowired
    public CatalogService(List<ContentMappingStrategy> mappingStrategies, ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
        this.mappingStrategies = mappingStrategies.stream()
                .collect(Collectors.toMap(ContentMappingStrategy::getSupportedType, Function.identity()));
    }

    public Content createContent(final CreateContentCommand command) {
        ContentMappingStrategy strategy = mappingStrategies.get(command.getType());

        if (strategy == null) {
            throw new UnsupportedOperationException("No existe estrategia de mapeo para el tipo: " + command.getType().toString());
        }
        Content content = strategy.mapToDomain(command);
        return contentRepository.save(content);
    }
}
