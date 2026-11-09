package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.utils.DtoLists;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record ContentSearchFilter(
        @Size(max = 100) String title,
        ContentType type,
        @Size(max = 50) String genre,
        @Pattern(regexp = ContentMetadata.LANGUAGE) String audioLanguage,
        @Min(1888) @Max(2100) Integer year,
        int page,
        int size
) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    public ContentSearchFilter {
        title = DtoLists.blankToNull(title);
        genre = DtoLists.blankToNull(genre);
        if (genre != null) {
            genre = genre.toLowerCase(Locale.ROOT);     // los géneros se guardan en minúsculas
        }
        audioLanguage = DtoLists.blankToNull(audioLanguage);
        page = Math.max(page, 0);
        size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    /** Sin filtros, primera página. */
    public static ContentSearchFilter none() {
        return new ContentSearchFilter(null, null, null, null, null, 0, DEFAULT_SIZE);
    }
}
