package com.cineconecta.platform.catalog.services;

import com.cineconecta.platform.catalog.ContentStatus;
import com.cineconecta.platform.catalog.exceptions.ContentNotFoundException;
import com.cineconecta.platform.catalog.exceptions.ContentRuleException;
import com.cineconecta.platform.catalog.exceptions.DuplicateContentException;
import com.cineconecta.platform.catalog.exceptions.StaleContentException;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.repositories.ContentRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ContentStore {
    private final ContentRepository repository;

    public ContentStore(ContentRepository repository) {
        this.repository = repository;
    }

    public Content get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException("No existe el contenido " + id));
    }

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
            throw new StaleContentException(content.getId());
        } catch (DuplicateKeyException e) {
            throw new DuplicateContentException(content.getTitle(), content.getYear());
        }
    }

    //TODO: Metodos find que necesitan los services.
}
