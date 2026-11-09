package com.cineconecta.platform.catalog.controllers;

import com.cineconecta.platform.catalog.dtos.CreateContentCommand;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.services.CatalogService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogService catalogService;

    @Autowired
    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping()
    public Content createContent(@RequestBody @Valid CreateContentCommand command) {
        return catalogService.createContent(command);
    }
}
