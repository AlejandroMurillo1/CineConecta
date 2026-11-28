package com.cineconecta.platform.reporting.internal.service;

import com.cineconecta.platform.catalog.ContentType;

import java.math.BigDecimal;

/** Una fila del informe de consumo: minutos vistos y perfiles distintos de un plan en un tipo de contenido. */
public record ConsumptionReportRow(
        int planId,
        String planName,
        ContentType type,
        BigDecimal minutes,
        long distinctProfiles
) {

    /** Nombre del tipo de contenido para mostrar en pantalla y en el CSV. */
    public String typeLabel() {
        return switch (type) {
            case MOVIE -> "Película";
            case SERIES -> "Serie";
            case DOCUMENTARY -> "Documental";
        };
    }
}
