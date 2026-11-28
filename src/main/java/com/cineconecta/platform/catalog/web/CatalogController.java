package com.cineconecta.platform.catalog.web;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.ViewerContext;
import com.cineconecta.platform.catalog.dtos.ContentSearchFilter;
import com.cineconecta.platform.catalog.exceptions.ContentNotFoundException;
import com.cineconecta.platform.catalog.services.CatalogSearchService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Catálogo visto por el usuario: buscar y abrir un contenido. Solo muestra lo publicado que la
 * clasificación del perfil permite. Que haya un perfil activo con suscripción vigente se garantiza
 * ANTES de llegar aquí (seguridad / ViewerContext); este controlador solo aplica el tope de
 * clasificación que el servidor le da.
 */
@Controller
@RequestMapping("/catalog")
public class CatalogController {

    private final CatalogSearchService search;
    private final ViewerContext viewer;

    @Autowired
    public CatalogController(CatalogSearchService search, ViewerContext viewer) {
        this.search = search;
        this.viewer = viewer;
    }

    /**
     * Página completa, o solo el fragmento de resultados cuando la pide htmx (cabecera HX-Request),
     * para actualizar la lista sin recargar la página.
     */
    @GetMapping
    public String search(@Valid @ModelAttribute("filter") ContentSearchFilter filter, BindingResult result,
                         @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                         Model model) {
        ContentSearchFilter effective = filter;
        if (filter == null || result.hasErrors()) {
            // Filtros ilegibles o inválidos (p. ej. ?type=xyz): se muestra el catálogo sin filtrar, con un aviso.
            effective = ContentSearchFilter.none();
            model.addAttribute("filter", effective);
            model.addAttribute("error", "Algún filtro no es válido y se ignoró");
        }
        model.addAttribute("page", search.search(viewer.maxAgeRating(), effective));
        model.addAttribute("types", ContentType.values());
        return htmxRequest != null ? "catalog/search :: results" : "catalog/search";
    }

    /** Títulos que muestra la sección del catálogo en la página de inicio. */
    static final int HOME_SECTION_SIZE = 12;

    /**
     * Sección "Catálogo" de la página de inicio, que la carga con htmx: los títulos más recientes
     * que el perfil en uso puede ver. No redirige: sin perfil elegido o sin suscripción vigente
     * responde vacío, y la página de inicio queda como estaba.
     */
    @GetMapping("/home-section")
    public String homeSection(Model model) {
        viewer.allowedMaxAgeRating().ifPresent(maxAgeRating -> model.addAttribute("titles",
                search.search(maxAgeRating, new ContentSearchFilter(
                        null, null, null, null, null, 0, HOME_SECTION_SIZE)).getContent()));
        return "catalog/fragments/home-section :: home-section";
    }

    /** Abre un contenido (en una serie, con sus temporadas y episodios). Si no es accesible: 404. */
    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        model.addAttribute("content", search.findAccessible(id, viewer.maxAgeRating())
                .orElseThrow(() -> new ContentNotFoundException("El contenido no está disponible")));
        return "catalog/detail";
    }
}
