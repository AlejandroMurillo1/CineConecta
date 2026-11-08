package com.cineconecta.platform.catalog.dtos;

import java.util.*;
import java.util.function.Function;

final class DtoLists {

    private DtoLists() {}

    /** Texto sin espacios sobrantes; conserva null para que @NotBlank lo reporte. */
    static String strip(String text) {
        return text == null ? null : text.strip();
    }

    /** Lista inmutable y nunca nula. Tolera elementos nulos para que Bean Validation los reporte. */
    static <T> List<T> immutable(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }

    /**
     * Lista de textos limpia: sin nulos ni blancos, sin espacios sobrantes y sin repetidos
     * (conserva el orden). Los repetidos se eliminan porque el esquema de Mongo usa uniqueItems.
     */
    static List<String> cleanTexts(List<String> list) {
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

    /** true si ningún par de elementos comparte clave. Los nulos se ignoran (los reporta Bean Validation). */
    static <T, K> boolean hasUniqueKeys(List<T> items, Function<T, K> key) {
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
