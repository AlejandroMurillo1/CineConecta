package com.cineconecta.platform.catalog.web;

import com.cineconecta.platform.catalog.ContentStatus;
import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.*;
import com.cineconecta.platform.catalog.exceptions.ContentRuleException;
import com.cineconecta.platform.catalog.exceptions.DuplicateContentException;
import com.cineconecta.platform.catalog.exceptions.StaleContentException;
import com.cineconecta.platform.catalog.services.CatalogService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/contents")
@PreAuthorize("hasRole('ADMIN')")
public class ContentAdminController {
    private final CatalogService catalog;

    public ContentAdminController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public String list(@RequestParam(required = false) ContentStatus status,
                       @RequestParam(required = false) ContentType type,
                       @RequestParam(defaultValue = "0") int page, Model model,
                       @RequestHeader(value = "HX-Request", required = false) String htmx) {
        model.addAttribute("contents", catalog.listContents(status, type,
                PageRequest.of(Math.max(0, page), 20, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id")))));
        model.addAttribute("status", status);
        model.addAttribute("type", type);
        model.addAttribute("statuses", ContentStatus.values());
        model.addAttribute("types", ContentType.values());
        return htmx == null ? "admin/contents/list" : "admin/contents/list :: content-list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(defaultValue = "MOVIE") ContentType type, Model model) {
        model.addAttribute("types", ContentType.values());
        model.addAttribute("type", type);
        model.addAttribute("command", blankCommand(type));
        return "admin/contents/form";
    }

    @PostMapping("/movies")
    public String createMovie(@Valid @ModelAttribute("command") CreateMovieCommand command,
                              BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return formError(model, ContentType.MOVIE, command);
        try { var created = catalog.createContent(command); redirect.addFlashAttribute("message", "Película creada como borrador"); return "redirect:/admin/contents/" + created.id(); }
        catch (DuplicateContentException | ContentRuleException e) { result.reject("catalog", e.getMessage()); return formError(model, ContentType.MOVIE, command); }
    }

    @PostMapping("/documentaries")
    public String createDocumentary(@Valid @ModelAttribute("command") CreateDocumentaryCommand command,
                                   BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return formError(model, ContentType.DOCUMENTARY, command);
        try { var created = catalog.createContent(command); redirect.addFlashAttribute("message", "Documental creado como borrador"); return "redirect:/admin/contents/" + created.id(); }
        catch (DuplicateContentException | ContentRuleException e) { result.reject("catalog", e.getMessage()); return formError(model, ContentType.DOCUMENTARY, command); }
    }

    @PostMapping("/series")
    public String createSeries(@Valid @ModelAttribute("command") CreateSeriesCommand command,
                               BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return formError(model, ContentType.SERIES, command);
        try { var created = catalog.createContent(command); redirect.addFlashAttribute("message", "Serie creada como borrador"); return "redirect:/admin/contents/" + created.id(); }
        catch (DuplicateContentException | ContentRuleException e) { result.reject("catalog", e.getMessage()); return formError(model, ContentType.SERIES, command); }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        model.addAttribute("content", catalog.getContent(id));
        return "admin/contents/detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable String id, Model model) {
        ContentEditModel edit = catalog.getEditModel(id);
        model.addAttribute("contentId", edit.id());
        model.addAttribute("version", edit.version());
        model.addAttribute("command", edit.command());
        model.addAttribute("type", edit.command().getType());
        model.addAttribute("types", ContentType.values());
        return "admin/contents/form";
    }

    @PostMapping("/{id}/movies")
    public String updateMovie(@PathVariable String id, @RequestParam long version,
                              @Valid @ModelAttribute("command") UpdateMovieCommand command, BindingResult result,
                              Model model, RedirectAttributes redirect) {
        return update(id, version, ContentType.MOVIE, command, result, model, redirect);
    }
    @PostMapping("/{id}/documentaries")
    public String updateDocumentary(@PathVariable String id, @RequestParam long version,
                                    @Valid @ModelAttribute("command") UpdateDocumentaryCommand command, BindingResult result,
                                    Model model, RedirectAttributes redirect) {
        return update(id, version, ContentType.DOCUMENTARY, command, result, model, redirect);
    }
    @PostMapping("/{id}/series")
    public String updateSeries(@PathVariable String id, @RequestParam long version,
                               @Valid @ModelAttribute("command") UpdateSeriesCommand command, BindingResult result,
                               Model model, RedirectAttributes redirect) {
        return update(id, version, ContentType.SERIES, command, result, model, redirect);
    }

    @PostMapping("/{id}/publish")
    public String publish(@PathVariable String id, @RequestParam long version, RedirectAttributes redirect) {
        return transition(id, redirect, "Contenido publicado", () -> catalog.publishContent(id, version));
    }
    @PostMapping("/{id}/retire")
    public String retire(@PathVariable String id, @RequestParam long version, RedirectAttributes redirect) {
        return transition(id, redirect, "Contenido retirado", () -> catalog.retireContent(id, version));
    }
    @PostMapping("/{id}/restore")
    public String restore(@PathVariable String id, @RequestParam long version, RedirectAttributes redirect) {
        return transition(id, redirect, "Contenido restaurado como borrador", () -> catalog.restoreAsDraft(id, version));
    }

    private String update(String id, long version, ContentType type, UpdateContentCommand command,
                          BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try { catalog.updateContent(id, version, command); redirect.addFlashAttribute("message", "Cambios guardados"); return "redirect:/admin/contents/" + id; }
            catch (DuplicateContentException | ContentRuleException | StaleContentException e) { result.reject("catalog", e.getMessage()); }
        }
        model.addAttribute("contentId", id); model.addAttribute("version", version); model.addAttribute("type", type); model.addAttribute("types", ContentType.values());
        return "admin/contents/form";
    }

    private String transition(String id, RedirectAttributes redirect, String success, Runnable action) {
        try { action.run(); redirect.addFlashAttribute("message", success); }
        catch (ContentRuleException | StaleContentException e) { redirect.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/contents/" + id;
    }

    private String formError(Model model, ContentType type, Object command) {
        model.addAttribute("types", ContentType.values()); model.addAttribute("type", type); model.addAttribute("command", command);
        return "admin/contents/form";
    }

    private static Object blankCommand(ContentType type) {
        ContentMetadata metadata = new ContentMetadata(null, null, 0, 0, null, null, null, null, null);
        return switch (type) {
            case MOVIE -> new CreateMovieCommand(metadata, 0, null, null);
            case DOCUMENTARY -> new CreateDocumentaryCommand(metadata, 0, null, null, null, null);
            case SERIES -> new CreateSeriesCommand(metadata, null);
        };
    }
}
