package com.cineconecta.platform.catalog;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface CatalogApi {
    Optional<ContentSummary> findPublished(String contentId);
    Optional<PlayableRef> resolvePlayable(String contentId, String episodeId);
    Map<String, ContentSummary> summaries(Collection<String> contentIds);
}
