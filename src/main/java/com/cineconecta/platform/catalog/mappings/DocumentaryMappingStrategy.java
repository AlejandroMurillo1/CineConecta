package com.cineconecta.platform.catalog.mappings;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.CreateContentCommand;
import com.cineconecta.platform.catalog.dtos.CreateDocumentaryCommand;
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
        return new DocumentaryDetails(
                documentary.durationSeconds(),
                documentary.directors(),
                documentary.topics(),
                documentary.interviewees(),
                documentary.sources());
    }
}
