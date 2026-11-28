package com.cineconecta.platform.viewing.internal.service;

import com.cineconecta.platform.catalog.ContentStatus;
import com.cineconecta.platform.catalog.ContentSummary;
import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.ICatalogApi;
import com.cineconecta.platform.viewing.internal.persistence.MyListStore;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MyListService {
    private final ViewingAccess access;
    private final ICatalogApi catalog;
    private final MyListStore store;

    public MyListService(ViewingAccess access, ICatalogApi catalog, MyListStore store) {
        this.access = access;
        this.catalog = catalog;
        this.store = store;
    }

    public List<SavedTitle> list(int profileId) {
        int ageLimit = access.requireEligibleProfile(profileId).profile().maxAgeRating();
        List<String> ids = store.findIds(profileId);
        Map<String, ContentSummary> summaries = catalog.findSummaries(ids);
        List<SavedTitle> titles = new ArrayList<>();

        for (String id : ids) {
            ContentSummary content = summaries.get(id);
            if (content == null || content.ageRating() > ageLimit
                    || (content.status() != ContentStatus.PUBLISHED && content.status() != ContentStatus.RETIRED)) {
                titles.add(new SavedTitle(id, "Título no disponible", "", null, null, false));
            } else {
                String status = content.status() == ContentStatus.RETIRED ? "Retirado" : "";
                titles.add(new SavedTitle(id, content.title(), status, content.type(), content.posterUrl(), true));
            }
        }

        return titles;
    }

    public void add(int profileId, String contentId) {
        int ageLimit = access.requireEligibleProfile(profileId).profile().maxAgeRating();
        String id = validId(contentId);
        ContentSummary content = catalog.findSummaries(List.of(id)).get(id);
        if (content == null || content.status() != ContentStatus.PUBLISHED || content.ageRating() > ageLimit) {
            throw new IllegalArgumentException("Este título no está disponible para el perfil.");
        }
        store.add(profileId, id);
    }

    public void remove(int profileId, String contentId) {
        access.requireEligibleProfile(profileId);
        store.remove(profileId, validId(contentId));
    }

    private String validId(String contentId) {
        if (contentId == null || !ObjectId.isValid(contentId.strip())) {
            throw new IllegalArgumentException("Ingresa un ID de título válido.");
        }
        return new ObjectId(contentId.strip()).toHexString();
    }

    /**
     * Título guardado. {@code available} es falso si ya no existe, dejó de estar publicado (salvo
     * retirado) o supera la clasificación del perfil: se muestra sin afiche ni enlace, solo para quitarlo.
     */
    public record SavedTitle(String id, String title, String status, ContentType type, String posterUrl,
                             boolean available) {

        /** "Película", "Serie" o "Documental"; null si el título no está disponible. */
        public String typeLabel() {
            if (type == null) {
                return null;
            }
            return switch (type) {
                case MOVIE -> "Película";
                case SERIES -> "Serie";
                case DOCUMENTARY -> "Documental";
            };
        }
    }
}
