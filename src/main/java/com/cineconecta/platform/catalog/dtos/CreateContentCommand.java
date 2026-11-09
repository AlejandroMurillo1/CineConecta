package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.ContentType;

public interface CreateContentCommand {

    ContentType getType();

    /** Los records lo implementan automáticamente con su componente "metadata". */
    ContentMetadata metadata();
}
