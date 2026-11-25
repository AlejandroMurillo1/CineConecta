package com.cineconecta.platform.catalog.models;

import org.springframework.data.annotation.TypeAlias;

@TypeAlias("CastMember")
public record CastMember(String name, String character) {
}
