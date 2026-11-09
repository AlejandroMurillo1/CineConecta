package com.cineconecta.platform.catalog.repositories;

import com.cineconecta.platform.catalog.ContentStatus;
import com.cineconecta.platform.catalog.dtos.ContentSearchFilter;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.utils.TextNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ContentSearchRepository {
    /** Lo único que necesita una tarjeta: evita traer los episodios completos de las series. */
    private static final String[] CARD_FIELDS = {"title", "type", "year", "ageRating", "posterUrl", "genres"};

    private final MongoTemplate mongoTemplate;

    @Autowired
    public ContentSearchRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Página de resultados accesibles. Devuelve entidades PARCIALES (solo los campos de la tarjeta):
     * no usar este resultado para nada más que construir tarjetas. El orden lo trae el Pageable.
     */
    public Page<Content> searchPublished(int maxAgeRating, ContentSearchFilter filter, Pageable pageable) {
        Criteria criteria = accessible(maxAgeRating);
        if (filter.type() != null) {
            criteria = criteria.and("type").is(filter.type());
        }
        if (filter.genre() != null) {
            criteria = criteria.and("genres").is(filter.genre());                 // el arreglo contiene el valor
        }
        if (filter.audioLanguage() != null) {
            criteria = criteria.and("audioLanguages").is(filter.audioLanguage());
        }
        if (filter.year() != null) {
            criteria = criteria.and("year").is(filter.year());
        }
        if (filter.title() != null) {
            String text = TextNormalizer.normalize(filter.title());
            if (!text.isEmpty()) {
                criteria = criteria.and("titleNormalized").regex(escapeRegex(text));   // "contiene", sin tildes ni mayúsculas
            }
        }

        Query pageQuery = new Query(criteria).with(pageable);
        pageQuery.fields().include(CARD_FIELDS);
        List<Content> content = mongoTemplate.find(pageQuery, Content.class);

        Criteria countCriteria = criteria;
        return PageableExecutionUtils.getPage(content, pageable,
                () -> mongoTemplate.count(new Query(countCriteria), Content.class));
    }

    /** Un contenido completo, solo si es accesible con esa clasificación máxima. */
    public Optional<Content> findPublishedById(String id, int maxAgeRating) {
        Query query = new Query(accessible(maxAgeRating).and("id").is(id));
        return Optional.ofNullable(mongoTemplate.findOne(query, Content.class));
    }

    /** La regla de acceso, escrita una sola vez: publicado y con clasificación que no excede el tope. */
    private static Criteria accessible(int maxAgeRating) {
        return Criteria.where("status").is(ContentStatus.PUBLISHED)
                .and("ageRating").lte(maxAgeRating);
    }

    /** El texto del usuario se busca literalmente: ningún carácter suyo actúa como expresión regular. */
    private static String escapeRegex(String text) {
        return text.replaceAll("[\\\\.*+?^${}()|\\[\\]]", "\\\\$0");
    }
}
