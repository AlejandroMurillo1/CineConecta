package com.cineconecta.platform.reporting.internal.service;

/**
 * El período pedido para un informe no es válido (fechas faltantes, invertidas o demasiado amplias).
 * El mensaje está pensado para mostrarse al administrador.
 */
public class InvalidReportPeriodException extends IllegalArgumentException {

    public InvalidReportPeriodException(String message) {
        super(message);
    }
}
