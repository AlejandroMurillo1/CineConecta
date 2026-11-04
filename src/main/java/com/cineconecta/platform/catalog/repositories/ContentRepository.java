package com.cineconecta.platform.catalog.repositories;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.models.Content;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ContentRepository extends MongoRepository<Content, String> {
    Page<Content> findAll(@NonNull Pageable pageable);
    List<Content> findAllByType(ContentType type, int limit, int offset);
}
