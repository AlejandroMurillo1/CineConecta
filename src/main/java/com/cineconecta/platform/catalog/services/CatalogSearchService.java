package com.cineconecta.platform.catalog.services;

import com.cineconecta.platform.catalog.dtos.ContentCardView;
import com.cineconecta.platform.catalog.dtos.ContentDetailView;
import com.cineconecta.platform.catalog.dtos.ContentSearchFilter;
import com.cineconecta.platform.catalog.mappings.ContentMapper;
import com.cineconecta.platform.catalog.repositories.ContentSearchRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;

@Service
@Validated
public class CatalogSearchService {
    /** Orden estable (el desempate por id evita repetidos y omitidos al paginar) y alineado con los índices. */
    private static final Sort ORDER = Sort.by(Sort.Order.desc("year"), Sort.Order.asc("id"));

    private final ContentSearchRepository searchRepository;

    @Autowired
    public CatalogSearchService(ContentSearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    /** Buscar por título y filtrar por tipo, género, idioma de audio y año, con paginación. */
    public Page<ContentCardView> search(int maxAgeRating, @NotNull @Valid ContentSearchFilter filter) {
        PageRequest pageable = PageRequest.of(filter.page(), filter.size(), ORDER);
        return searchRepository.searchPublished(maxAgeRating, filter, pageable).map(ContentMapper::toCard);
    }

    /**
     * Abrir un contenido (en una serie, con sus temporadas y episodios). Vacío si no existe, no está
     * publicado o su clasificación excede el tope: el llamador lo trata como "no encontrado".
     */
    public Optional<ContentDetailView> findAccessible(String id, int maxAgeRating) {
        if (id == null) {
            return Optional.empty();
        }
        return searchRepository.findPublishedById(id, maxAgeRating)
                .map(ContentMapper::toDetail);
    }
}
