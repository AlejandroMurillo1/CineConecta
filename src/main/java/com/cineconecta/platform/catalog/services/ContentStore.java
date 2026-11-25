package com.cineconecta.platform.catalog.services;

import com.cineconecta.platform.catalog.ContentStatus;
import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.exceptions.ContentNotFoundException;
import com.cineconecta.platform.catalog.exceptions.ContentRuleException;
import com.cineconecta.platform.catalog.exceptions.DuplicateContentException;
import com.cineconecta.platform.catalog.exceptions.StaleContentException;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.repositories.ContentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class ContentStore {
    private final ContentRepository repository;

    @Autowired
    public ContentStore(ContentRepository repository) {
        this.repository = repository;
    }

    // ------------------------------------------------------------------ lecturas
    public Optional<Content> find(String id) {
        return id == null ? Optional.empty() : repository.findById(id);
    }

    public Content get(String id) {
        return find(id).orElseThrow(() -> new ContentNotFoundException("No existe el contenido " + id));
    }

    public List<Content> findAllById(Collection<String> ids) {
        return repository.findAllById(ids);
    }

    /** Listado de administración; status y type son filtros opcionales (null = todos). */
    public Page<Content> list(ContentStatus status, ContentType type, Pageable pageable) {
        if (status != null && type != null) {
            return repository.findByStatusAndType(status, type, pageable);
        }
        if (status != null) {
            return repository.findByStatus(status, pageable);
        }
        if (type != null) {
            return repository.findByType(type, pageable);
        }
        return repository.findAll(pageable);
    }

    /** ¿Existe OTRO contenido con el mismo título normalizado y año? (el propio no cuenta). */
    public boolean existsOtherWithSameTitleAndYear(Content content) {
        return content.getId() == null
                ? repository.existsByTitleNormalizedAndYear(content.getTitleNormalized(), content.getYear())
                : repository.existsByTitleNormalizedAndYearAndIdNot(
                content.getTitleNormalized(), content.getYear(), content.getId());
    }

    // ------------------------------------------------------------------ escrituras
    /** Para operaciones que modifican: exige la versión que vio quien edita y que no esté retirado. */
    public Content getForUpdate(String id, long expectedVersion) {
        Content content = get(id);
        if (!Objects.equals(content.getVersion(), expectedVersion)) {
            throw new StaleContentException(id);
        }
        if (content.getStatus() == ContentStatus.RETIRED) {
            throw new ContentRuleException("Un contenido retirado no puede modificarse");
        }
        return content;
    }

    public Content save(Content content) {
        try {
            return repository.save(content);
        } catch (OptimisticLockingFailureException e) {
            throw new StaleContentException(content.getId());   // @Version detectó una edición simultánea
        } catch (DuplicateKeyException e) {
            throw new DuplicateContentException(content.getTitle(), content.getYear());   // índice único activo
        }
    }
}
