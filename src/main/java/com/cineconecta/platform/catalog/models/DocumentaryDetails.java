package com.cineconecta.platform.catalog.models;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;

@JsonTypeName("DOCUMENTARY")
public record DocumentaryDetails (
        int durationSeconds,
        List<String> directors,
        List<String> topics,
        List<String> interviewees,
        List<String> sources
) implements ContentDetails {}
