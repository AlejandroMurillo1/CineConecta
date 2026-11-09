package com.cineconecta.platform.catalog.services;

import com.cineconecta.platform.catalog.dtos.ContentView;
import com.cineconecta.platform.catalog.dtos.EpisodeDto;
import com.cineconecta.platform.catalog.dtos.SeasonDto;
import com.cineconecta.platform.catalog.exceptions.ContentRuleException;
import com.cineconecta.platform.catalog.mappings.ContentMapper;
import com.cineconecta.platform.catalog.mappings.SeriesUtils;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.models.SeriesDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.function.UnaryOperator;

@Service
@Validated
public class SeriesService {
    private final ContentStore store;

    public SeriesService(ContentStore store) {
        this.store = store;
    }

    public ContentView addSeason(String seriesId, long expectedVersion, @NotNull @Valid SeasonDto season) {
        return change(seriesId, expectedVersion, series -> SeriesUtils.addSeason(series, season));
    }

    public ContentView updateSeasonDescription(String seriesId, int seasonNumber, long expectedVersion,
                                               @NotBlank String description) {
        return change(seriesId, expectedVersion,
                series -> SeriesUtils.updateSeasonDescription(series, seasonNumber, description));
    }

    public ContentView addEpisode(String seriesId, int seasonNumber, long expectedVersion,
                                  @NotNull @Valid EpisodeDto episode) {
        return change(seriesId, expectedVersion,
                series -> SeriesUtils.addEpisode(series, seasonNumber, episode));
    }

    public ContentView updateEpisode(String seriesId, int seasonNumber, String episodeId, long expectedVersion,
                                     @NotNull @Valid EpisodeDto episode) {
        return change(seriesId, expectedVersion,
                series -> SeriesUtils.updateEpisode(series, seasonNumber, episodeId, episode));
    }

    private ContentView change(String seriesId, long expectedVersion, UnaryOperator<SeriesDetails> operation) {
        Content content = store.getForUpdate(seriesId, expectedVersion);
        if (!(content.getContentDetails() instanceof SeriesDetails series)) {
            throw new ContentRuleException("El contenido " + seriesId + " no es una serie");
        }
        content.setContentDetails(operation.apply(series));
        content.setUpdatedAt(Instant.now());
        return ContentMapper.toView(store.save(content));
    }
}
