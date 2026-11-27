package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.utils.DtoLists;
import jakarta.validation.constraints.*;

import java.util.List;

public record ContentMetadata(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 2000) String synopsis,
        @Min(1888) @Max(2100) int year,
        @Min(0) @Max(18) int ageRating,
        @NotBlank @Pattern(regexp = ContentMetadata.URL) String posterUrl,
        @NotEmpty List<String> genres,
        @NotEmpty List<@Pattern(regexp = ContentMetadata.COUNTRY) String> countries,
        @NotEmpty List<@Pattern(regexp = ContentMetadata.LANGUAGE) String> audioLanguages,
        List<@Pattern(regexp = ContentMetadata.LANGUAGE) String> subtitleLanguages
) {
    static final String URL = "^https?://\\S+$";
    static final String COUNTRY = "^[A-Z]{2}$";
    static final String LANGUAGE = "^[a-z]{2,3}(-[A-Za-z]{2,4})?$";

    public ContentMetadata {
        title = DtoLists.strip(title);
        synopsis = DtoLists.strip(synopsis);
        posterUrl = DtoLists.strip(posterUrl);
        genres = DtoLists.cleanLowercase(genres);
        countries = DtoLists.cleanTexts(countries);
        audioLanguages = DtoLists.cleanTexts(audioLanguages);
        subtitleLanguages = DtoLists.cleanTexts(subtitleLanguages);
    }
}
