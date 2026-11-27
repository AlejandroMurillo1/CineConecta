package com.cineconecta.platform.catalog.services;

import com.cineconecta.platform.catalog.*;
import com.cineconecta.platform.catalog.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CatalogApiService implements ICatalogApi {
    private final ContentStore store;

    @Autowired
    public CatalogApiService(ContentStore contentStore) {
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
        if (contentIds == null || contentIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = contentIds.stream().filter(Objects::nonNull).distinct().toList();
        return store.findAllById(ids).stream()
                .map(CatalogApiService::toSummary)
                .collect(Collectors.toMap(ContentSummary::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private static ContentSummary toSummary(Content c) {
        return new ContentSummary(c.getId(), c.getTitle(), c.getType(), c.getAgeRating(),
                c.getStatus(), c.getPosterUrl());
    }

    private static Optional<PlayableRef> toPlayable(Content content, String episodeId) {
        if (content.getStatus() != ContentStatus.PUBLISHED || content.getContentDetails() == null) {
            return Optional.empty();
        }
        return switch (content.getContentDetails()) {
            case MovieDetails movie -> episodeId != null ? Optional.empty()
                    : Optional.of(single(content, movie.durationSeconds()));

            case DocumentaryDetails documentary -> episodeId != null ? Optional.empty()
                    : Optional.of(single(content, documentary.durationSeconds()));

            case SeriesDetails series -> episodeId == null ? Optional.empty()
                    : episodeOf(content, series, episodeId);
        };
    }

    /** Contenido de una sola pieza (película o documental). */
    private static PlayableRef single(Content content, int durationSeconds) {
        return new PlayableRef(content.getId(), null, content.getType(), content.getTitle(),
                null, null, null, durationSeconds, content.getAgeRating(), content.getPosterUrl());
    }

    private static Optional<PlayableRef> episodeOf(Content content, SeriesDetails series, String episodeId) {
        if (series.seasons() == null) {
            return Optional.empty();
        }
        for (Season season : series.seasons()) {
            if (season.episodes() == null) {
                continue;
            }
            for (Episode episode : season.episodes()) {
                if (Objects.equals(episode.episodeId(), episodeId)) {
                    return Optional.of(new PlayableRef(
                            content.getId(),
                            episode.episodeId(),
                            ContentType.SERIES,
                            content.getTitle(),
                            episode.title(),
                            season.number(),
                            episode.number(),
                            episode.durationSeconds(),
                            content.getAgeRating(),
                            content.getPosterUrl())
                    );
                }
            }
        }
        return Optional.empty();
    }
}
