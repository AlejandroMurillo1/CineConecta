package com.cineconecta.platform.catalog.web;

import com.cineconecta.platform.catalog.ViewerContext;
import com.cineconecta.platform.catalog.dtos.ContentSearchFilter;
import com.cineconecta.platform.catalog.services.CatalogSearchService;
import com.cineconecta.platform.shared.PermitAllSecurityTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.ContentCardView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.OptionalInt;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Abrir el catálogo sin filtros no debe mostrar el aviso de "filtro no válido". */
@WebMvcTest(CatalogController.class)
@Import(PermitAllSecurityTestConfig.class)
class CatalogSearchFilterWebMvcTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CatalogSearchService search;
    @MockitoBean
    private ViewerContext viewer;

    @BeforeEach
    void setUp() {
        when(viewer.maxAgeRating()).thenReturn(12);
        when(search.search(anyInt(), any())).thenReturn(Page.empty());
    }

    @Test
    void openingTheCatalogWithoutParametersShowsNoFilterError() throws Exception {
        mockMvc.perform(get("/catalog"))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("error"));
        verify(search).search(eq(12), eq(ContentSearchFilter.none()));
    }

    @Test
    void emptyFormFieldsShowNoFilterError() throws Exception {
        mockMvc.perform(get("/catalog?title=&type=&genre=&audioLanguage=&year=&page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("error"));
    }

    @Test
    void homeSectionShowsRecentTitlesForTheProfileRating() throws Exception {
        when(viewer.allowedMaxAgeRating()).thenReturn(OptionalInt.of(12));
        when(search.search(eq(12), any())).thenReturn(new PageImpl<>(List.of(
                new ContentCardView("6740a1f0c3b2a1d4e5f60004", "La última función", ContentType.MOVIE,
                        2021, 7, "/images/posters/6740a1f0c3b2a1d4e5f60004.svg", List.of("comedia")),
                new ContentCardView("6740a1f0c3b2a1d4e5f60001", "Río Abajo", ContentType.SERIES,
                        2024, 13, null, List.of("drama")))));

        mockMvc.perform(get("/catalog/home-section").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Catálogo")))
                .andExpect(content().string(containsString("href=\"/catalog\"")))
                .andExpect(content().string(containsString("Ver todo")))
                .andExpect(content().string(containsString("La última función")))
                .andExpect(content().string(containsString("Película · 2021 · 7+")))
                .andExpect(content().string(containsString("Serie · 2024 · 13+")))
                .andExpect(content().string(containsString("/images/posters/placeholder.svg")))
                .andExpect(content().string(not(containsString("<html"))));
        verify(search).search(eq(12), eq(new ContentSearchFilter(null, null, null, null, null, 0, 12)));
    }

    @Test
    void homeSectionIsEmptyWhenTheProfileCannotSeeTheCatalog() throws Exception {
        when(viewer.allowedMaxAgeRating()).thenReturn(OptionalInt.empty());

        mockMvc.perform(get("/catalog/home-section").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("cw-"))));
        verify(search, never()).search(anyInt(), any());
    }

    @Test
    void anInvalidFilterIsStillReported() throws Exception {
        mockMvc.perform(get("/catalog?year=1500"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "Algún filtro no es válido y se ignoró"));
    }
}
