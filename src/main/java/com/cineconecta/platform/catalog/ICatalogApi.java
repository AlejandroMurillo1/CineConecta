package com.cineconecta.platform.catalog;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ICatalogApi {
    /**
     * Resuelve lo reproducible. Devuelve vacío si el contenido no existe, no está PUBLICADO, o el
     * episodio no corresponde: en películas y documentales episodeId debe ser null; en series es
     * obligatorio y debe existir en esa serie.
     */
    Optional<PlayableRef> findPlayable(String contentId, String episodeId);

    /**
     * Versión por lotes de {@link #findPlayable}, para evitar una consulta por elemento (p. ej.
     * "Continuar viendo"). Las claves que no son reproducibles simplemente no aparecen en el mapa.
     */
    Map<PlaybackKey, PlayableRef> findPlayables(Collection<PlaybackKey> keys);

    /**
     * Fichas de los contenidos pedidos, en CUALQUIER estado (incluye los retirados). Los ids que no
     * existen no aparecen en el mapa. Colección nula o vacía devuelve un mapa vacío.
     */
    Map<String, ContentSummary> findSummaries(Collection<String> contentIds);
}
