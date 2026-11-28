package com.cineconecta.platform.catalog.repositories;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.models.Content;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Completa el alias de los detalles en documentos creados antes de que el esquema lo exigiera.
 * Spring Data necesita este discriminador para materializar la implementación concreta de
 * {@code ContentDetails} al leer un contenido completo.
 */
@Configuration
class ContentDetailsAliasMigration {

    @Bean
    ApplicationRunner contentDetailsAliasMigrationRunner(MongoTemplate mongoTemplate) {
        return _ -> {
            for (ContentType type : ContentType.values()) {
                Query query = Query.query(Criteria.where("type").is(type)
                        .and("contentDetails._class").exists(false));
                mongoTemplate.updateMulti(query,
                        new Update().set("contentDetails._class", type.name()),
                        Content.class);
            }
        };
    }
}
