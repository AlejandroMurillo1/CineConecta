package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.utils.DtoLists;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record UpdateDocumentaryCommand(
        @NotNull @Valid ContentMetadata metadata,
        @Positive int durationSeconds,
        @NotEmpty List<String> directors,
        @NotEmpty List<String> topics,
        List<String> interviewees,
        List<String> sources
) implements UpdateContentCommand {

    public UpdateDocumentaryCommand {
        directors = DtoLists.cleanTexts(directors);
        topics = DtoLists.cleanTexts(topics);
        interviewees = DtoLists.cleanTexts(interviewees);
        sources = DtoLists.cleanTexts(sources);
    }

    @Override
    public ContentType getType() {
        return ContentType.DOCUMENTARY;
    }
}
