package com.cineconecta.platform.catalog.model;

import java.util.List;

public record Season(int number, String description, List<Episode> episodes) {
}
