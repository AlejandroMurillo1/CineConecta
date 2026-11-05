package com.cineconecta.platform.catalog.config;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.mapping.ContentMappingStrategy;
import com.cineconecta.platform.catalog.mapping.SeriesMappingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class CatalogConfig {
    @Bean
    public Map<ContentType, ContentMappingStrategy> mapperStrategiesMap(List<ContentMappingStrategy> strategies) {
        return strategies.stream()
                .collect(Collectors.toMap(
                        ContentMappingStrategy::getSupportedType,
                        Function.identity(),
                        (existing, replacement) -> {
                            throw new IllegalStateException("Conflicto: Ya existe un mapper para el tipo " + existing.getSupportedType());
                        }
                ));
    }
}
