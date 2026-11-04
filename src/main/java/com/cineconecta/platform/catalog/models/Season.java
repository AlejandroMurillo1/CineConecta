package com.cineconecta.platform.catalog.models;

import java.util.List;

public record Season(int number, String description, List<Episode> episodes) {
}
