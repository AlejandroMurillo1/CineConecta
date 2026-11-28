package com.cineconecta.platform.viewing.internal.service;

import com.cineconecta.platform.catalog.PlaybackKey;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lo que el perfil en uso ya vio, para marcarlo en el catálogo ("✓ Visto") y ofrecer volver a verlo.
 * Una película o documental cuenta como vista entera; en una serie se marcan los episodios.
 */
public final class WatchedTitles {

    public static final WatchedTitles NONE = new WatchedTitles(Set.of());

    private final Set<String> contents = new HashSet<>();
    private final Set<String> episodes = new HashSet<>();
    private final Map<String, Set<String>> episodesBySeries = new HashMap<>();

    public WatchedTitles(Collection<PlaybackKey> completed) {
        for (PlaybackKey key : completed) {
            if (key.episodeId() == null) {
                contents.add(key.contentId());
            } else {
                episodes.add(key.episodeId());
                episodesBySeries.computeIfAbsent(key.contentId(), id -> new HashSet<>()).add(key.episodeId());
            }
        }
    }

    /** Película o documental ya visto. */
    public boolean content(String contentId) {
        return contents.contains(contentId);
    }

    /** Episodio ya visto. */
    public boolean episode(String episodeId) {
        return episodes.contains(episodeId);
    }

    /** Cuántos episodios distintos de la serie ya vio. */
    public int episodesOf(String contentId) {
        return episodesBySeries.getOrDefault(contentId, Set.of()).size();
    }

    /**
     * Primer episodio sin ver, recorriendo las temporadas en orden (cada una con los ids de sus episodios).
     * Devuelve {@code null} si no hay episodios o si ya vio todos.
     */
    public String firstUnwatchedEpisode(List<? extends List<String>> episodeIdsBySeason) {
        for (List<String> season : episodeIdsBySeason) {
            for (String episodeId : season) {
                if (!episodes.contains(episodeId)) {
                    return episodeId;
                }
            }
        }
        return null;
    }
}
