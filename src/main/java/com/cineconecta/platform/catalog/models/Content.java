package com.cineconecta.platform.catalog.models;

import com.cineconecta.platform.catalog.ContentStatus;
import com.cineconecta.platform.catalog.ContentType;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "contents")
@TypeAlias("Content")
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

    private ContentDetails contentDetails;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public ContentType getType() { return type; }
    public void setType(ContentType type) { this.type = type; }
    public ContentStatus getStatus() { return status; }
    public void setStatus(ContentStatus status) { this.status = status; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTitleNormalized() { return titleNormalized; }
    public void setTitleNormalized(String titleNormalized) { this.titleNormalized = titleNormalized; }
    public String getSynopsis() { return synopsis; }
    public void setSynopsis(String synopsis) { this.synopsis = synopsis; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public int getAgeRating() { return ageRating; }
    public void setAgeRating(int ageRating) { this.ageRating = ageRating; }
    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }
    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }
    public List<String> getCountries() { return countries; }
    public void setCountries(List<String> countries) { this.countries = countries; }
    public List<String> getAudioLanguages() { return audioLanguages; }
    public void setAudioLanguages(List<String> audioLanguages) { this.audioLanguages = audioLanguages; }
    public List<String> getSubtitleLanguages() { return subtitleLanguages; }
    public void setSubtitleLanguages(List<String> subtitleLanguages) { this.subtitleLanguages = subtitleLanguages; }
    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public ContentDetails getContentDetails() { return contentDetails; }
    public void setContentDetails(ContentDetails contentDetails) { this.contentDetails = contentDetails; }
}
