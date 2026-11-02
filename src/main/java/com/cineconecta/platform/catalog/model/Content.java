package com.cineconecta.platform.catalog.model;

import com.cineconecta.platform.catalog.ContentStatus;
import com.cineconecta.platform.catalog.ContentType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "contents")
@TypeAlias("Content")
@Getter
@Setter
public class Content {
    @Id
    private String id;
    @Version
    private Long version;

    private ContentType type;
    private ContentStatus status = ContentStatus.DRAFT;

    private String title;
    private String titleNormalized;
    private String synopsis;
    private int year;
    private int ageRating;
    private String posterUrl;
    private List<String> genres = new ArrayList<>();
    private List<String> countries = new ArrayList<>();
    private List<String> audioLanguages = new ArrayList<>();
    private List<String> subtitleLanguages = new ArrayList<>();

    private int schemaVersion = 1;
    private Instant createdAt;
    private Instant updatedAt;

    private MovieDetails movie;
    private SeriesDetails series;
    private DocumentaryDetails documentary;
}
