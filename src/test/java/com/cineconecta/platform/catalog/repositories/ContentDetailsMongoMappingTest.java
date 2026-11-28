package com.cineconecta.platform.catalog.repositories;

import com.cineconecta.platform.catalog.config.CatalogConfig;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.models.DocumentaryDetails;
import com.cineconecta.platform.catalog.models.MovieDetails;
import com.cineconecta.platform.catalog.models.SeriesDetails;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ContentDetailsMongoMappingTest {

    @Test
    void readsPolymorphicContentDetailsUsingTypeAliases() {
        MongoCustomConversions conversions = MongoCustomConversions.create(_ -> {});
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.setInitialEntitySet(Set.of(Content.class));
        context.afterPropertiesSet();

        BeanPostProcessor registrar = CatalogConfig.contentDetailsMongoEntitiesRegistrar();
        registrar.postProcessAfterInitialization(context, "mongoMappingContext");

        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();

        Document movieDoc = new Document("_id", "m1")
                .append("type", "MOVIE")
                .append("title", "Test Movie")
                .append("contentDetails", new Document("_class", "MOVIE")
                        .append("durationSeconds", 5400)
                        .append("directors", List.of("Director"))
                        .append("cast", List.of(new Document("name", "Actor").append("character", "Role"))));

        Document seriesDoc = new Document("_id", "s1")
                .append("type", "SERIES")
                .append("title", "Test Series")
                .append("contentDetails", new Document("_class", "SERIES")
                        .append("seasons", List.of()));

        Document docDoc = new Document("_id", "d1")
                .append("type", "DOCUMENTARY")
                .append("title", "Test Doc")
                .append("contentDetails", new Document("_class", "DOCUMENTARY")
                        .append("durationSeconds", 3600)
                        .append("directors", List.of("Director"))
                        .append("topics", List.of("Nature"))
                        .append("interviewees", List.of())
                        .append("sources", List.of()));

        Content movie = converter.read(Content.class, movieDoc);
        assertInstanceOf(MovieDetails.class, movie.getContentDetails());
        assertEquals(5400, ((MovieDetails) movie.getContentDetails()).durationSeconds());

        Content series = converter.read(Content.class, seriesDoc);
        assertInstanceOf(SeriesDetails.class, series.getContentDetails());

        Content doc = converter.read(Content.class, docDoc);
        assertInstanceOf(DocumentaryDetails.class, doc.getContentDetails());
    }
}
