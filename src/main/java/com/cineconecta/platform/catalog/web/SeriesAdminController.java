package com.cineconecta.platform.catalog.web;

import com.cineconecta.platform.catalog.dtos.ContentEditModel;
import com.cineconecta.platform.catalog.dtos.ContentView;
import com.cineconecta.platform.catalog.dtos.EpisodeDto;
import com.cineconecta.platform.catalog.dtos.SeasonDto;
import com.cineconecta.platform.catalog.exceptions.ContentRuleException;
import com.cineconecta.platform.catalog.exceptions.StaleContentException;
import com.cineconecta.platform.catalog.services.SeriesService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/series/{seriesId}")
@PreAuthorize("hasRole('ADMIN')")
public class SeriesAdminController {

    private static final String SEASON_FORM = "admin/series/season-form";
    private static final String EPISODE_FORM = "admin/series/episode-form";
    private static final String INVALID_DATA = "No se pudo leer el formulario. Revisa los datos e inténtalo de nuevo.";

    private final SeriesService seriesService;

    @Autowired
    public SeriesAdminController(SeriesService seriesService) {
        this.seriesService = seriesService;
    }

    // ================================================================== temporadas
    @GetMapping("/seasons/new")
    public String newSeasonForm(@PathVariable String seriesId, Model model) {
        return seasonForm(model, seriesId, blankSeason());
    }

    @PostMapping("/seasons")
    public String addSeason(@PathVariable String seriesId, @RequestParam long version,
                            @Valid @ModelAttribute("season") SeasonDto season,
                            BindingResult result, Model model, RedirectAttributes redirect) {
        if (season != null && !result.hasErrors()) {
            try {
                seriesService.addSeason(seriesId, version, season);
                redirect.addFlashAttribute("message", "Temporada agregada");
                return redirectTo(seriesId);
            } catch (ContentRuleException | StaleContentException e) {
                result.reject("catalog.error", e.getMessage());
            }
        }
        // El formulario se vuelve a mostrar con la versión que se envió: si estaba desactualizada, hay que recargar.
        return seasonForm(model, seriesId, season, version);
    }

    @PostMapping("/seasons/{seasonNumber}/description")
    public String updateSeasonDescription(@PathVariable String seriesId, @PathVariable int seasonNumber,
                                          @RequestParam long version, @RequestParam String description,
                                          RedirectAttributes redirect) {
        if (description == null || description.isBlank()) {
            redirect.addFlashAttribute("error", "La descripción de la temporada es obligatoria");
            return redirectTo(seriesId);
        }
        return act(seriesId, redirect, "Temporada actualizada",
                () -> seriesService.updateSeasonDescription(seriesId, seasonNumber, version, description));
    }

    // ================================================================== episodios
    @GetMapping("/seasons/{seasonNumber}/episodes/new")
    public String newEpisodeForm(@PathVariable String seriesId, @PathVariable int seasonNumber, Model model) {
        return episodeForm(model, seriesId, seasonNumber, null, blankEpisode());
    }

    @PostMapping("/seasons/{seasonNumber}/episodes")
    public String addEpisode(@PathVariable String seriesId, @PathVariable int seasonNumber,
                             @RequestParam long version,
                             @Valid @ModelAttribute("episode") EpisodeDto episode,
                             BindingResult result, Model model, RedirectAttributes redirect) {
        if (episode != null && !result.hasErrors()) {
            try {
                seriesService.addEpisode(seriesId, seasonNumber, version, episode);
                redirect.addFlashAttribute("message", "Episodio agregado");
                return redirectTo(seriesId);
            } catch (ContentRuleException | StaleContentException e) {
                result.reject("catalog.error", e.getMessage());
            }
        }
        return episodeForm(model, seriesId, seasonNumber, null, episode, version);
    }

    @GetMapping("/seasons/{seasonNumber}/episodes/{episodeId}/edit")
    public String editEpisodeForm(@PathVariable String seriesId, @PathVariable int seasonNumber,
                                  @PathVariable String episodeId, Model model) {
        ContentEditModel edit = seriesService.getEpisodeEditModel(seriesId, seasonNumber, episodeId);
        return episodeForm(model, seriesId, seasonNumber, episodeId, edit.episode(), edit.version());
    }

    @PostMapping("/seasons/{seasonNumber}/episodes/{episodeId}")
    public String updateEpisode(@PathVariable String seriesId, @PathVariable int seasonNumber,
                                @PathVariable String episodeId, @RequestParam long version,
                                @Valid @ModelAttribute("episode") EpisodeDto episode,
                                BindingResult result, Model model, RedirectAttributes redirect) {
        if (episode != null && !result.hasErrors()) {
            try {
                seriesService.updateEpisode(seriesId, seasonNumber, episodeId, version, episode);
                redirect.addFlashAttribute("message", "Episodio actualizado");
                return redirectTo(seriesId);
            } catch (ContentRuleException | StaleContentException e) {
                result.reject("catalog.error", e.getMessage());
            }
        }
        return episodeForm(model, seriesId, seasonNumber, episodeId, episode, version);
    }

    // ================================================================== internos
    private String act(String seriesId, RedirectAttributes redirect, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("message", success);
        } catch (ContentRuleException | StaleContentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return redirectTo(seriesId);
    }

    /** Formulario de temporada recién abierto: la versión es la actual de la serie. */
    private String seasonForm(Model model, String seriesId, SeasonDto season) {
        ContentView series = seriesService.getSeries(seriesId);
        return seasonForm(model, seriesId, season, series.version());
    }

    private String seasonForm(Model model, String seriesId, SeasonDto season, long version) {
        Object shown = season;
        if (shown == null) {
            shown = blankSeason();
            model.addAttribute("error", INVALID_DATA);
        }
        model.addAttribute("series", seriesService.getSeries(seriesId));
        model.addAttribute("season", shown);
        model.addAttribute("version", version);
        model.addAttribute("action", "/admin/series/" + seriesId + "/seasons");
        return SEASON_FORM;
    }

    private String episodeForm(Model model, String seriesId, int seasonNumber, String episodeId, EpisodeDto episode) {
        ContentView series = seriesService.getSeries(seriesId);
        return episodeForm(model, seriesId, seasonNumber, episodeId, episode, series.version());
    }

    private String episodeForm(Model model, String seriesId, int seasonNumber, String episodeId,
                               EpisodeDto episode, long version) {
        Object shown = episode;
        if (shown == null) {
            shown = blankEpisode();
            model.addAttribute("error", INVALID_DATA);
        }
        String base = "/admin/series/" + seriesId + "/seasons/" + seasonNumber + "/episodes";
        model.addAttribute("series", seriesService.getSeries(seriesId));
        model.addAttribute("seasonNumber", seasonNumber);
        model.addAttribute("episode", shown);
        model.addAttribute("version", version);
        if (episodeId != null) {
            model.addAttribute("episodeId", episodeId);
        }
        model.addAttribute("action", episodeId == null ? base : base + "/" + episodeId);
        return EPISODE_FORM;
    }

    /** Una temporada nueva exige al menos un episodio: se ofrece una fila en blanco para empezar. */
    private static SeasonDto blankSeason() {
        return new SeasonDto(null, null, List.of(blankEpisode()));
    }

    private static EpisodeDto blankEpisode() {
        return new EpisodeDto(null, null, null, null);
    }

    private static String redirectTo(String seriesId) {
        return "redirect:/admin/contents/" + seriesId;
    }
}
