package com.cineconecta.platform.catalog.models;

import java.util.List;

public record DocumentaryDetails(int durationSeconds,
                                 List<String> directors,
                                 List<String> topics,
                                 List<String> interviewees,
                                 List<String> sources) {}
