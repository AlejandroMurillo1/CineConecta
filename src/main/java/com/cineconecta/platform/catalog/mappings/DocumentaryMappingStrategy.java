package com.cineconecta.platform.catalog.mappings;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.CreateContentCommand;
import com.cineconecta.platform.catalog.dtos.CreateDocumentaryCommand;
import com.cineconecta.platform.catalog.dtos.UpdateContentCommand;
import com.cineconecta.platform.catalog.dtos.UpdateDocumentaryCommand;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.models.ContentDetails;
import com.cineconecta.platform.catalog.models.DocumentaryDetails;
import org.springframework.stereotype.Component;

@Component
public class DocumentaryMappingStrategy implements ContentMappingStrategy {

    @Override
    public ContentType getSupportedType() {
        return ContentType.DOCUMENTARY;
    }

    @Override
    public ContentDetails mapDetails(CreateContentCommand command) {
        if (!(command instanceof CreateDocumentaryCommand documentary)) {
            throw new IllegalArgumentException("Comando no soportado por DocumentaryMappingStrategy: "
                    + command.getClass().getSimpleName());
        }
        return new DocumentaryDetails(documentary.durationSeconds(), documentary.directors(),
                documentary.topics(), documentary.interviewees(), documentary.sources());
    }

    @Override
    public ContentDetails updateDetails(ContentDetails current, UpdateContentCommand command) {
        if (!(command instanceof UpdateDocumentaryCommand documentary)) {
            throw new IllegalArgumentException("Comando no soportado por DocumentaryMappingStrategy: "
                    + command.getClass().getSimpleName());
        }
        return new DocumentaryDetails(documentary.durationSeconds(), documentary.directors(),
                documentary.topics(), documentary.interviewees(), documentary.sources());
    }

    @Override
    public UpdateContentCommand toUpdateCommand(Content content) {
        if (!(content.getContentDetails() instanceof DocumentaryDetails details)) {
            throw new IllegalStateException("El contenido " + content.getId() + " no tiene detalles de documental");
        }
        return new UpdateDocumentaryCommand(ContentMapper.toMetadata(content), details.durationSeconds(),
                details.directors(), details.topics(), details.interviewees(), details.sources());
    }
}
