package com.cineconecta.platform.catalog.utils;

import java.util.*;
import java.util.function.Function;

public final class DtoLists {

    private DtoLists() {}

    /** Texto sin espacios sobrantes; conserva null para que @NotBlank lo reporte. */
    public static String strip(String text) {
        return text == null ? null : text.strip();
    }

    /** Texto sin espacios sobrantes; vacío o en blanco se convierte en null (filtro no informado). */
    public static String blankToNull(String text) {
        return (text == null || text.isBlank()) ? null : text.strip();
    }

    /** Lista inmutable y nunca nula. Tolera elementos nulos para que Bean Validation los reporte. */
    public static <T> List<T> immutable(List<T> list) {
        return list == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(list));
    }

    /**
     * Lista de textos limpia: sin nulos ni blancos, sin espacios sobrantes y sin repetidos
     * (conserva el orden). Los repetidos se eliminan porque el esquema de Mongo usa uniqueItems.
     */
    public static List<String> cleanTexts(List<String> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(text -> !text.isEmpty())
                .distinct()
                .toList();
    }

    /**
     * Como {@link #cleanTexts} pero además en minúsculas, para que "Drama" y "drama" sean el mismo
     * género (el filtro de búsqueda compara el valor exacto). Los repetidos se eliminan DESPUÉS de bajar a minúsculas.
     */
    public static List<String> cleanLowercase(List<String> list) {
        return cleanTexts(list).stream().map(text -> text.toLowerCase(Locale.ROOT)).distinct().toList();
    }

    /** true si ningún par de elementos comparte clave. Los nulos se ignoran (los reporta Bean Validation). */
    public static <T, K> boolean hasUniqueKeys(List<T> items, Function<T, K> key) {
        if (items == null) {
            return true;
        }
        Set<K> seen = new HashSet<>();
        for (T item : items) {
            if (item != null && !seen.add(key.apply(item))) {
                return false;
            }
        }
        return true;
    }
}
