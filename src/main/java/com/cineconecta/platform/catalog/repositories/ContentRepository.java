package com.cineconecta.platform.catalog.repositories;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.models.Content;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ContentRepository extends MongoRepository<Content, String> {
    List<Content> findAllByType(ContentType type, Pageable pageable);
    Optional<Content> findByTitleNormalized(String title);

    boolean existsByTitleNormalizedAndYear(String titleNormalized, int year);
}
