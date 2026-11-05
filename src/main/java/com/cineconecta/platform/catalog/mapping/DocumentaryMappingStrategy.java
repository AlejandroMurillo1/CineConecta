package com.cineconecta.platform.catalog.mapping;

import com.cineconecta.platform.catalog.ContentType;
import com.cineconecta.platform.catalog.dtos.ContentMetadata;
import com.cineconecta.platform.catalog.dtos.CreateContentCommand;
import com.cineconecta.platform.catalog.dtos.CreateDocumentaryCommand;
import com.cineconecta.platform.catalog.models.Content;
import com.cineconecta.platform.catalog.models.DocumentaryDetails;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentaryMappingStrategy implements ContentMappingStrategy{
    @Override
    public ContentType getSupportedType() {
        return  ContentType.DOCUMENTARY;
    }

    @Override
    public Content mapToDomain(CreateContentCommand command) {
        if(!(command instanceof
                CreateDocumentaryCommand(ContentMetadata metadata, int durationSeconds, List<String> directors,
                                         List<String> topics, List<String> interviewees, List<String> sources))){
            throw new IllegalArgumentException("Comando no soportado por DocumentaryMappingStrategy");
        }

        Content content = baseMapToDomain(metadata,ContentType.DOCUMENTARY);
        content.setContentDetails(new DocumentaryDetails(durationSeconds, directors, topics, interviewees, sources));
        return content;
    }
}
