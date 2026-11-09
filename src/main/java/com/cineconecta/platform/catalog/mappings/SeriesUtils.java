package com.cineconecta.platform.catalog.mappings;

import com.cineconecta.platform.catalog.dtos.EpisodeDto;
import com.cineconecta.platform.catalog.dtos.SeasonDto;
import com.cineconecta.platform.catalog.exceptions.ContentNotFoundException;
import com.cineconecta.platform.catalog.exceptions.ContentRuleException;
import com.cineconecta.platform.catalog.models.Episode;
import com.cineconecta.platform.catalog.models.Season;
import com.cineconecta.platform.catalog.models.SeriesDetails;

import java.util.*;
import java.util.function.UnaryOperator;

public final class SeriesUtils {
    private SeriesUtils() {}

    // ------------------------------------------------------------------ creación
    public static SeriesDetails create(List<SeasonDto> seasons) {
        requireUnique(seasons.stream().map(SeasonDto::number).toList(), "temporadas");
        return new SeriesDetails(sortSeasons(seasons.stream().map(SeriesUtils::toSeason).toList()));
    }

    // ------------------------------------------------------------------ operaciones
    public static SeriesDetails addSeason(SeriesDetails series, SeasonDto dto) {
        List<Season> seasons = new ArrayList<>(seasonsOf(series));
        if (seasons.stream().anyMatch(s -> s.number() == dto.number())) {
            throw new ContentRuleException("Ya existe la temporada " + dto.number());
        }
        seasons.add(toSeason(dto));
        return new SeriesDetails(sortSeasons(seasons));
    }

    public static SeriesDetails updateSeasonDescription(SeriesDetails series, int seasonNumber, String description) {
        return replaceSeason(series, seasonNumber,
                season -> new Season(season.number(), description.strip(), season.episodes()));
    }

    public static SeriesDetails addEpisode(SeriesDetails series, int seasonNumber, EpisodeDto dto) {
        return replaceSeason(series, seasonNumber, season -> {
            List<Episode> episodes = new ArrayList<>(episodesOf(season));
            if (episodes.stream().anyMatch(e -> e.number() == dto.number())) {
                throw new ContentRuleException("La temporada " + seasonNumber + " ya tiene el episodio " + dto.number());
            }
            episodes.add(toEpisode(dto));
            return new Season(season.number(), season.description(), sortEpisodes(episodes));
        });
    }

    /** Edita un episodio conservando su episodeId. */
    public static SeriesDetails updateEpisode(SeriesDetails series, int seasonNumber, String episodeId, EpisodeDto dto) {
        return replaceSeason(series, seasonNumber, season -> {
            List<Episode> episodes = episodesOf(season);
            if (episodes.stream().noneMatch(e -> Objects.equals(e.episodeId(), episodeId))) {
                throw new ContentNotFoundException(
                        "No existe el episodio " + episodeId + " en la temporada " + seasonNumber);
            }
            if (episodes.stream().anyMatch(e -> e.number() == dto.number() && !Objects.equals(e.episodeId(), episodeId))) {
                throw new ContentRuleException("La temporada " + seasonNumber + " ya tiene el episodio " + dto.number());
            }
            List<Episode> updated = episodes.stream()
                    .map(e -> Objects.equals(e.episodeId(), episodeId)
                            ? new Episode(episodeId, dto.number(), dto.title(), dto.synopsis(), dto.durationSeconds())
                            : e)
                    .toList();
            return new Season(season.number(), season.description(), sortEpisodes(updated));
        });
    }

    /** Una serie se publica con al menos una temporada, y cada temporada con al menos un episodio. */
    public static void assertPublishable(SeriesDetails series) {
        List<Season> seasons = seasonsOf(series);
        if (seasons.isEmpty()) {
            throw new ContentRuleException("Una serie necesita al menos una temporada para publicarse");
        }
        List<Integer> empty = seasons.stream().filter(s -> episodesOf(s).isEmpty()).map(Season::number).toList();
        if (!empty.isEmpty()) {
            throw new ContentRuleException("No se puede publicar: temporadas sin episodios " + empty);
        }
    }

    // ------------------------------------------------------------------ helpers
    private static Season toSeason(SeasonDto dto) {
        requireUnique(dto.episodes().stream().map(EpisodeDto::number).toList(), "episodios");
        return new Season(dto.number(), dto.description(),
                sortEpisodes(dto.episodes().stream().map(SeriesUtils::toEpisode).toList()));
    }

    /** El episodeId lo genera el servidor y no vuelve a cambiar. */
    private static Episode toEpisode(EpisodeDto dto) {
        return new Episode(UUID.randomUUID().toString(), dto.number(), dto.title(),
                dto.synopsis(), dto.durationSeconds());
    }

    private static SeriesDetails replaceSeason(SeriesDetails series, int seasonNumber, UnaryOperator<Season> change) {
        List<Season> result = new ArrayList<>();
        boolean found = false;
        for (Season season : seasonsOf(series)) {
            if (season.number() == seasonNumber) {
                result.add(change.apply(season));
                found = true;
            } else {
                result.add(season);
            }
        }
        if (!found) {
            throw new ContentNotFoundException("No existe la temporada " + seasonNumber);
        }
        return new SeriesDetails(sortSeasons(result));
    }

    private static void requireUnique(List<Integer> numbers, String what) {
        Set<Integer> seen = new HashSet<>();
        for (Integer n : numbers) {
            if (!seen.add(n)) {
                throw new ContentRuleException("No pueden existir " + what + " con el mismo número (" + n + ")");
            }
        }
    }

    private static List<Season> seasonsOf(SeriesDetails series) {
        return series.seasons() == null ? List.of() : series.seasons();
    }

    private static List<Episode> episodesOf(Season season) {
        return season.episodes() == null ? List.of() : season.episodes();
    }

    private static List<Season> sortSeasons(List<Season> seasons) {
        return seasons.stream().sorted(Comparator.comparingInt(Season::number)).toList();
    }

    private static List<Episode> sortEpisodes(List<Episode> episodes) {
        return episodes.stream().sorted(Comparator.comparingInt(Episode::number)).toList();
    }
}
