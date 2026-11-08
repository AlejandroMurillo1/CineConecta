package com.cineconecta.platform.catalog.models;

import org.springframework.data.annotation.TypeAlias;

import java.util.List;

@TypeAlias("DOCUMENTARY")
public record DocumentaryDetails (
        int durationSeconds,
        List<String> directors,
        List<String> topics,
        List<String> interviewees,
        List<String> sources
) implements ContentDetails {}
