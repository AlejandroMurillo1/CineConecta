package com.cineconecta.platform.catalog.services;

import com.cineconecta.platform.catalog.ContentSummary;
import com.cineconecta.platform.catalog.ICatalogApi;
import com.cineconecta.platform.catalog.PlayableRef;
import com.cineconecta.platform.catalog.PlaybackKey;
import com.cineconecta.platform.catalog.models.Content;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CatalogApiImpl implements ICatalogApi {
    private final ContentStore store;

    @Autowired
    public CatalogApiImpl(ContentStore contentStore) {
        this.store = contentStore;
    }

    @Override
    public Optional<PlayableRef> findPlayable(String contentId, String episodeId) {
        return store.find(contentId).flatMap(content -> toPlayable(content, episodeId));
    }

    @Override
    public Map<PlaybackKey, PlayableRef> findPlayables(Collection<PlaybackKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }

        List<String> ids = keys.stream()
                .filter(Objects::nonNull)
                .map(PlaybackKey::contentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, Content> contents = store.findAllById(ids).stream()
                .collect(Collectors.toMap(Content::getId, Function.identity()));

        Map<PlaybackKey, PlayableRef> result = new LinkedHashMap<>();
        for (PlaybackKey key : new LinkedHashSet<>(keys)) {
            Content content = key == null ? null : contents.get(key.contentId());
            if (content != null) {
                toPlayable(content, key.episodeId()).ifPresent(ref -> result.put(key, ref));
            }
        }
        return result;
    }

    @Override
    public Map<String, ContentSummary> findSummaries(Collection<String> contentIds) {
        return Map.of();
    }
}
