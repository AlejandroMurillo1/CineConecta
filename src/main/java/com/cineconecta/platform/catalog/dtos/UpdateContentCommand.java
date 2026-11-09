package com.cineconecta.platform.catalog.dtos;

import com.cineconecta.platform.catalog.ContentType;

public interface UpdateContentCommand {

    ContentType getType();

    ContentMetadata metadata();
}
