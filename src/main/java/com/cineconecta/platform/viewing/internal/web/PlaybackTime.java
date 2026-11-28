package com.cineconecta.platform.viewing.internal.web;

/** Convierte posiciones del reproductor entre segundos y el formato hh:mm:ss que ve el usuario. */
final class PlaybackTime {

    private PlaybackTime() {
    }

    /** 3725 → "01:02:05". */
    static String format(int totalSeconds) {
        int seconds = Math.max(0, totalSeconds);
        return "%02d:%02d:%02d".formatted(seconds / 3600, seconds % 3600 / 60, seconds % 60);
    }

    /**
     * Acepta "hh:mm:ss", "mm:ss" o solo segundos. Devuelve {@code null} si llega vacío
     * (el servicio decide si la acción necesita posición).
     */
    static Integer parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] parts = text.strip().split(":");
        if (parts.length > 3) {
            throw invalid();
        }
        int total = 0;
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("\\d{1,6}")) {
                throw invalid();
            }
            int value = Integer.parseInt(parts[i]);
            // Minutos y segundos (todo lo que no es la primera parte) van de 0 a 59.
            if (i > 0 && value > 59) {
                throw invalid();
            }
            total = total * 60 + value;
        }
        return total;
    }

    /** Porcentaje visto, de 0 a 100, para la barra del reproductor. */
    static double percent(int position, int duration) {
        if (duration <= 0) {
            return 0;
        }
        return Math.min(100.0, Math.max(0.0, position * 100.0 / duration));
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Escribe la posición como hh:mm:ss, por ejemplo 00:12:30.");
    }
}
