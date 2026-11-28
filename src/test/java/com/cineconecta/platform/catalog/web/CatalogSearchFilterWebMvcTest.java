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
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void anInvalidFilterIsStillReported() throws Exception {
        mockMvc.perform(get("/catalog?year=1500"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "Algún filtro no es válido y se ignoró"));
    }
}
