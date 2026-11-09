package com.cineconecta.platform.catalog.repositories;

import com.cineconecta.platform.catalog.ContentStatus;
import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.models.Content;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ContentRepository extends MongoRepository<Content, String> {
    boolean existsByTitleNormalizedAndYear(String titleNormalized, int year);

    boolean existsByTitleNormalizedAndYearAndIdNot(String titleNormalized, int year, String id);

    // Listado de administración; el orden lo trae el Pageable (p. ej. updatedAt descendente).
    Page<Content> findByStatus(ContentStatus status, Pageable pageable);
    Page<Content> findByType(ContentType type, Pageable pageable);
    Page<Content> findByStatusAndType(ContentStatus status, ContentType type, Pageable pageable);
}
