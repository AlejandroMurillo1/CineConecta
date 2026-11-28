package com.cineconecta.platform.catalog.config;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.mappings.ContentMappingStrategy;
import com.cineconecta.platform.catalog.models.DocumentaryDetails;
import com.cineconecta.platform.catalog.models.MovieDetails;
import com.cineconecta.platform.catalog.models.SeriesDetails;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
@EnableMethodSecurity
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

    /**
     * Registra las implementaciones concretas de {@code ContentDetails} en {@link MongoMappingContext}
     * para que Spring Data MongoDB resuelva sus {@code @TypeAlias} al deserializar documentos.
     */
    @Bean
    public static BeanPostProcessor contentDetailsMongoEntitiesRegistrar() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof MongoMappingContext context) {
                    context.getPersistentEntity(MovieDetails.class);
                    context.getPersistentEntity(SeriesDetails.class);
                    context.getPersistentEntity(DocumentaryDetails.class);
                }
                return bean;
            }
        };
    }
}
